/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.test_auxiliary;

import android.util.Log;

import com.digi.wva.async.AlarmType;
import com.digi.wva.async.EventFactory;
import com.digi.wva.async.FaultCodeCommon;
import com.digi.wva.internal.VehicleData;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides a simulated WVA event channel to connect to.
 *
 * <p>
 *     Use {@link #getPort()} to determine the port to connect on,
 *     {@link #start()} to run the loop (in an automatically-created background thread),
 *     methods like {@link #sendVehicleData(String, double)} to send data through,
 *     {@link #triggerRemoteClose()} to close the socket, and
 *     {@link #shutdown()} to completely stop the event channel.
 * </p>
 */
public class SimulatedEventChannel {
    private static final String TAG = "SimulatedEventChannel";
    private final ServerSocket socket;
    private Socket client;
    private BlockingQueue<JSONObject> outputQueue = new ArrayBlockingQueue<JSONObject>(100, true);
    private boolean runLoop = true;
    private Thread worker;

    private final Object clientLock = new Object();

    private DecimalFormat vehicleDataValueFormat = new DecimalFormat("#.00000");

    AtomicInteger alarmSequence = new AtomicInteger(),
                  dataSequence = new AtomicInteger();
    DateTimeFormatter formatter = ISODateTimeFormat.dateTimeNoMillis();

    /**
     * Constructor.
     * @throws IOException if an error occurs while creating the socket
     */
    public SimulatedEventChannel() throws IOException {
        // Create a new server socket, on a random port, on localhost
        socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
        // Allow the port to be reused
        socket.setReuseAddress(true);
    }

    /**
     * Gets the port on which this event channel is being served.
     * @return the port to connect to
     */
    public int getPort() {
        return socket.getLocalPort();
    }

    private void mainloop() {
        PrintWriter writer;

        while (runLoop) {
            try {
                if (socket.isClosed()) {
                    // SimulatedEventChannel has been shut down.
                    return;
                }

                // Accept a client connection.
                synchronized (clientLock) {
                    client = socket.accept();
                    writer = new PrintWriter(client.getOutputStream(), true);
                }

                while (runLoop) {
                    JSONObject next = outputQueue.take();

                    Log.v(TAG, "Writing into event channel: " + next.toString());
                    writer.println(next.toString());
                }
            } catch (IOException e) {
                Log.w(TAG, "IOException in main loop");
                e.printStackTrace();
            } catch (InterruptedException e) {
                // Thread is interrupted by shutdown()
                runLoop = false;
            }
        }
    }

    /**
     * Commence the event channel's operations.
     */
    public void start() {
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                mainloop();
            }
        });
        worker.start();
    }

    /**
     * Permanently stop this event channel's operations.
     */
    public void shutdown() {
        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        worker.interrupt();
    }

    public boolean isShutDown() {
        return worker.isInterrupted() && socket.isClosed();
    }

    /**
     * Construct a template event channel JSON object, containing a timestamp and
     * with the top-level key corresponding to the event type ("data" or "alarm").
     * @param type the event type to use
     * @return a template JSON object
     * @throws JSONException if there is an error in building this JSON
     */
    private JSONObject buildStub(EventFactory.Type type) throws JSONException {
        JSONObject stub = new JSONObject();
        String timestamp = formatter.print(DateTime.now(DateTimeZone.UTC));

        // Add inner object
        stub.put(type == EventFactory.Type.SUBSCRIPTION ? "data" : "alarm",
                 new JSONObject().put("timestamp", timestamp));

        return stub;
    }

    /**
     * Used to generate vehicle data events.
     * @param eventType alarm or subscription
     * @param alarmType the alarm type. Leave null if using subscription
     * @param endpoint the endpoint name
     * @param value the value
     * @return a JSON object for the event channel
     * @throws JSONException if there's an error building the JSON
     */
    private JSONObject buildVehicleData(EventFactory.Type eventType, AlarmType alarmType, String endpoint, double value) throws JSONException {
        JSONObject stub = buildStub(eventType);
        JSONObject inner = stub.getJSONObject(eventType == EventFactory.Type.SUBSCRIPTION ? "data" : "alarm");


        // Make deep JSON object, copy timestamp
        JSONObject inner2 = new JSONObject().put("timestamp", inner.getString("timestamp"));
        inner2.put("value", vehicleDataValueFormat.format(value));
        // Place the deep object inside the inner one
        inner.put(endpoint, inner2);

        // Build out inner object
        inner.put("uri", "vehicle/data/" + endpoint);
        switch (eventType) {
            case ALARM:
                inner.put("short_name", endpoint + "~" + AlarmType.makeString(alarmType));
                inner.put("sequence", alarmSequence.incrementAndGet());
                break;
            case SUBSCRIPTION:
                inner.put("short_name", endpoint + VehicleData.SUB_SUFFIX);
                inner.put("sequence", dataSequence.incrementAndGet());
                break;
        }

        return stub;
    }

    /**
     * Used to generate fault code events.
     * @param eventType alarm or subscription
     * @param bus the CAN bus
     * @param type the message type
     * @param ecu the ECU name
     * @param value the value
     * @return a JSON object for the event channel
     * @throws JSONException if there's an error building the JSON
     */
    private JSONObject buildFaultCode(EventFactory.Type eventType, FaultCodeCommon.Bus bus, FaultCodeCommon.FaultCodeType type, String ecu, String value) throws JSONException {
        JSONObject stub = buildStub(eventType);
        JSONObject inner = stub.getJSONObject(eventType == EventFactory.Type.SUBSCRIPTION ? "data" : "alarm");

        // Make deep JSON object, copy timestamp
        JSONObject inner2 = new JSONObject().put("timestamp", inner.getString("timestamp"));
        inner2.put("value", value);
        // Place the deep object inside the inner one
        inner.put(ecu, inner2);

        // Build out inner object
        String ecuPath = FaultCodeCommon.createEcuPath(bus, type, ecu);
        inner.put("uri", FaultCodeCommon.createUri(ecuPath));
        String shortnameBase = ecuPath.replace('/', '~');
        switch (eventType) {
            case ALARM:
                inner.put("short_name", shortnameBase + "~change");
                inner.put("sequence", alarmSequence.incrementAndGet());
                break;
            case SUBSCRIPTION:
                inner.put("short_name", shortnameBase + "~dtcsub");
                inner.put("sequence", dataSequence.incrementAndGet());
                break;
        }

        return stub;
    }

    /**
     * Enqueue a new fault code subscription event to be sent through the event channel.
     * @param bus the CAN bus
     * @param type the message type
     * @param ecu the ECU name
     * @param value the fault code value
     * @throws JSONException if there is an error in building the JSON
     */
    public void sendFaultCodeData(FaultCodeCommon.Bus bus, FaultCodeCommon.FaultCodeType type, String ecu, String value) throws JSONException {
        outputQueue.add(buildFaultCode(EventFactory.Type.SUBSCRIPTION, bus, type, ecu, value));
    }

    /**
     * Enqueue a new fault code alarm event to be sent through the event channel.
     * @param bus the CAN bus
     * @param type the message type
     * @param ecu the ECU name
     * @param value the fault code value
     * @throws JSONException if there is an error in building the JSON
     */
    public void sendFaultCodeAlarm(FaultCodeCommon.Bus bus, FaultCodeCommon.FaultCodeType type, String ecu, String value) throws JSONException {
        outputQueue.add(buildFaultCode(EventFactory.Type.ALARM, bus, type, ecu, value));
    }

    /**
     * Enqueue a new vehicle data subscription event to be sent through the event channel.
     * @param endpoint the endpoint name
     * @param value the value
     * @throws JSONException if there is an error in building the JSON
     */
    public void sendVehicleData(String endpoint, double value) throws JSONException {
        outputQueue.add(buildVehicleData(EventFactory.Type.SUBSCRIPTION, null, endpoint, value));
    }

    /**
     * Enqueue a new vehicle data alarm event to be sent through the event channel.
     * @param type the alarm type to use
     * @param endpoint the endpoint name
     * @param value the value
     * @throws JSONException if there is an error in building the JSON
     */
    public void sendVehicleDataAlarm(AlarmType type, String endpoint, double value) throws JSONException {
        outputQueue.add(buildVehicleData(EventFactory.Type.ALARM, type, endpoint, value));
    }

    /**
     * Close the currently-connected 'client' socket, if any.
     */
    public void triggerRemoteClose() {
        synchronized (clientLock) {
            if (client == null) {
                Log.d(TAG, "triggerRemoteClose called without client socket");
            } else {
                try {
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                client = null;
            }
        }
    }
}
