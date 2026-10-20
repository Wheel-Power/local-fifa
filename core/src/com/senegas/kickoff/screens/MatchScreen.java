package com.senegas.kickoff.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.senegas.kickoff.KickOff;
import com.senegas.kickoff.entities.Ball;
import com.senegas.kickoff.entities.Player;
import com.senegas.kickoff.entities.Team;
import com.senegas.kickoff.net.ClientSession;
import com.senegas.kickoff.net.HostSession;
import com.senegas.kickoff.net.Link;
import com.senegas.kickoff.net.Snapshot;
import com.senegas.kickoff.pitches.Pitch;
import com.senegas.kickoff.pitches.PitchFactory;
import com.senegas.kickoff.pitches.Scanner;
import com.senegas.kickoff.scenes.Hud;
import com.senegas.kickoff.states.MatchState;
import com.senegas.kickoff.utils.CameraHelper;
import com.senegas.kickoff.utils.Direction;
import com.senegas.kickoff.utils.PitchUtils;
import com.senegas.kickoff.utils.RuleBook;
import com.senegas.kickoff.utils.Stopwatch;
import com.badlogic.gdx.utils.ScreenUtils;

import static com.senegas.kickoff.pitches.FootballDimensionConstants.CM_PER_PIXEL;

/**
 * Match screen. Three modes:
 * - SOLO:   local player vs tactic AI
 * - HOST:   home team = local touch, away team = remote peer input,
 *           this device runs the authoritative simulation and broadcasts snapshots
 * - CLIENT: away team = local touch, world state corrected from host snapshots
 *
 * @author Sébastien Sénégas (original), Local FIFA modifications
 */
public class MatchScreen extends AbstractScreen {
    private static final String TAG = MatchScreen.class.getSimpleName();

    public enum Mode {SOLO, HOST, CLIENT}

    public static final float MATCH_DURATION_SECONDS = 180f;
    private static final float SNAPSHOT_INTERVAL = 1f / 30f;
    private static final float INPUT_INTERVAL = 1f / 30f;

    private OrthogonalTiledMapRenderer renderer;
    private OrthographicCamera camera;
    private CameraHelper cameraHelper;
    private BitmapFont font;

    private Pitch pitch;
    private Scanner scanner;
    private Ball ball;
    private Team home;
    private Team away;
    private int scoreHome;
    private int scoreAway;
    private Player lastTouch;
    private Hud hud;
    private Stopwatch stopwatch;

    public StateMachine<MatchScreen, MatchState> stateMachine;
    public Sound crowd;
    public Sound whistle;
    public Sound dribble;

    private final Mode mode;
    private HostSession hostSession;
    private ClientSession clientSession;

    private float timeLeft = MATCH_DURATION_SECONDS;
    private byte kickoffAttack;                 // 0 = home, 1 = away
    private final Vector2 restartPoint = new Vector2();
    private int lastScorer = -1;

    private float snapAcc;
    private float inputAcc;
    private int inputSeq;
    private int tick;
    private final Vector3 localDestination = new Vector3();
    private boolean hasLocalDestination;

    private boolean linkLost;
    private float linkLostTimer;

    private float telemetryAcc;
    private float matchElapsed;
    private float inPlayElapsed;
    private boolean goalFired;
    private boolean playShotTaken;

    /** in-game screenshot hook (desktop automation): localfifa.shots=/tmp */
    private final String shotDir = System.getProperty("localfifa.shots");
    private float shotTimer = -1f;
    private String shotTag;

    private static final boolean DEBUG = true;

    public MatchScreen(KickOff app) {
        this(app, Mode.SOLO, null);
    }

    public MatchScreen(KickOff app, Mode mode, Link link) {
        super(app);
        this.mode = mode;
        if (mode == Mode.HOST && link != null) {
            this.hostSession = new HostSession(link);
        }
        if (mode == Mode.CLIENT && link != null) {
            this.clientSession = new ClientSession(link);
        }

        this.scoreHome = 0;
        this.scoreAway = 0;

        this.camera = new OrthographicCamera();
        this.camera.setToOrtho(false, KickOff.V_WIDTH, KickOff.V_HEIGHT);

        this.cameraHelper = new CameraHelper();
        this.cameraHelper.setZoom(.35f);

        this.font = new BitmapFont();
        this.stateMachine = new DefaultStateMachine<>(this);

        Gdx.app.log(TAG, "constructor mode=" + mode);
    }

    @Override
    public void show() {
        // make sure every queued asset is actually available
        while (!this.app.assets.isFinished()) {
            this.app.assets.update();
        }

        this.pitch = PitchFactory.INSTANCE.createPitch(PitchFactory.PitchType.CLASSIC);
        this.renderer = new OrthogonalTiledMapRenderer(this.pitch.getTiledMap(), this.app.batch);

        Vector2 centerSpot = this.pitch.getCenterSpot();
        this.ball = new Ball(this.app.assets.get("entities/ball.png"),
                new Vector3(centerSpot.x, centerSpot.y, 320));
        this.home = new Team(this, "HOME", Direction.NORTH);
        this.away = new Team(this, "AWAY", Direction.SOUTH);

        switch (this.mode) {
            case HOST:
                this.home.setHuman(true);
                this.away.setHuman(true);
                break;
            case CLIENT:
                this.home.setHuman(false);
                this.away.setHuman(true);
                break;
            default:
                this.home.setHuman(true);
                this.away.setHuman(false);
        }

        this.hud = new Hud(this);
        this.stopwatch = new Stopwatch(3);

        this.scanner = new Scanner(this);

        this.crowd = this.app.assets.get("sounds/crowd.ogg");
        this.whistle = this.app.assets.get("sounds/whistle.ogg");
        this.dribble = this.app.assets.get("sounds/dribble.ogg");

        this.timeLeft = MATCH_DURATION_SECONDS;
        this.kickoffAttack = 0;
        this.restartPoint.set(centerSpot);
        this.lastScorer = -1;
        this.linkLost = false;
        this.linkLostTimer = 0;
        this.hasLocalDestination = false;

        this.stateMachine.changeState(MatchState.INTRODUCTION);
    }

    @Override
    public void update(float deltaTime) {
        handleKeys();

        if (this.mode == Mode.CLIENT) {
            // apply authoritative corrections first, then follow the host state
            applySnapshotCorrections();
            syncStateFromSnapshot();
            this.away.updateControlled(this.ball.getPosition());
            if (this.stateMachine.getCurrentState() == MatchState.INPLAY) {
                cameraFollowBall();
                updateTactics();
            }
        } else {
            this.home.updateControlled(this.ball.getPosition());
            this.away.updateControlled(this.ball.getPosition());

            // remote peer drives the away team's controlled player
            if (this.mode == Mode.HOST && this.hostSession != null && this.hostSession.hasRemoteInput()) {
                Player awayControlled = this.away.getControlled();
                if (awayControlled != null) {
                    awayControlled.setDestination(new Vector3(this.hostSession.remoteX(), this.hostSession.remoteY(), 0));
                }
            }

            this.stateMachine.update();

            if (this.stateMachine.getCurrentState() == MatchState.INPLAY) {
                this.timeLeft = Math.max(0f, this.timeLeft - deltaTime);
                if (this.timeLeft <= 0f) {
                    this.stateMachine.changeState(MatchState.FULLTIME);
                }
            }
        }

        // local touch wins over tactic destinations, applied after them
        handleTouch();

        this.home.update(deltaTime);
        this.away.update(deltaTime);
        this.ball.update(deltaTime);

        checkCollisions();
        if (this.mode != Mode.CLIENT) {
            checkRules();
        }

        this.cameraHelper.update(deltaTime);
        this.cameraHelper.applyTo(this.camera);

        this.hud.update(deltaTime);
        this.stopwatch.update(deltaTime);

        netPump(deltaTime);
        checkLinkLost(deltaTime);

        runTestBot(deltaTime);
    }

    /**
     * Desktop automation only (system property localfifa.bot=home|away):
     * drives the given team's controlled player at the ball so two desktop
     * instances actually play against each other over the wire.
     */
    private void runTestBot(float deltaTime) {
        if (System.getProperty("localfifa.bot") == null) return;
        Team botTeam = (this.mode == Mode.CLIENT) ? this.away : this.home;
        if (!botTeam.isHuman() || botTeam.getControlled() == null) return;

        // line up behind the ball relative to the goal being attacked, so
        // contact kicks actually shoot at the goal. With localfifa.botPark the
        // client bot just parks near its own goal so the host bot can score.
        Vector3 ballPos = this.ball.getPosition();
        if (this.mode == Mode.CLIENT && System.getProperty("localfifa.botPark") != null) {
            botTeam.getControlled().setDestination(new Vector3(RuleBook.CENTER_X, RuleBook.FIELD_TOP_Y + 60f, 0));
        } else {
            float goalY = (this.mode == Mode.CLIENT)
                    ? RuleBook.FIELD_TOP_Y     // away (client's team) attacks the top goal
                    : RuleBook.FIELD_BOTTOM_Y; // home attacks the bottom goal
            Vector3 goal = new Vector3(RuleBook.CENTER_X, goalY, 0);
            Vector3 toGoal = goal.cpy().sub(ballPos.x, ballPos.y, 0);
            if (toGoal.len2() < 1f) {
                toGoal.set(0, 1, 0);
            }
            Vector3 approach = new Vector3(ballPos).sub(toGoal.nor().scl(8f));
            botTeam.getControlled().setDestination(approach);
        }

        this.telemetryAcc += deltaTime;
        if (this.telemetryAcc > 5f) {
            this.telemetryAcc = 0;
            String snap = this.clientSession != null ? " snap=" + this.clientSession.sinceLastSnapshotMs() + "ms" : "";
            Gdx.app.log(TAG, "telemetry mode=" + this.mode + " state=" + this.stateMachine.getCurrentState()
                    + " score=" + this.scoreHome + "-" + this.scoreAway + snap);
        }

        float exitAfter = Float.parseFloat(System.getProperty("localfifa.exitAfter", "0"));
        if (exitAfter > 0) {
            this.matchElapsed += deltaTime;
            if (this.matchElapsed > exitAfter) {
                Gdx.app.log(TAG, "auto exit");
                Gdx.app.exit();
            }
        }

        // screenshot hook: force a goal after N seconds of actual play (host
        // side triggers it once; the client follows through the snapshot)
        float goalAt = Float.parseFloat(System.getProperty("localfifa.goalAt", "0"));
        if (goalAt > 0 && !this.goalFired && this.mode != Mode.CLIENT
                && this.stateMachine.getCurrentState() == MatchState.INPLAY) {
            this.inPlayElapsed += deltaTime;
            if (this.inPlayElapsed > goalAt) {
                this.goalFired = true;
                this.scoreHome++;
                this.lastScorer = 0;
                this.kickoffAttack = 1;
                this.stateMachine.changeState(MatchState.GOAL);
            }
        }

        if (this.shotTimer >= 0f) {
            this.shotTimer -= deltaTime;
            if (this.shotTimer <= 0f) {
                this.shotTimer = -1f;
                saveScreenshot(this.shotTag);
            }
        }
    }

    public void requestShot(String tag, float delaySeconds) {
        if (this.shotDir == null) return;
        this.shotTag = tag;
        this.shotTimer = delaySeconds;
    }

    private void saveScreenshot(String tag) {
        try {
            int w = Gdx.graphics.getWidth();
            int h = Gdx.graphics.getHeight();
            byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
            Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(pixels.length);
            buffer.put(pixels);
            buffer.position(0);
            pixmap.setPixels(buffer);
            PixmapIO.writePNG(new FileHandle(this.shotDir + "/shot-" + tag + "-" + this.mode.name().toLowerCase() + ".png"), pixmap);
            pixmap.dispose();
            Gdx.app.log(TAG, "screenshot saved: " + tag);
        } catch (Exception e) {
            Gdx.app.log(TAG, "screenshot failed: " + e);
        }
    }

    @Override
    public void render(float deltaTime) {
        super.render(deltaTime);

        this.renderer.setView(this.camera);
        this.renderer.render();

        // controlled-player markers under the entities
        this.app.shapeBatch.setProjectionMatrix(this.camera.combined);
        drawControlledMarker(this.home);
        drawControlledMarker(this.away);

        // renders overlays for tactic and ball position
        this.app.shapeBatch.setProjectionMatrix(this.camera.combined);
        this.home.showDebug(this.app.shapeBatch, this.ball.getPosition());

        // renders entities
        this.app.batch.begin();

        this.home.draw(this.app.batch);
        this.away.draw(this.app.batch);
        this.ball.render(this.app.batch);

        // renders stopwatch
        this.app.batch.setProjectionMatrix(this.hud.stage.getCamera().combined);
        this.stopwatch.draw(this.app.batch);

        if (DEBUG) {
            displayDebugInfo();
        }

        this.app.batch.end();

        this.hud.stage.draw();

        this.app.shapeBatch.setProjectionMatrix(this.app.batch.getProjectionMatrix());
        this.scanner.draw(this.app.shapeBatch);
    }

    private void drawControlledMarker(Team team) {
        if (!team.isHuman() || team.getControlled() == null) return;
        Vector3 p = team.getControlled().getPosition();
        this.app.shapeBatch.begin(ShapeRenderer.ShapeType.Line);
        this.app.shapeBatch.setColor(Color.WHITE);
        this.app.shapeBatch.circle(p.x, p.y, 11);
        this.app.shapeBatch.end();
    }

    private void displayDebugInfo() {
        this.app.batch.setProjectionMatrix(this.hud.stage.getCamera().combined);

        Player player = this.home.getPlayers().get(0);
        Vector2 ballLocation = new Vector2(this.ball.getPosition().x, this.ball.getPosition().y);

        this.font.draw(this.app.batch, "FPS: " + Gdx.graphics.getFramesPerSecond(), 10, 20);
        this.font.draw(this.app.batch, "Player: " + (int) player.getPosition().x + ", " +
                                          (int) player.getPosition().y, 10, 40);

        this.font.draw(this.app.batch, "Ball: " + (int) ballLocation.x + ", " +
                                        (int) ballLocation.y + ", " +
                                        (int) this.ball.getPosition().z, 10, 60);

        this.font.draw(this.app.batch, this.home.getTactic().getName(), 10, 100);
        this.font.draw(this.app.batch, this.stateMachine.getCurrentState().toString() + "  mode=" + this.mode
                + (this.clientSession != null ? "  snap " + this.clientSession.sinceLastSnapshotMs() + "ms ago" : ""), 10, 120);
    }

    public void setLastPlayerTouch(Player player) {
        this.lastTouch = player;
    }

    public Player getLastPlayerTouch() {
        return this.lastTouch;
    }

    public Team getLastTeamTouch() {
        if (this.lastTouch == null) {
            return null;
        }
        return this.lastTouch.getTeam();
    }

    /**
     * Players bump the ball Kick Off style: contact pushes it in the running
     * direction, which doubles as dribbling and shooting.
     */
    private void checkCollisions() {
        if (this.stateMachine.getCurrentState() == MatchState.INPLAY) {
            bumpTeam(this.home);
            bumpTeam(this.away);
        }
    }

    private void bumpTeam(Team team) {
        for (Player player : team.getPlayers()) {
            if (player.getBounds().contains(this.ball.getPosition().x, this.ball.getPosition().y)) {
                setLastPlayerTouch(player);
                if (this.ball.getPosition().z < player.height() / CM_PER_PIXEL) {
                    Vector3 dv = player.getDirection().getDirectionVector();
                    Vector3 velocity = dv.scl(player.speed() * 1.125f + 30.0f);
                    velocity.z = 80;
                    this.ball.kick(velocity, player);
                    this.dribble.play(0.2f);
                }
            }
        }
    }

    /**
     * Host/solo only: goals, out of play. The client follows the snapshot.
     */
    private void checkRules() {
        if (this.stateMachine.getCurrentState() != MatchState.INPLAY) return;

        Vector3 last = this.ball.getLastPosition();
        Vector3 cur = this.ball.getPosition();

        int goal = RuleBook.goalScored(last, cur);
        if (goal != 0) {
            if (goal == 1) {
                this.scoreAway++;
                this.lastScorer = 1;
                this.kickoffAttack = 0; // conceding team kicks off
            } else {
                this.scoreHome++;
                this.lastScorer = 0;
                this.kickoffAttack = 1;
            }
            this.stateMachine.changeState(MatchState.GOAL);
            return;
        }

        if (RuleBook.outOfPlay(last, cur, this.restartPoint)) {
            this.stateMachine.changeState(MatchState.THROW_IN);
        }
    }

    /** Called by the state machine on INPLAY entry (screenshot hook fires once). */
    public void onInplayEntered() {
        if (!this.playShotTaken) {
            this.playShotTaken = true;
            requestShot("inplay", 4f);
        }
    }

    public void cameraFollowBall() {
        this.cameraHelper.setPosition(com.badlogic.gdx.math.MathUtils.clamp(this.ball.getPosition().x,
                this.camera.viewportWidth / 2 * this.camera.zoom, Pitch.WIDTH - this.camera.viewportWidth / 2 * this.camera.zoom),
                com.badlogic.gdx.math.MathUtils.clamp(this.ball.getPosition().y,
                        this.camera.viewportHeight / 2 * this.camera.zoom, Pitch.HEIGHT - this.camera.viewportHeight / 2 * this.camera.zoom));
    }

    public void updateTactics() {
        this.home.getTactic().update(this.ball.getPosition());
        this.away.getTactic().update(this.ball.getPosition());
    }

    private void syncStateFromSnapshot() {
        if (this.clientSession == null) return;
        Snapshot s = this.clientSession.latest();
        if (s == null) return;
        MatchState remote = MatchState.byId(s.stateId);
        if (this.stateMachine.getCurrentState() != remote) {
            this.stateMachine.changeState(remote);
        }
    }

    private void applySnapshotCorrections() {
        if (this.clientSession == null) return;
        Snapshot s = this.clientSession.latest();
        if (s == null) return;

        // detect scorer from score changes so the GOAL banner shows the right colour
        if (s.scoreHome > this.scoreHome) this.lastScorer = 0;
        if (s.scoreAway > this.scoreAway) this.lastScorer = 1;
        this.scoreHome = s.scoreHome;
        this.scoreAway = s.scoreAway;
        this.timeLeft = s.timeLeftSeconds;
        this.kickoffAttack = s.kickoffAttack;
        this.restartPoint.set(s.restartX, s.restartY);

        // smooth the world towards the authoritative state
        Vector3 tmp = new Vector3();
        lerpTeam(this.home.getPlayers(), s.homeXY, tmp);
        lerpTeam(this.away.getPlayers(), s.awayXY, tmp);
        tmp.set(s.ballX, s.ballY, s.ballZ);
        this.ball.getPosition().lerp(tmp, 0.5f);
        this.ball.getVelocity().set(s.ballVx, s.ballVy, s.ballVz);
    }

    private void lerpTeam(Array<Player> players, float[] xy, Vector3 tmp) {
        for (int i = 0; i < players.size && i * 2 + 1 < xy.length; i++) {
            Player p = players.get(i);
            tmp.set(xy[i * 2], xy[i * 2 + 1], 0);
            p.getPosition().lerp(tmp, 0.35f);
            p.syncBounds();
        }
    }

    private void netPump(float deltaTime) {
        if (this.mode == Mode.HOST && this.hostSession != null) {
            this.snapAcc += deltaTime;
            while (this.snapAcc >= SNAPSHOT_INTERVAL) {
                this.snapAcc -= SNAPSHOT_INTERVAL;
                this.hostSession.sendSnapshot(buildSnapshot());
            }
        }
        if (this.mode == Mode.CLIENT && this.clientSession != null && this.hasLocalDestination) {
            this.inputAcc += deltaTime;
            while (this.inputAcc >= INPUT_INTERVAL) {
                this.inputAcc -= INPUT_INTERVAL;
                this.clientSession.sendInput(this.inputSeq++, this.localDestination.x, this.localDestination.y);
            }
        }
    }

    private Snapshot buildSnapshot() {
        Snapshot s = new Snapshot();
        s.tick = this.tick++;
        s.stateId = (byte) this.stateMachine.getCurrentState().ordinal();
        s.scoreHome = (byte) this.scoreHome;
        s.scoreAway = (byte) this.scoreAway;
        s.timeLeftSeconds = (short) this.timeLeft;
        s.kickoffAttack = this.kickoffAttack;
        s.ballX = this.ball.getPosition().x;
        s.ballY = this.ball.getPosition().y;
        s.ballZ = this.ball.getPosition().z;
        s.ballVx = this.ball.getVelocity().x;
        s.ballVy = this.ball.getVelocity().y;
        s.ballVz = this.ball.getVelocity().z;
        fillTeam(this.home.getPlayers(), s.homeXY);
        fillTeam(this.away.getPlayers(), s.awayXY);
        s.restartX = this.restartPoint.x;
        s.restartY = this.restartPoint.y;
        return s;
    }

    private void fillTeam(Array<Player> players, float[] xy) {
        for (int i = 0; i < players.size && i * 2 + 1 < xy.length; i++) {
            Vector3 p = players.get(i).getPosition();
            xy[i * 2] = p.x;
            xy[i * 2 + 1] = p.y;
        }
    }

    private void checkLinkLost(float deltaTime) {
        boolean gone = (this.hostSession != null && this.hostSession.peerGone())
                || (this.clientSession != null && (this.clientSession.peerGone() || this.clientSession.stalled()));
        if (gone && !this.linkLost) {
            this.linkLost = true;
            this.linkLostTimer = 0;
            this.hud.showBanner("OPPONENT DISCONNECTED", Color.RED);
        }
        if (this.linkLost) {
            this.linkLostTimer += deltaTime;
            if (this.linkLostTimer > 2.5f) {
                endMatch();
            }
        }
    }

    public void endMatch() {
        if (this.hostSession != null) {
            this.hostSession.sendBye();
            this.hostSession.close();
            this.hostSession = null;
        }
        if (this.clientSession != null) {
            this.clientSession.sendBye();
            this.clientSession.close();
            this.clientSession = null;
        }
        this.app.gsm.setScreen(com.senegas.kickoff.managers.GameScreenManager.STATE.MAIN_MENU);
    }

    @Override
    public void resize(int width, int height) {
        this.camera.viewportHeight = height;
        this.camera.viewportWidth = width;
        if (this.hud != null) {
            this.hud.stage.getViewport().update(width, height, true);
        }
    }

    public int getScoreHome() {
        return this.scoreHome;
    }

    public int getScoreAway() {
        return this.scoreAway;
    }

    public float getTimeLeft() {
        return this.timeLeft;
    }

    public byte getKickoffAttack() {
        return this.kickoffAttack;
    }

    public int getLastScorer() {
        return this.lastScorer;
    }

    public Vector2 getRestartPoint() {
        return this.restartPoint;
    }

    public Team getHomeTeam() {
        return this.home;
    }

    public Team getAwayTeam() {
        return this.away;
    }

    public Ball getBall() {
        return this.ball;
    }

    public Pitch getPitch() {
        return this.pitch;
    }

    public OrthographicCamera getCamera() {
        return this.camera;
    }

    public CameraHelper getCameraHelper() {
        return this.cameraHelper;
    }

    public Hud getHud() {
        return this.hud;
    }

    @Override
    public void hide() {
        Gdx.app.log(TAG, "hide");
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        super.dispose();
        Gdx.app.log(TAG, "dispose");

        if (this.hostSession != null) this.hostSession.close();
        if (this.clientSession != null) this.clientSession.close();
        if (this.renderer != null) this.renderer.dispose();
        if (this.hud != null) this.hud.dispose();
    }

    private void handleKeys() {
        if (Gdx.input.isKeyPressed(Input.Keys.O)) {
            this.cameraHelper.setZoom(this.cameraHelper.getZoom() + 0.02f);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.I)) {
            this.cameraHelper.setZoom(this.cameraHelper.getZoom() - 0.02f);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.A)) {
            this.scanner.toggleZoom();
        }

        // desktop debug kicks (host authority only)
        if (this.mode == Mode.CLIENT) return;
        if (Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            kickBallDebug(Direction.WEST);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            kickBallDebug(Direction.EAST);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            kickBallDebug(Direction.NORTH);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.X)) {
            kickBallDebug(Direction.SOUTH);
        }
    }

    private void kickBallDebug(Direction direction) {
        Vector3 dv = direction.getDirectionVector();
        Vector3 velocity = dv.scl(400 * 1.125f + 30.0f);
        velocity.z = 200;
        this.ball.kick(velocity, null);
    }

    /**
     * Tap/drag anywhere: the human team's controlled player runs there.
     * On the client the destination is also shipped to the host.
     */
    private void handleTouch() {
        if (!Gdx.input.isTouched()) return;

        Vector3 touchPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        this.camera.unproject(touchPos);

        Team mine = (this.mode == Mode.CLIENT) ? this.away : this.home;
        Player controlled = mine.getControlled();
        if (controlled != null) {
            controlled.setDestination(touchPos);
            if (this.mode == Mode.CLIENT) {
                this.localDestination.set(touchPos);
                this.hasLocalDestination = true;
            }
        }
    }
}
