package com.senegas.kickoff;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.senegas.kickoff.desktop.DesktopPlatform;
import com.senegas.kickoff.managers.GameScreenManager;
import com.senegas.kickoff.screens.LobbyScreen;

// Please note that on macOS your application needs to be started with the -XstartOnFirstThread JVM argument
public class DesktopLauncher {
    public static void main (String[] arg) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle(KickOff.TITLE + " v" + KickOff.VERSION);
        config.setWindowedMode(KickOff.APP_DESKTOP_WIDTH, KickOff.APP_DESKTOP_HEIGHT);
        config.useVsync(true);
        config.setForegroundFPS(60);

        final KickOff game = new KickOff(new DesktopPlatform());
        scheduleAutomation(game);
        new Lwjgl3Application(game, config);
    }

    /**
     * Automation for headless CI-style multiplayer runs:
     *   java -Dlocalfifa.role=host -Dlocalfifa.bot=1 -Dlocalfifa.exitAfter=60 -jar desktop.jar
     *   java -Dlocalfifa.role=join -Dlocalfifa.bot=1 -Dlocalfifa.exitAfter=60 -jar desktop.jar
     */
    private static void scheduleAutomation(final KickOff game) {
        final String role = System.getProperty("localfifa.role");
        if (role == null) return;

        if (System.getProperty("localfifa.bot") != null) {
            System.setProperty("localfifa.bot", "1");
        }

        Thread auto = new Thread(new Runnable() {
            @Override
            public void run() {
                // wait for the application to come up before touching Gdx
                for (int i = 0; i < 300 && Gdx.app == null; i++) {
                    sleep(100);
                }
                if (Gdx.app == null) return;
                sleep(1500);
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        game.gsm.setScreen(GameScreenManager.STATE.MULTIPLAYER);
                    }
                });
                sleep(1500);
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        Object screen = game.getScreen();
                        if (screen instanceof LobbyScreen) {
                            if ("host".equals(role)) {
                                ((LobbyScreen) screen).autoHost();
                            } else if ("join".equals(role)) {
                                ((LobbyScreen) screen).autoJoin();
                            }
                        }
                    }
                });
            }
        }, "auto-pilot");
        auto.setDaemon(true);
        auto.start();
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
