/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import android.util.Log;

import com.digi.wva.async.EventChannelStateListener;
import com.digi.wva.WVA;
import com.digi.wva.exc.DisconnectedException;
import com.digi.wva.exc.FailedConnectionException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Handles the TCP event channel connection between an Android application
 * and the WVA device.
 *
 * <p>You should not need to create an instance of this class manually.
 * Use the {@link com.digi.wva.WVA} class to manage all interactions with the WVA.</p>
 */
public class EventChannel extends Thread {
    private static final int MAX_LENGTH = 500;
    private static final String TAG = "wvalib EventChannel";
    private boolean running;
    private String hostname;
    private int port;
    private EventChannelStateListener listener;

    /***
     * Queue for incoming parsed messages. Will block
     * when full
     */
    private final BlockingQueue<JSONObject> incoming = new ArrayBlockingQueue<JSONObject>(100);

    /**
     * TCP socket for receiving messages from the WVA web service.
     * Does not write.
     */
    private Socket clientSock;

    private WVA device;


    /**
     * Constructor taking in the TCP socket to be used. Useful in unit-testing and
     * if the need to subclass EventChannel arises.
     * @param owner the {@link WVA} instance which will be using this EventChannel
     * @param socket the TCP socket through which event data will be received
     */
    public EventChannel(WVA owner, Socket socket) {
        this.device = owner;
        this.port = socket.getPort();
        this.running = false;
        clientSock = socket;
        if (listener == null) {
            listener = EventChannelStateListener.getDefault();
        }
    }

    /**
     * Constructor. Calls {@link #EventChannel(WVA, String, int, com.digi.wva.async.EventChannelStateListener)}}
     * with a null listener.
     *
     * @param owner the {@link WVA} instance which will be using this EventChannel
     * @param hostname the hostname/IP address of the WVA
     * @param port the TCP port to connect to
     */
    public EventChannel(WVA owner, String hostname, int port) {
        this(owner, hostname, port, null);
    }

    /**
     * Constructor.
     *
     * @param owner the {@link WVA} instance which will be using this EventChannel
     * @param hostname the hostname/IP address of the WVA
     * @param port the TCP port to connect to
     * @param listener the listener to use to monitor event channel status
     */
    public EventChannel(WVA owner, String hostname, int port, EventChannelStateListener listener) {
        this.device = owner;
        this.hostname = hostname;
        this.port = port;
        this.running = false;
        if (listener == null) {
            listener = EventChannelStateListener.getDefault();
        }
        this.listener = listener;
    }

    /**
     * Gets the queue where JSON data received via the event channel is placed
     * @return the received data queue
     */
    public BlockingQueue<JSONObject> getQueue() {
        return incoming;
    }

    /**
     * Sets the state listener used to report back changes in the connection state.
     * @param listener the listener to set
     */
    public void setStateListener(EventChannelStateListener listener) {
        this.listener = listener;
    }

    /**
     * Calling this method will permanently stop this thread.
     */
    public void stopThread() {
        this.running = false;
        this.interrupt();
    }

    /**
     * Calling this method will permanently stop this thread.
     *
     * @param e  one of a small set of IO related exceptions that are cleaned up by this routine.
     */
    void stopThread(IOException e) {
        if (this.clientSock != null) {
            try {
                this.clientSock.close();
            } catch (IOException e1) {
                Log.i(TAG, "IOException closing client socket in stopThread: " + e1.getMessage());
            }
        }

        //e.printStackTrace();

        if (e instanceof DisconnectedException) {
            listener.onRemoteClose(device, port);
        } else if (e instanceof FailedConnectionException) {
            listener.onFailedConnection(device, port);
        } else {
            listener.onError(device, e);
        }
        stopThread();
    }

    /** Returns true if the thread has been stopped, false otherwise */
    public boolean isStopped() {
        return !running;
    }

    /**
     * Read JSONObjects from a TCP stream into the incoming queue until either
     * the thread is interrupted or stopThread() is called. No validation is
     * performed on the objects, but they will be discarded if they are longer
     * than MAX_LENGTH characters.
     *
     */
    public void run() {
        if (clientSock == null) {
            try {
                clientSock = makeSocket();
            } catch (IOException e) {
                stopThread(new FailedConnectionException("Failed to connect to TCP socket on port " + port, e));
                listener.onDone(device);
                return;
            }
        }

        this.running = true;
        listener.onConnected(device);

        // Set up the input stream. If this fails, all hope is lost.
        BufferedReader in;

        try {
            in = new BufferedReader(new InputStreamReader(clientSock.getInputStream(), "UTF-8"));
        } catch (IOException e1) {
            stopThread(e1);
            listener.onDone(device);
            return;
        }


        StringBuilder builder = new StringBuilder();
        JSONObject j;

        while (running) {

            if (this.isInterrupted()) {
                break;
            }

            try {
                // Read a line out of the incoming buffer. This blocks
                // indefinitely
                String next = in.readLine();

                // If this string is null, we have reached an EOF or the connection
                // has otherwise been severed at the remote end.
                if (next == null) {
                    Log.i(TAG, "Socket closed on remote end");
                    stopThread(new DisconnectedException("Socket closed on remote end"));
                    continue;
                }

                // Append this string to a stringBuilder. If it creates a valid
                // JSONObject, add it to the queue, otherwise, continue building
                builder.append(next);
                int i = builder.indexOf("{");
                builder.delete(0, (i < 0) ? 0 : i);
                j = new JSONObject(builder.toString());
                incoming.put(j);
                builder = new StringBuilder();
            } catch(JSONException je) {
                // JSONException is caught whenever the builder does not have a
                // valid JSON string.

                if (builder.length() > MAX_LENGTH) {
                    //Message shouldn't be this long; abort!
                    // Should only happen upon receiving a rogue '{'
                    builder = new StringBuilder();
                }

            } catch (IOException e) {
                Log.i(TAG, "IOException in EventChannel");
                stopThread(e);

            } catch (Exception e) {
                Log.e(TAG, "Unknown Exception in EventChannel", e);
            }

        }

        // Close scanner (which closes all underlying structures)
        try {
            in.close();
            if (!clientSock.isClosed()) {
                clientSock.close();
            }
        } catch (IOException e) {
            Log.i(TAG, "IOException closing client socket or its reader: " + e.getMessage());
        }
        Log.d(TAG, "End of run()");
        listener.onDone(device);
    }

    /**
     * Create a new {@link Socket} pointed at this EventChannel's hostname and port.
     *
     * <p>This method is protected, rather than private, due to a bug between JaCoCo and
     * the Android build tools which causes the instrumented bytecode to be invalid when this
     * method is private:
     * <a href="http://stackoverflow.com/questions/17603192/dalvik-transformation-using-wrong-invoke-opcode" target="_blank">see StackOverflow question.</a>
     * </p>
     *
     * @return a new Socket
     * @throws IOException if an error occurs while creating the socket
     */
    protected Socket makeSocket() throws IOException {
        return new Socket(hostname, port);
    }
}
