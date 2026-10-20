package com.senegas.kickoff.desktop;

import com.senegas.kickoff.net.Link;
import com.senegas.kickoff.net.Platform;
import com.senegas.kickoff.net.StreamLink;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Desktop platform: multiplayer over TCP loopback so the exact same netcode
 * can be exercised by running two desktop instances.
 */
public class DesktopPlatform implements Platform {
    public static final int PORT = 4761;

    private volatile boolean cancelled;
    private volatile ServerSocket serverSocket;
    private volatile Socket clientSocket;

    @Override
    public void host(final Callback<Link> callback) {
        this.cancelled = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket ss = new ServerSocket(PORT);
                    DesktopPlatform.this.serverSocket = ss;
                    Socket socket = ss.accept();
                    if (DesktopPlatform.this.cancelled) {
                        socket.close();
                        ss.close();
                        return;
                    }
                    callback.onReady(new StreamLink(socket.getInputStream(), socket.getOutputStream(), socket, ss));
                } catch (IOException e) {
                    if (!DesktopPlatform.this.cancelled) callback.onError(e);
                }
            }
        }, "desktop-host").start();
    }

    @Override
    public void connect(final Peer peer, final Callback<Link> callback) {
        this.cancelled = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket("127.0.0.1", PORT);
                    DesktopPlatform.this.clientSocket = socket;
                    if (DesktopPlatform.this.cancelled) {
                        socket.close();
                        return;
                    }
                    callback.onReady(new StreamLink(socket.getInputStream(), socket.getOutputStream(), socket));
                } catch (IOException e) {
                    if (!DesktopPlatform.this.cancelled) callback.onError(e);
                }
            }
        }, "desktop-connect").start();
    }

    @Override
    public void cancel() {
        this.cancelled = true;
        closeQuietly(this.serverSocket);
        closeQuietly(this.clientSocket);
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public List<Peer> peers() {
        List<Peer> peers = new ArrayList<Peer>();
        peers.add(new Peer("loopback", "This machine (TCP loopback)"));
        return peers;
    }
}
