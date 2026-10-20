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
import com.senegas.kickoff.screens.MatchScreen.Mode;

public class MainMenuScreen extends AbstractScreen {
    private static final String TAG = MainMenuScreen.class.getSimpleName();

    private Stage menustage;
    private TextureAtlas atlas;
    private Skin skin;
    private Table table;
    private BitmapFont white;

    public MainMenuScreen(KickOff app) {
        super(app);
    }

    @Override
    public void update(float deltaTime) {
    }

    @Override
    public void show() {
        this.menustage = new Stage();

        Gdx.input.setInputProcessor(this.menustage);

        this.atlas = new TextureAtlas("ui/button.pack");
        this.skin = new Skin(this.atlas);

        this.table = new Table(this.skin);
        this.table.setBounds(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        this.white = new BitmapFont(Gdx.files.internal("font/white.fnt"), false);

        TextButtonStyle textButtonStyle = new TextButtonStyle();
        textButtonStyle.up = this.skin.getDrawable("button.up");
        textButtonStyle.down = this.skin.getDrawable("button.down");
        textButtonStyle.pressedOffsetX = 1;
        textButtonStyle.pressedOffsetY = -1;
        textButtonStyle.font = this.white;

        Label.LabelStyle titleStyle = new Label.LabelStyle(this.white, null);
        Label title = new Label("LOCAL FIFA", titleStyle);
        title.setFontScale(2f);

        Label.LabelStyle hintStyle = new Label.LabelStyle(this.white, null);
        Label hint = new Label("Bluetooth arcade football", hintStyle);

        TextButton buttonSolo = new TextButton("SOLO MATCH", textButtonStyle);
        buttonSolo.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                MainMenuScreen.this.app.gsm.startMatch(Mode.SOLO, null);
            }
        });
        buttonSolo.pad(15);

        TextButton buttonBluetooth = new TextButton("BLUETOOTH MATCH", textButtonStyle);
        buttonBluetooth.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                MainMenuScreen.this.app.gsm.setScreen(GameScreenManager.STATE.MULTIPLAYER);
            }
        });
        buttonBluetooth.pad(15);

        TextButton buttonQuit = new TextButton("QUIT", textButtonStyle);
        buttonQuit.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });
        buttonQuit.pad(15);

        this.table.center();
        this.table.add(title).padBottom(6);
        this.table.row();
        this.table.add(hint).padBottom(40);
        this.table.row();
        this.table.add(buttonSolo).padBottom(12);
        this.table.row();
        this.table.add(buttonBluetooth).padBottom(12);
        this.table.row();
        this.table.add(buttonQuit);
        this.menustage.addActor(this.table);
    }

    @Override
    public void render(float delta) {
        super.render(delta);

        this.menustage.act(delta);
        this.menustage.draw();
    }

    @Override
    public void resize(int width, int height) {
        this.menustage.getViewport().update(width, height, true);
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

        if (this.menustage != null) this.menustage.dispose();
        if (this.skin != null) this.skin.dispose();
    }
}
