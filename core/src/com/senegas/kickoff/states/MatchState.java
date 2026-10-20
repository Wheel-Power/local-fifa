package com.senegas.kickoff.states;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.senegas.kickoff.pitches.FootballDimensionConstants;
import com.senegas.kickoff.pitches.Pitch;
import com.senegas.kickoff.screens.MatchScreen;
import com.senegas.kickoff.utils.Direction;
import com.senegas.kickoff.utils.RuleBook;

import static com.senegas.kickoff.pitches.FootballDimensionConstants.OUTER_TOP_EDGE_Y;
import static com.senegas.kickoff.pitches.FootballDimensionConstants.PITCH_HEIGHT_IN_PX;

public enum MatchState implements State<MatchScreen> {

    INTRODUCTION() {
        private float elapsedTimeInSeconds;

        @Override
        public void enter(MatchScreen match) {
            this.elapsedTimeInSeconds = 0;
            match.crowd.play(0.2f);
            match.getHomeTeam().setupIntroduction();
            match.getAwayTeam().setupIntroduction();

            match.getCameraHelper().setPosition(MathUtils.clamp(0,
                    match.getCamera().viewportWidth / 2 * match.getCamera().zoom,
                    Pitch.WIDTH - match.getCamera().viewportWidth / 2 * match.getCamera().zoom),
                    MathUtils.clamp(Pitch.HEIGHT,
                            match.getCamera().viewportHeight / 2 * match.getCamera().zoom,
                            Pitch.HEIGHT - match.getCamera().viewportHeight / 2 * match.getCamera().zoom));

            match.getCameraHelper().setTarget(new Vector2(352, (int) (PITCH_HEIGHT_IN_PX / 2 + OUTER_TOP_EDGE_Y + 16)));
        }

        @Override
        public void update(MatchScreen match) {
            this.elapsedTimeInSeconds += Gdx.graphics.getDeltaTime();

            if (match.getHomeTeam().isReady() && match.getAwayTeam().isReady() && this.elapsedTimeInSeconds > 7) {
                match.stateMachine.changeState(PREPAREFORKICKOFF);
            }
        }

        @Override
        public void exit(MatchScreen match) {
        }

        @Override
        public boolean onMessage(MatchScreen match, Telegram telegram) {
            return false;
        }
    },

    PREPAREFORKICKOFF() {
        private float elapsedTimeInSeconds;

        @Override
        public void enter(MatchScreen match) {
            this.elapsedTimeInSeconds = 0;
            match.getCameraHelper().setTarget(match.getPitch().getCenterSpot());
            match.getHomeTeam().setupKickoff(match.getKickoffAttack() == 0);
            match.getAwayTeam().setupKickoff(match.getKickoffAttack() == 1);
            match.requestShot("intro", 0.8f);
        }

        @Override
        public void update(MatchScreen match) {
            if (match.getHomeTeam().isReady() && match.getAwayTeam().isReady()) {
                this.elapsedTimeInSeconds += Gdx.graphics.getDeltaTime();
                if (this.elapsedTimeInSeconds > 2) {
                    match.stateMachine.changeState(INPLAY);
                }
            }
        }

        @Override
        public void exit(MatchScreen match) {
            match.whistle.play(0.2f);
            // ball placed at the centre spot for the kickoff
            Vector2 center = match.getPitch().getCenterSpot();
            match.getBall().setPosition(new Vector3(center.x, center.y, 0));
            match.getBall().kick(new Vector3(0, 0, 0), null);
        }

        @Override
        public boolean onMessage(MatchScreen match, Telegram telegram) {
            return false;
        }
    },

    INPLAY() {
        @Override
        public void enter(MatchScreen match) {
            match.getCameraHelper().setTarget(null);
            match.onInplayEntered();
        }

        @Override
        public void update(MatchScreen match) {
            match.cameraFollowBall();
            match.updateTactics();
        }

        @Override
        public void exit(MatchScreen match) {
        }

        @Override
        public boolean onMessage(MatchScreen match, Telegram telegram) {
            return false;
        }
    },

    /** Generic restart: throw-in on the sides, goal kick/corner style on the ends. */
    THROW_IN() {
        @Override
        public void enter(MatchScreen match) {
            long id = match.whistle.play(0.2f);
            Vector2 restart = match.getRestartPoint();
            match.getHomeTeam().setupThrowIn(new Vector3(restart.x, restart.y, 0), match.getLastTeamTouch() != match.getHomeTeam());
            match.getAwayTeam().setupThrowIn(new Vector3(restart.x, restart.y, 0), match.getLastTeamTouch() != match.getAwayTeam());
            match.getCameraHelper().setTarget(new Vector2(restart.x, restart.y));
        }

        @Override
        public void update(MatchScreen match) {
            if (match.getHomeTeam().isReady() && match.getAwayTeam().isReady()) {
                animateRestart(match);
                match.stateMachine.changeState(INPLAY);
            }
        }

        private void animateRestart(MatchScreen match) {
            Vector2 restart = match.getRestartPoint();
            Vector3 nudge = new Vector3(RuleBook.CENTER_X - restart.x, RuleBook.CENTER_Y - restart.y, 0).nor().scl(4);
            Vector3 position = new Vector3(restart.x + nudge.x, restart.y + nudge.y, 20);
            match.getBall().setPosition(position);
            match.getBall().kick(RuleBook.restartKickVelocity(restart), null);
        }

        @Override
        public void exit(MatchScreen match) {
        }

        @Override
        public boolean onMessage(MatchScreen match, Telegram telegram) {
            return false;
        }
    },

    GOAL() {
        private float elapsedTimeInSeconds;

        @Override
        public void enter(MatchScreen match) {
            this.elapsedTimeInSeconds = 0;
            match.requestShot("goal", 0.7f);
            match.crowd.play(0.6f);
            Color color = match.getLastScorer() == 0 ? match.getHomeTeam().getMainColor() : match.getAwayTeam().getMainColor();
            match.getHud().showBanner("GOAL!!!", color);
        }

        @Override
        public void update(MatchScreen match) {
            this.elapsedTimeInSeconds += Gdx.graphics.getDeltaTime();
            if (this.elapsedTimeInSeconds > 3) {
                match.stateMachine.changeState(PREPAREFORKICKOFF);
            }
        }

        @Override
        public void exit(MatchScreen match) {
        }

        @Override
        public boolean onMessage(MatchScreen match, Telegram telegram) {
            return false;
        }
    },

    FULLTIME() {
        private float elapsedTimeInSeconds;

        @Override
        public void enter(MatchScreen match) {
            this.elapsedTimeInSeconds = 0;
            match.whistle.play(0.4f);
            String result = match.getScoreHome() + " - " + match.getScoreAway();
            match.getHud().showBanner("FULL TIME  " + result, Color.WHITE);
        }

        @Override
        public void update(MatchScreen match) {
            this.elapsedTimeInSeconds += Gdx.graphics.getDeltaTime();
            if (this.elapsedTimeInSeconds > 6) {
                match.endMatch();
            }
        }

        @Override
        public void exit(MatchScreen match) {
        }

        @Override
        public boolean onMessage(MatchScreen match, Telegram telegram) {
            return false;
        }
    };

    public static final MatchState[] VALUES = values();

    public static MatchState byId(byte id) {
        if (id < 0 || id >= VALUES.length) return INPLAY;
        return VALUES[id];
    }

    @Override
    public void enter(MatchScreen match) {
    }

    @Override
    public void exit(MatchScreen match) {
    }

    @Override
    public boolean onMessage(MatchScreen match, Telegram telegram) {
        // We don't use messaging in this example
        return false;
    }
}
