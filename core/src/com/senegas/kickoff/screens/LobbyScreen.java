package com.senegas.kickoff.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.senegas.kickoff.KickOff;
import com.senegas.kickoff.managers.GameScreenManager;
import com.senegas.kickoff.net.Link;
import com.senegas.kickoff.net.Platform;
import com.senegas.kickoff.screens.MatchScreen.Mode;

import java.util.List;

/**
 * Bluetooth lobby: host a game or pick an already paired device to join.
 */
public class LobbyScreen extends AbstractScreen {
    private static final String TAG = LobbyScreen.class.getSimpleName();

    private Stage stage;
    private TextureAtlas atlas;
    private Skin skin;
    private BitmapFont white;

    private Label statusLabel;
    private Table peerTable;
    private TextButton hostButton;
    private TextButton refreshButton;

    public LobbyScreen(KickOff app) {
        super(app);
    }

    @Override
    public void update(float deltaTime) {
    }

    @Override
    public void show() {
        this.stage = new Stage();
        Gdx.input.setInputProcessor(this.stage);

        this.atlas = new TextureAtlas("ui/button.pack");
        this.skin = new Skin(this.atlas);
        this.white = new BitmapFont(Gdx.files.internal("font/white.fnt"), false);

        TextButtonStyle style = new TextButtonStyle();
        style.up = this.skin.getDrawable("button.up");
        style.down = this.skin.getDrawable("button.down");
        style.pressedOffsetX = 1;
        style.pressedOffsetY = -1;
        style.font = this.white;

        Label.LabelStyle labelStyle = new Label.LabelStyle(this.white, null);
        Label title = new Label("BLUETOOTH MATCH", labelStyle);
        title.setFontScale(1.6f);

        this.statusLabel = new Label("", labelStyle);

        this.hostButton = new TextButton("HOST GAME", style);
        this.hostButton.pad(12);
        this.hostButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                startHosting();
            }
        });

        this.refreshButton = new TextButton("REFRESH", style);
        this.refreshButton.pad(12);
        this.refreshButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                refreshPeers();
            }
        });

        TextButton backButton = new TextButton("BACK", style);
        backButton.pad(12);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                cancelPending();
                LobbyScreen.this.app.gsm.setScreen(GameScreenManager.STATE.MAIN_MENU);
            }
        });

        this.peerTable = new Table();

        Table root = new Table();
        root.setFillParent(true);
        root.top().padTop(30);
        root.add(title).padBottom(20);
        root.row();
        root.add(this.hostButton).padBottom(10);
        root.row();
        root.add(this.statusLabel).padBottom(10);
        root.row();
        root.add(this.refreshButton).padBottom(6);
        root.row();
        root.add(this.peerTable).expand().fill().padBottom(6);
        root.row();
        root.add(backButton).padBottom(20);
        this.stage.addActor(root);

        refreshPeers();
    }

    private Platform platform() {
        return this.app.getPlatform();
    }

    /** Test hook: programmatically host a game (desktop automation). */
    public void autoHost() {
        startHosting();
    }

    /** Test hook: programmatically join the first listed peer. */
    public void autoJoin() {
        if (platform() == null) return;
        List<Platform.Peer> peers = platform().peers();
        if (!peers.isEmpty()) {
            connectPeer(peers.get(0));
        }
    }

    private void startHosting() {
        if (platform() == null) return;
        setBusy("Waiting for opponent...");
        this.hostButton.setDisabled(true);
        platform().host(new Platform.Callback<Link>() {
            @Override
            public void onReady(final Link link) {
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        LobbyScreen.this.app.gsm.startMatch(Mode.HOST, link);
                    }
                });
            }

            @Override
            public void onError(final Exception e) {
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        reset(String.valueOf(e.getMessage()));
                    }
                });
            }
        });
    }

    private void connectPeer(final Platform.Peer peer) {
        if (platform() == null) return;
        setBusy("Connecting to " + peer.name + "...");
        platform().connect(peer, new Platform.Callback<Link>() {
            @Override
            public void onReady(final Link link) {
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        LobbyScreen.this.app.gsm.startMatch(Mode.CLIENT, link);
                    }
                });
            }

            @Override
            public void onError(final Exception e) {
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        reset(String.valueOf(e.getMessage()));
                    }
                });
            }
        });
    }

    private void refreshPeers() {
        this.peerTable.clear();
        if (platform() == null) {
            this.statusLabel.setText("Multiplayer not available on this platform");
            return;
        }
        try {
            List<Platform.Peer> peers = platform().peers();
            if (peers.isEmpty()) {
                this.statusLabel.setText("No paired devices found.\nPair the phones in Android Bluetooth settings first.");
            } else {
                this.statusLabel.setText("Pick the opponent's phone:");
                TextButtonStyle style = this.hostButton.getStyle();
                for (final Platform.Peer peer : peers) {
                    TextButton peerButton = new TextButton(peer.name, style);
                    peerButton.pad(10);
                    peerButton.addListener(new ClickListener() {
                        @Override
                        public void clicked(InputEvent event, float x, float y) {
                            connectPeer(peer);
                        }
                    });
                    this.peerTable.add(peerButton).padBottom(6);
                    this.peerTable.row();
                }
            }
        } catch (Exception e) {
            this.statusLabel.setText("Bluetooth error: " + e.getMessage());
        }
    }

    private void setBusy(String message) {
        this.statusLabel.setText(message);
        this.hostButton.setDisabled(true);
    }

    private void reset(String message) {
        this.statusLabel.setText(message);
        this.hostButton.setDisabled(false);
    }

    private void cancelPending() {
        if (platform() != null) {
            platform().cancel();
        }
        this.hostButton.setDisabled(false);
    }

    @Override
    public void render(float delta) {
        super.render(delta);

        this.stage.act(delta);
        this.stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        this.stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        super.dispose();
        Gdx.app.log(TAG, "dispose");

        if (this.stage != null) this.stage.dispose();
        if (this.skin != null) this.skin.dispose();
    }
}
