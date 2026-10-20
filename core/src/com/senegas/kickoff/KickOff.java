package com.senegas.kickoff;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.senegas.kickoff.managers.GameScreenManager;
import com.senegas.kickoff.net.Platform;
import com.senegas.kickoff.pitches.PitchFactory;

public class KickOff extends Game {

    public static String TITLE = "Local FIFA";
    public static String VERSION = "1.0.0";
    public static int APP_DESKTOP_WIDTH = 1280;
    public static int APP_DESKTOP_HEIGHT = 800;

    public static int V_WIDTH = 1280;
    public static int V_HEIGHT = 800;

    public GameScreenManager gsm;
    public AssetManager assets;

    public SpriteBatch batch;
    public ShapeRenderer shapeBatch;

    private final Platform platform;

    public KickOff(Platform platform) {
        this.platform = platform;
    }

    public Platform getPlatform() {
        return this.platform;
    }

    @Override
    public void create() {
        this.batch = new SpriteBatch();
        this.shapeBatch = new ShapeRenderer();

        this.assets = new AssetManager();
        PitchFactory.INSTANCE.initialize(this.assets);
        this.gsm = new GameScreenManager(this);
    }

    @Override
    public void render() {
        // keep pumping pending assets even if the loading screen was left early
        if (!this.assets.isFinished()) {
            this.assets.update();
        }

        super.render();

        if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        this.batch.dispose();
        this.shapeBatch.dispose();
        this.gsm.dispose();
        this.assets.dispose();
    }
}
