package com.senegas.kickoff.net;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Length-prefixed framing over a reliable ordered byte stream
 * ([len:2 LE][payload]). Works for both TCP sockets and Bluetooth SPP.
 */
public class StreamLink implements Link {
    private final InputStream in;
    private final OutputStream out;
    private final Closeable[] toClose;

    private volatile boolean open = true;
    private LinkListener listener;
    private final Object sendLock = new Object();

    public StreamLink(InputStream in, OutputStream out, Closeable... toClose) {
        this.in = in;
        this.out = out;
        this.toClose = toClose;
    }

    /** Spawns the reader thread; call once after the listener is set. */
    public void start() {
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                readLoop();
            }
        }, "link-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop() {
        try {
            while (open) {
                int lenHi = in.read();
                int lenLo = in.read();
                if (lenHi < 0 || lenLo < 0) break;
                int len = (lenHi << 8) | lenLo;
                if (len <= 0 || len > 64 * 1024) break;
                byte[] buf = new byte[len];
                int read = 0;
                while (read < len) {
                    int n = in.read(buf, read, len - read);
                    if (n < 0) {
                        return;
                    }
                    read += n;
                }
                LinkListener l = listener;
                if (l != null) {
                    l.onReceive(buf);
                }
            }
        } catch (IOException ignored) {
            // closed or reset
        } finally {
            handleClosed();
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void send(byte[] packet) {
        if (!open || packet == null || packet.length == 0) return;
        try {
            synchronized (sendLock) {
                out.write((packet.length >> 8) & 0xFF);
                out.write(packet.length & 0xFF);
                out.write(packet);
                out.flush();
            }
        } catch (IOException e) {
            handleClosed();
        }
    }

    @Override
    public void setListener(LinkListener listener) {
        this.listener = listener;
    }

    @Override
    public void close() {
        handleClosed();
    }

    private void handleClosed() {
        if (!open) return;
        open = false;
        for (Closeable c : toClose) {
            try {
                if (c != null) c.close();
            } catch (IOException ignored) {
            }
        }
        LinkListener l = listener;
        if (l != null) {
            l.onClosed();
        }
    }
}
