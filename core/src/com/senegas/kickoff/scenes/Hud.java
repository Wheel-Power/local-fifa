package com.senegas.kickoff.scenes;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.senegas.kickoff.screens.MatchScreen;

public class Hud implements Disposable {
    public Stage stage;
    private Viewport viewport;

    private Label homeLabel;
    private Label scoreLabel;
    private Label awayLabel;
    private Label timerLabel;
    private Label bannerLabel;

    private BitmapFont font;
    private BitmapFont bannerFont;

    private float bannerTime;

    private MatchScreen match;

    public Hud(MatchScreen match) {
        this.match = match;

        this.viewport = new ScreenViewport();
        this.stage = new Stage(this.viewport, match.getApp().batch);

        this.font = new BitmapFont();
        this.font.getData().setScale(2f);
        this.bannerFont = new BitmapFont();
        this.bannerFont.getData().setScale(4f);

        Table top = new Table();
        top.top();
        top.setFillParent(true);

        this.homeLabel = new Label("HOME", new Label.LabelStyle(this.font, new Color(1f, 0.3f, 0.3f, 1f)));
        this.scoreLabel = new Label("0 - 0", new Label.LabelStyle(this.font, Color.WHITE));
        this.awayLabel = new Label("AWAY", new Label.LabelStyle(this.font, new Color(0.35f, 0.55f, 1f, 1f)));
        this.timerLabel = new Label("3:00", new Label.LabelStyle(this.font, Color.WHITE));

        top.add(this.homeLabel).padTop(12).padRight(20);
        top.add(this.scoreLabel).padTop(12);
        top.add(this.awayLabel).padTop(12).padLeft(20);
        top.row();
        top.add(this.timerLabel).colspan(3).padTop(4);
        this.stage.addActor(top);

        Table center = new Table();
        center.setFillParent(true);
        this.bannerLabel = new Label("", new Label.LabelStyle(this.bannerFont, Color.YELLOW));
        this.bannerLabel.setVisible(false);
        center.add(this.bannerLabel);
        this.stage.addActor(center);
    }

    public void showBanner(String text, Color color) {
        this.bannerLabel.setText(text);
        this.bannerLabel.setColor(color);
        this.bannerLabel.setVisible(true);
        this.bannerTime = 3f;
    }

    public void update(float deltaTime) {
        this.scoreLabel.setText(this.match.getScoreHome() + " - " + this.match.getScoreAway());

        int totalSeconds = Math.max(0, (int) Math.ceil(this.match.getTimeLeft()));
        this.timerLabel.setText(String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60));

        if (this.bannerLabel.isVisible()) {
            this.bannerTime -= deltaTime;
            Color c = this.bannerLabel.getColor();
            c.a = (float) Math.min(1.0, Math.max(0.0, this.bannerTime));
            if (this.bannerTime <= 0) {
                this.bannerLabel.setVisible(false);
                c.a = 1f;
            }
        }
    }

    @Override
    public void dispose() {
        this.stage.dispose();
        this.font.dispose();
        this.bannerFont.dispose();
    }
}
