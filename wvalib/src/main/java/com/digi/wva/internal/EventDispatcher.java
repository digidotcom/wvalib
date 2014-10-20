/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import org.json.JSONObject;

import android.util.Log;

import com.digi.wva.async.EventFactory;
import com.digi.wva.async.FaultCodeEvent;
import com.digi.wva.async.VehicleDataEvent;

import java.util.concurrent.BlockingQueue;

/**
 * The EventDispatcher takes {@link JSONObject JSONObjects} from the {@link EventChannel} queue,
 * parses them into events, and then hands them off to the {@link VehicleData} or
 * {@link FaultCodes} objects.
*/
public class EventDispatcher extends Thread {

    private boolean running;
    private final EventChannel channel;
    private final VehicleData vehicleData;
    private final FaultCodes faultCodes;

    /**
     * Constructor
     *
     * All events passing through this runnable should be well-formed.
     *
     * @param channel the {@link EventChannel} instance to use
     * @param vehicleData the {@link VehicleData} instance to use, for handling new vehicle data
     * @param faultCodes the {@link FaultCodes} instance to use, for handling new fault code information
     */
    public EventDispatcher(EventChannel channel, VehicleData vehicleData, FaultCodes faultCodes) {
        this.vehicleData = vehicleData;
        this.faultCodes = faultCodes;
        this.channel = channel;
        running = true;
    }


    /**
     * Interrupts the thread and exits the run method
     */
    public void stopThread() {
        this.running = false;
        this.interrupt();
    }

    /**
     * This object will continually attempt to take JSONObjects from the
     * EventChannel, parse them into events, and notify appropriate listeners (VehicleData,
     * FaultCodes, etc).
     * If an object received from the EventChannel is invalid or malformed, it will
     * be disregarded.
     */
    public void run() {
        BlockingQueue<JSONObject> queue = channel.getQueue();
        while(running) {
            JSONObject obj;
            try {
                obj = queue.take(); //blocks until available
//                Log.i("EventDispatcher", "Got message: " + obj.toString());
                AbstractEvent<?> e;
                try {
                    e = EventFactory.fromTCP(obj);
                } catch (Exception exc) {
                    Log.e("EventDispatcher", "Failed to parse event object", exc);
                    continue;
                }

                if (e != null) { // if obj contained a valid message
                    if (e instanceof VehicleDataEvent) {
                        // Pass event off to VehicleData to handle as new vehicle data
                        vehicleData.updateCachedVehicleData((VehicleDataEvent) e);
                    } else if (e instanceof FaultCodeEvent) {
                        // Pass event off to FaultCodes to handle as new fault code information
                        faultCodes.updateCachedFaultCode((FaultCodeEvent) e);
                    } else {
                        Log.i("EventDispatcher", "Got non-vehicle-data, non-DTC event.");
                    }
                }
                else {
                    Log.i("EventDispatcher", "Message wasn't parsed...");
                }
            } catch (InterruptedException e) {
                running = false;
            }
            if (Thread.interrupted()) {
                running = false;
            }
        }

    }
}
