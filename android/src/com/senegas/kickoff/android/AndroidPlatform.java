package com.senegas.kickoff.android;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import com.senegas.kickoff.net.Link;
import com.senegas.kickoff.net.Platform;
import com.senegas.kickoff.net.StreamLink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Android platform: classic Bluetooth SPP (RFCOMM). Insecure RFCOMM is used
 * so players do not have to fight the system pairing dialog inside the game —
 * they simply pair once in Android settings and pick the phone in the lobby.
 */
public class AndroidPlatform implements Platform {
    private static final UUID SPP_UUID = UUID.fromString("5f1a2b3c-0000-4000-8000-0c1d2e3f4a5b");
    private static final String SERVICE_NAME = "LocalFIFA";

    private final Context context;

    private volatile boolean cancelled;
    private volatile BluetoothServerSocket serverSocket;
    private volatile BluetoothSocket clientSocket;

    public AndroidPlatform(Context context) {
        this.context = context.getApplicationContext();
    }

    private BluetoothAdapter adapter() {
        BluetoothManager manager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    @Override
    public void host(final Callback<Link> callback) {
        this.cancelled = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BluetoothAdapter adapter = adapter();
                    if (adapter == null || !adapter.isEnabled()) {
                        callback.onError(new IllegalStateException("Bluetooth is off"));
                        return;
                    }
                    BluetoothServerSocket ss = adapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID);
                    AndroidPlatform.this.serverSocket = ss;
                    BluetoothSocket socket = ss.accept();
                    if (AndroidPlatform.this.cancelled) {
                        closeQuietly(socket);
                        closeQuietly(ss);
                        return;
                    }
                    callback.onReady(new StreamLink(socket.getInputStream(), socket.getOutputStream(), socket, ss));
                } catch (IOException e) {
                    if (!AndroidPlatform.this.cancelled) callback.onError(e);
                }
            }
        }, "bt-host").start();
    }

    @Override
    public void connect(final Peer peer, final Callback<Link> callback) {
        this.cancelled = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BluetoothAdapter adapter = adapter();
                    if (adapter == null || !adapter.isEnabled()) {
                        callback.onError(new IllegalStateException("Bluetooth is off"));
                        return;
                    }
                    adapter.cancelDiscovery();
                    BluetoothSocket socket = adapter.getRemoteDevice(peer.id)
                            .createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                    AndroidPlatform.this.clientSocket = socket;
                    socket.connect();
                    if (AndroidPlatform.this.cancelled) {
                        closeQuietly(socket);
                        return;
                    }
                    callback.onReady(new StreamLink(socket.getInputStream(), socket.getOutputStream(), socket));
                } catch (IOException e) {
                    if (!AndroidPlatform.this.cancelled) callback.onError(e);
                }
            }
        }, "bt-connect").start();
    }

    @Override
    public synchronized void cancel() {
        this.cancelled = true;
        BluetoothServerSocket ss = this.serverSocket;
        this.serverSocket = null;
        closeQuietly(ss);
        BluetoothSocket cs = this.clientSocket;
        this.clientSocket = null;
        closeQuietly(cs);
    }

    @SuppressLint("MissingPermission")
    @Override
    public List<Peer> peers() {
        List<Peer> peers = new ArrayList<Peer>();
        BluetoothAdapter adapter = adapter();
        if (adapter == null || !adapter.isEnabled()) {
            return peers;
        }
        Set<android.bluetooth.BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded != null) {
            for (android.bluetooth.BluetoothDevice device : bonded) {
                String name = device.getName();
                peers.add(new Peer(device.getAddress(), name == null ? device.getAddress() : name));
            }
        }
        return peers;
    }

    private static void closeQuietly(BluetoothServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void closeQuietly(BluetoothSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
