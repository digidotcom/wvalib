/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */
package com.digi.wva;

import com.digi.wva.async.FaultCodeEvent;
import com.digi.wva.test_auxiliary.IntegrationTestCase;
import com.digi.wva.test_auxiliary.MockFaultCodeListener;
import com.digi.wva.test_auxiliary.MockStateListener;
import com.digi.wva.test_auxiliary.MockWvaCallback;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import org.json.JSONObject;

import java.util.Random;

import static com.digi.wva.async.FaultCodeCommon.*;

/**
 * Integration tests dealing specifically with fault codes (a.k.a. dtc), and event subscription and
 * configuration thereof.
 */
public class FaultCodesIntegrationTest extends IntegrationTestCase {

    /**
     * Test that the appropriate subscription ws call is generated.
     * <p>
     * This should be a PUT to {@code /ws/subscriptions/&lt;short_name&gt;} with a JSON object
     * that contains {@code uri, buffer} and {@code interval} elements.  The {@code short_name}
     * is formed from the bus, message type, ecu name, and dtcsub separated by ~ characters. The
     * {@code uri} and {@code interval} elements to be formed from the parameters of the subscribe
     * call.
     */
    public void testSubscribe() throws Exception {
        // Respond with success
        MockResponse response = new MockResponse();
        response.setResponseCode(201);
        ws.enqueue(response);

        // Perform test call
        MockWvaCallback<Void> cb = new MockWvaCallback<Void>("FaultCodes subscribe");
        wva.subscribeToFaultCodes(Bus.CAN0,
                                  FaultCodeType.ACTIVE,
                                  "ecu0", 30, cb);
        cb.expectResponse();

        // Verify callback behavior
        assertNull(cb.error);

        RecordedRequest record = ws.takeRequest();
        assertEquals("PUT", record.getMethod());
        assertEquals("/ws/subscriptions/can0_active~ecu0~dtcsub", record.getPath());
        //JSONObject body = new JSONObject(record.getUtf8Body());
        JSONObject body = new JSONObject(record.getBody().readUtf8());
        // Verify body contents
        JSONObject subscription = body.getJSONObject("subscription");
        assertEquals("vehicle/dtc/can0_active/ecu0", subscription.getString("uri"));
        assertEquals(30, subscription.getInt("interval"));
    }

    private static Random random = new Random();

    /*
     * This private helper class generates either a random test endpoint when created with no
     * arguments or will generate the specified test endpoint when arguments are provided.
     */
    private class FaultCodeSubscriptionTestPoint {
        private Bus bus;
        private FaultCodeType messageType;
        private String ecu;
        private String value;

        /*
         * This constructor returns a test endpoint where the Bus, FaultCodeType (message type),
         * ecu string and value are all selected at random.
         */
        public FaultCodeSubscriptionTestPoint() {
            switch (random.nextInt(2)) {
            case 0:
                bus = Bus.CAN0;
                break;
            case 1:
                bus = Bus.CAN1;
                break;
            }
            switch (random.nextInt(2)) {
                case 0:
                    messageType = FaultCodeType.ACTIVE;
                    break;
                case 1:
                    messageType = FaultCodeType.INACTIVE;
                    break;
            }
            ecu = "ecu" + random.nextInt(100);
            value = String.format("%8x", random.nextLong());
        }

        /*
         * This constructor returns a test endpoint where the Bus, FaultCodeType (message type),
         * ecu string and value are specified as parameters. The data for the endpoint is
         * randomized.
         *
         * @param b Bus type either CAN0 or CAN1
         * @param m Message type either ACTIVE or INACTIVE
         * @param e String type containing the ecu of the form "ecu%d" where %d is an integer.
         */
        public FaultCodeSubscriptionTestPoint(Bus b, FaultCodeType m, String e) {
            bus = b;
            messageType = m;
            ecu = e;
            value = String.format("%8x", random.nextLong());
        }

        /* Accessor functions. */
        public Bus getBus() {return bus;}
        public FaultCodeType getMessageType() {return messageType;}
        public String getEcu() {return ecu;}
        public String getValue() {return value;}
        public void setBus(Bus b) {bus = b;}
        public void setMessageType(FaultCodeType m) {messageType = m;}
        public void setEcu(String e) {ecu = e;}
        public void setValue(String v) {value = v;}
    }

    /**
     * This method tests that an unfiltered listener will catch any faults received.
     */
    public void testSubscriptionCatchAll() throws Exception {
        final int TEST_POINTS = 100;

        /* Generate test data
             Doing some simple randomization to create unique scenarios each time the test is run,
             all of which have a common structure.  The test generates a number of test points
             which it will send through the system and then ensures that it sees the same data in
             order when it validates.
         */
        FaultCodeSubscriptionTestPoint[] testData= new FaultCodeSubscriptionTestPoint[TEST_POINTS];

        for (int i = 0; i < testData.length; i++) {
            testData[i] = new FaultCodeSubscriptionTestPoint();
        }

        // / Configure WVA object
        MockFaultCodeListener listener = new MockFaultCodeListener();
        wva.setFaultCodeListener(listener);

        // Connect and send test data
        MockStateListener state = new MockStateListener();
        wva.setEventChannelStateListener(state);
        wva.connectEventChannel(events.getPort());
        state.expectOnConnected();

        for (FaultCodeSubscriptionTestPoint i : testData) {
            events.sendFaultCodeData(i.getBus(), i.getMessageType(), i.getEcu(), i.getValue());
        }

        // Validate test data
        for (FaultCodeSubscriptionTestPoint i : testData) {
            FaultCodeEvent e = listener.expectEvent();
            assertEquals(i.getBus(), e.getBus());
            assertEquals(i.getEcu(), e.getEcu());
            assertEquals(i.getMessageType(), e.getMessageType());
            assertEquals(i.getValue(), e.getResponse().getValue());
        }

        // Should have consumed it all
        listener.verifyNoEvents();

        // That remove actually effects a removal of the listener is a unit-test concern, not
        // to be validated readily here.
        wva.removeFaultCodeListener();
    }

    /**
     * Tests that when a listener is created for a specific endpoint (Bus, Message Type and ECU)
     * is created. This method sets up several specific listeners and then sends events to those
     * listeners, and then validates that the events were received, and that no,
     */
    public void testSubscriptionSpecificListeners() throws Exception {
        FaultCodeSubscriptionTestPoint[] testData = {
                new FaultCodeSubscriptionTestPoint(Bus.CAN0, FaultCodeType.ACTIVE, "ecu0"),
                new FaultCodeSubscriptionTestPoint(Bus.CAN0, FaultCodeType.INACTIVE, "ecu1"),
                new FaultCodeSubscriptionTestPoint(Bus.CAN1, FaultCodeType.ACTIVE, "ecu2"),
                new FaultCodeSubscriptionTestPoint(Bus.CAN1, FaultCodeType.INACTIVE, "ecu3")
        };

        MockFaultCodeListener[] listeners = new MockFaultCodeListener[testData.length];

        // Listen for distinct endpoints.
        for (int i = 0 ; i < testData.length ; i++) {
            listeners[i] = new MockFaultCodeListener();
            wva.setFaultCodeListener(testData[i].getBus(),
                                     testData[i].getMessageType(),
                                     testData[i].getEcu(),
                                     listeners[i]);
        }

        // Connect and send test data.
        MockStateListener state = new MockStateListener();
        wva.setEventChannelStateListener(state);
        wva.connectEventChannel(events.getPort());
        state.expectOnConnected();

        // For each fault code send, validate that it was received by the correct listener.
        for (int i = 0 ; i < testData.length ; i++) {
            events.sendFaultCodeData(testData[i].getBus(),
                                     testData[i].getMessageType(),
                                     testData[i].getEcu(),
                                     testData[i].getValue());
            FaultCodeEvent e = listeners[i].expectEvent();
            assertEquals(testData[i].getBus(), e.getBus());
            assertEquals(testData[i].getMessageType(), e.getMessageType());
            assertEquals(testData[i].getEcu(), e.getEcu());
            assertEquals(testData[i].getValue(), e.getResponse().getValue());
        }

        // Check that there are no events remaining.
        for (MockFaultCodeListener l : listeners) {
            l.verifyNoEvents();
        }
    }
}
