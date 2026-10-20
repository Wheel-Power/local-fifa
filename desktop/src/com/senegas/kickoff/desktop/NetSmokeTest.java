package com.senegas.kickoff.desktop;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.senegas.kickoff.net.ClientSession;
import com.senegas.kickoff.net.HostSession;
import com.senegas.kickoff.net.Protocol;
import com.senegas.kickoff.net.Snapshot;
import com.senegas.kickoff.net.StreamLink;
import com.senegas.kickoff.utils.RuleBook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Headless smoke test: exercises the wire protocol over a TCP loopback pair
 * and the pitch rule geometry. Run with:
 *   ./gradlew desktop:runSmoke
 */
public class NetSmokeTest {
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testProtocolLoopback();
        testRuleGeometry();
        testRestartVelocity();

        System.out.println();
        if (failures > 0) {
            System.out.println("FAILED: " + failures + " check(s)");
            System.exit(1);
        } else {
            System.out.println("ALL CHECKS PASSED");
        }
    }

    private static void testProtocolLoopback() throws Exception {
        final ServerSocket serverSocket = new ServerSocket(0);
        final int port = serverSocket.getLocalPort();

        final Socket[] serverSide = new Socket[1];
        Thread acceptor = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSide[0] = serverSocket.accept();
                } catch (IOException ignored) {
                }
            }
        });
        acceptor.start();

        Socket clientSide = new Socket("127.0.0.1", port);
        acceptor.join(3000);

        check(serverSide[0] != null, "loopback server accepted");
        StreamLink hostLink = new StreamLink(serverSide[0].getInputStream(), serverSide[0].getOutputStream(), serverSide[0]);
        StreamLink clientLink = new StreamLink(clientSide.getInputStream(), clientSide.getOutputStream(), clientSide);

        HostSession host = new HostSession(hostLink);
        ClientSession client = new ClientSession(clientLink);

        // client input reaches the host
        client.sendInput(7, 12.5f, -3.25f);
        waitUntil(1000, new Condition() {
            @Override
            public boolean ok() {
                return host.hasRemoteInput();
            }
        });
        check(host.hasRemoteInput(), "host received remote input");
        check(Math.abs(host.remoteX() - 12.5f) < 1e-4 && Math.abs(host.remoteY() + 3.25f) < 1e-4,
                "remote input values decoded");

        // host snapshot reaches the client with all fields intact
        Snapshot s = new Snapshot();
        s.tick = 1234;
        s.stateId = 3;
        s.scoreHome = 2;
        s.scoreAway = 1;
        s.timeLeftSeconds = 177;
        s.kickoffAttack = 1;
        s.ballX = 100.5f;
        s.ballY = 200.25f;
        s.ballZ = 7.75f;
        s.ballVx = 1.5f;
        s.ballVy = -2.5f;
        s.ballVz = 0.25f;
        for (int i = 0; i < 20; i++) {
            s.homeXY[i] = i * 1.5f;
            s.awayXY[i] = 900f - i;
        }
        s.restartX = 176f;
        s.restartY = 94f;
        host.sendSnapshot(s);

        waitUntil(1000, new Condition() {
            @Override
            public boolean ok() {
                return client.latest() != null;
            }
        });
        Snapshot r = client.latest();
        check(r != null, "client received snapshot");
        if (r != null) {
            check(r.tick == 1234 && r.stateId == 3 && r.scoreHome == 2 && r.scoreAway == 1
                    && r.timeLeftSeconds == 177 && r.kickoffAttack == 1, "snapshot header fields");
            check(Math.abs(r.ballX - 100.5f) < 1e-3 && Math.abs(r.ballZ - 7.75f) < 1e-3
                    && Math.abs(r.ballVy + 2.5f) < 1e-3, "snapshot ball fields");
            check(Math.abs(r.homeXY[7] - 10.5f) < 1e-3 && Math.abs(r.awayXY[19] - 881f) < 1e-3, "snapshot player arrays");
            check(Math.abs(r.restartX - 176f) < 1e-3 && Math.abs(r.restartY - 94f) < 1e-3, "snapshot restart point");
        }

        host.close();
        client.close();
        serverSocket.close();
    }

    private static void testRuleGeometry() {
        // top goal mouth, under the bar: away scores
        check(RuleBook.goalScored(v(600, 100, 0), v(600, 60, 0)) == 1, "top goal scored");
        // bottom goal mouth: home scores
        check(RuleBook.goalScored(v(650, 1400, 0), v(650, 1500, 0)) == 2, "bottom goal scored");
        // wide of the posts: no goal, out over the end line
        check(RuleBook.goalScored(v(300, 100, 0), v(300, 60, 0)) == 0, "wide shot is no goal");
        check(RuleBook.outOfPlay(v(300, 100, 0), v(300, 60, 0), new Vector2()), "wide shot is out");
        // over the bar (z ~50px at the line, crossbar is 32px)
        check(RuleBook.goalScored(v(600, 100, 45), v(600, 60, 55)) == 0, "over the bar is no goal");
        check(RuleBook.outOfPlay(v(600, 100, 45), v(600, 60, 55), new Vector2()), "over the bar is out");
        // side line out with clamped restart point
        Vector2 out = new Vector2();
        check(RuleBook.outOfPlay(v(200, 700, 0), v(150, 700, 0), out), "side line out detected");
        check(Math.abs(out.x - (RuleBook.FIELD_LEFT_X + 14f)) < 0.01f, "restart clamped inside (left)");
        // in play
        check(!RuleBook.outOfPlay(v(500, 700, 0), v(520, 720, 0), new Vector2()), "normal play is not out");
        check(RuleBook.goalScored(v(500, 700, 0), v(520, 720, 0)) == 0, "normal play is no goal");
    }

    private static void testRestartVelocity() {
        Vector3 v = RuleBook.restartKickVelocity(new Vector2(RuleBook.FIELD_LEFT_X + 14, 700));
        check(v.len() > 100f, "restart kick has power");
        check(v.x > 0, "restart kicks back towards the field centre");
    }

    private static Vector3 v(float x, float y, float z) {
        return new Vector3(x, y, z);
    }

    private interface Condition {
        boolean ok();
    }

    private static void waitUntil(int timeoutMs, Condition condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.ok()) return;
            Thread.sleep(10);
        }
    }

    private static void check(boolean ok, String what) {
        if (ok) {
            System.out.println("  PASS  " + what);
        } else {
            failures++;
            System.out.println("  FAIL  " + what);
        }
    }
}
