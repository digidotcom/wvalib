/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva;

import android.util.Pair;

import com.digi.wva.async.AlarmType;
import com.digi.wva.async.EventFactory;
import com.digi.wva.async.VehicleDataEvent;
import com.digi.wva.async.VehicleDataResponse;
import com.digi.wva.exc.WvaHttpException;
import com.digi.wva.internal.Ecus;
import com.digi.wva.test_auxiliary.IntegrationTestCase;
import com.digi.wva.test_auxiliary.MockStateListener;
import com.digi.wva.test_auxiliary.MockVehicleDataListener;
import com.digi.wva.test_auxiliary.MockWvaCallback;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Integration tests dealing specifically with Vehicle Data; and event subscription and
 * configuration thereof.
 */
public class VehicleDataIntegrationTest extends IntegrationTestCase {

    public static final float VALUE_PRECISION = 1f / 100000;

    public void testSubscriptionCatchAll() throws Exception {
        final int TEST_POINTS = 100;

        /* Generate test data
             Doing some simple randomization to create unique scenarios each time the test is run,
             all of which have a common structure.  The test generates a number of test points
             which it will send through the system and then ensures that it sees the same data in
             order when it validates.
         */

        String[] endpoints = {"EngineSpeed", "FuelLevel", "VehicleSpeed"};

        Random random = new Random();

        List<Pair<String,Double>> testData = new ArrayList<Pair<String,Double>>();
        for (int i = 0 ; i < TEST_POINTS ; i++) {
            Pair<String, Double> p = new Pair<String, Double>(
                    endpoints[random.nextInt(endpoints.length)],  // Random endpoint
                    random.nextDouble() * 100                     // Random value [0-100)
            );
            testData.add(p);
        }

        // Configure WVA object
        MockVehicleDataListener listener = new MockVehicleDataListener();
        wva.setVehicleDataListener(listener);

        // Connect and send test data
        MockStateListener state = new MockStateListener();
        wva.setEventChannelStateListener(state);
        wva.connectEventChannel(events.getPort());
        state.expectOnConnected();

        for (Pair<String, Double> p : testData) {
            events.sendVehicleData(p.first, p.second);
        }

        // Validate test data
        for (Pair<String, Double> p : testData) {
            VehicleDataEvent e = listener.expectEvent();
            assertEquals(p.first, e.getEndpoint());
            double delta = Math.abs(p.second - e.getResponse().getValue());
            assertTrue("Values not close enough", delta < VALUE_PRECISION);
        }

        // Should have consumed it all
        listener.verifyNoEvents();

        // That remove actually effects a removal of the listener is a unit-test concern, not
        // to be validated readily here.
        wva.removeVehicleDataListener();
    }

    public void testSubscriptionSpecificListeners() throws Exception {
        Random random = new Random();
        String[] endpoints = { "FuelLevel", "VehicleSpeed"};
        MockVehicleDataListener[] listeners = new MockVehicleDataListener[endpoints.length];

        // Listen for distinct endpoints
        for (int i = 0 ; i < endpoints.length ; i++) {
            listeners[i] = new MockVehicleDataListener();
            wva.setVehicleDataListener(endpoints[i], listeners[i]);
        }

        // Connect and send test data
        MockStateListener state = new MockStateListener();
        wva.setEventChannelStateListener(state);
        wva.connectEventChannel(events.getPort());
        state.expectOnConnected();

        // For each endpoint in the test, pass through 100 data points and validate
        for (int index = 0 ; index < endpoints.length ; index++) {
            for (int dataPoint = 0; dataPoint < 100; dataPoint++) {
                double value = random.nextDouble() * 1000000;
                events.sendVehicleData(endpoints[index], value);
                VehicleDataEvent e = listeners[index].expectEvent();
                assertEquals(endpoints[index], e.getEndpoint());
                double delta = Math.abs(value - e.getResponse().getValue());
                assertTrue("Values not close enough", delta < VALUE_PRECISION);
            }
        }

        for (MockVehicleDataListener l : listeners) {
            l.verifyNoEvents();
        }
    }

    /**
     * Test that  the appropriate subscription ws call is generated.
     * <p>
     * This should be a PUT to {@code /ws/subscriptions/&lt;short_name&gt;} with a JSON object
     * that contains {@code uri, buffer} and {@code interval} elements.  The {@code short_name}
     * is formed from the name of the endpoint with ~sub appended. The {@code uri} and {@code
     * interval} elements to be formed from the parameters of the subscribe call,
     * {@code buffer} to be TBD.
     */
    public void testSubscribe() throws Exception {
        // Respond with success
        MockResponse response = new MockResponse();
        response.setResponseCode(201);
        ws.enqueue(response);

        // Perform test call
        MockWvaCallback<Void> cb = new MockWvaCallback<Void>("FuelLevel subscribe");
        wva.subscribeToVehicleData("FuelLevel", 1, cb);
        cb.expectResponse();

        // Verify callback behavior
        assertNull(cb.error);

        RecordedRequest record = ws.takeRequest();
        assertEquals("PUT", record.getMethod());
        assertEquals("/ws/subscriptions/FuelLevel~sub", record.getPath());
        JSONObject body = new JSONObject(record.getUtf8Body());

        // Verify body contents
        JSONObject subscription = body.getJSONObject("subscription");
        assertEquals("vehicle/data/FuelLevel", subscription.getString("uri"));
        assertEquals(1, subscription.getInt("interval"));
    }

    public void testSubscribeError() throws Exception {
        // Queue an error
        MockResponse response = new MockResponse();
        response.setResponseCode(400);
        ws.enqueue(response);

        // Perform test call
        MockWvaCallback<Void> cb = new MockWvaCallback<Void>("Error callback");
        wva.subscribeToVehicleData("FuelLevel", 1, cb);
        cb.expectResponse();

        // Verify callback behavior
        assertNotNull(cb.error);

    }

    /**
     * Test that  the appropriate unsubscribe ws call is generated.
     * <p>
     * This should be a DELETE to {@code /ws/subscriptions/&lt;short_name&gt;}.
     * The {@code short_name} is formed from the name of the endpoint with ~sub appended.
     *
     */
    public void testUnsubscribe() throws Exception {
        // Respond with success
        MockResponse response = new MockResponse();
        response.setResponseCode(200);
        ws.enqueue(response);

        // Perform test call
        MockWvaCallback<Void> cb = new MockWvaCallback<Void>("FuelLevel unsubscribe");
        wva.unsubscribeFromVehicleData("FuelLevel", cb);
        cb.expectResponse();

        // Verify callback behavior
        assertNull(cb.error);

        RecordedRequest record = ws.takeRequest();
        assertEquals("DELETE", record.getMethod());
        assertEquals("/ws/subscriptions/FuelLevel~sub", record.getPath());
        assertEquals("", record.getUtf8Body());
    }


    /**
     * Test the happy-path behavior of fetchVehicleData
     */
    public void testFetchVehicleData() throws Exception {
        // Respond with a value of 55
        MockResponse response = new MockResponse();
        JSONObject inner = new JSONObject();
        inner.put("timestamp", "2014-01-01T12:00:00Z");
        inner.put("value", 55.000);
        JSONObject body = new JSONObject().put("EngineTemperature", inner);
        response.setBody(body.toString());
        ws.enqueue(response);

        // Perform test call
        MockWvaCallback<VehicleDataResponse> callback = new MockWvaCallback<VehicleDataResponse>("fetchVehicleData");
        wva.fetchVehicleData("EngineTemperature", callback);
        callback.expectResponse();

        // Verify callback behavior
        assertNull(callback.error);
        assertNotNull(callback.response);
        assertEquals(55.0, callback.response.getValue());

        // Verify the HTTP request
        RecordedRequest request = ws.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/ws/vehicle/data/EngineTemperature", request.getPath());
        assertEquals("", request.getUtf8Body());
    }

    /**
     * Verify the behavior of fetchVehicleData when the WVA responds with non-successful
     * error codes or an invalid body.
     */
    public void testFetchVehicleDataErrors() throws Exception {
        //===========================================
        // Test 400 error

        // Enqueue a 'Bad Request' response
        ws.enqueue(new MockResponse().setResponseCode(400));

        // Perform test call
        MockWvaCallback<VehicleDataResponse> callback = new MockWvaCallback<VehicleDataResponse>("fetchVehicleData with errors");
        wva.fetchVehicleData("EngineTemperature", callback);
        callback.expectResponse();

        // Verify callback behavior
        assertNull(callback.response);
        assertNotNull(callback.error);
        assertTrue("Error is not WvaHttpBadRequest", callback.error instanceof WvaHttpException.WvaHttpBadRequest);

        // Verify the HTTP request
        RecordedRequest request = ws.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/ws/vehicle/data/EngineTemperature", request.getPath());
        assertEquals("", request.getUtf8Body());

        //===========================================
        // Test invalid body

        // Enqueue a response with an invalid body
        ws.enqueue(new MockResponse().setBody("Lorem Ipsum"));

        // Perform test call
        callback = new MockWvaCallback<VehicleDataResponse>("fetchVehicleData with invalid body");
        wva.fetchVehicleData("EngineTemperature", callback);
        callback.expectResponse();

        // Verify callback behavior
        assertNull(callback.response);
        assertNotNull(callback.error);
        assertEquals("Value Lorem of type java.lang.String cannot be converted to JSONObject", callback.error.getMessage());

        // Verify the HTTP request
        request = ws.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/ws/vehicle/data/EngineTemperature", request.getPath());
        assertEquals("", request.getUtf8Body());
    }

    /** Test vehicle data alarm creation by exercising a series of create requests. */
    public void testAlarmCreationAbove() throws Exception {
        // Successful creation
        ws.enqueue(new MockResponse().setResponseCode(201));

        MockWvaCallback<Void> cb = new MockWvaCallback<Void>("EngineTemperature test");
        wva.createVehicleDataAlarm("EngineTemperature", AlarmType.ABOVE, 90, 4, cb);
        cb.expectResponse();

        assertNull(cb.error);

        RecordedRequest req = ws.takeRequest();
        assertEquals("PUT", req.getMethod());
        assertEquals("/ws/alarms/EngineTemperature~above", req.getPath());

        JSONObject alarm = new JSONObject(req.getUtf8Body()).getJSONObject("alarm");
        assertEquals("vehicle/data/EngineTemperature", alarm.getString("uri"));
        assertEquals("above", alarm.getString("type"));
        assertEquals("queue", alarm.getString("buffer"));
        assertEquals(90.0, alarm.getDouble("threshold"));
        assertEquals(4, alarm.getInt("interval"));
    }

    /** Test vehicle data alarm creation by exercising a series of create requests. */
    public void testAlarmCreationBelow() throws Exception {
        // Successful creation
        ws.enqueue(new MockResponse().setResponseCode(201));

        MockWvaCallback<Void> cb = new MockWvaCallback<Void>("EngineTemperature test");
        wva.createVehicleDataAlarm("EngineTemperature", AlarmType.BELOW, 85, 4, cb);
        cb.expectResponse();

        assertNull(cb.error);

        RecordedRequest req = ws.takeRequest();
        assertEquals("PUT", req.getMethod());
        assertEquals("/ws/alarms/EngineTemperature~below", req.getPath());

        JSONObject alarm = new JSONObject(req.getUtf8Body()).getJSONObject("alarm");
        assertEquals("vehicle/data/EngineTemperature", alarm.getString("uri"));
        assertEquals("below", alarm.getString("type"));
        assertEquals("queue", alarm.getString("buffer"));
        assertEquals(85.0, alarm.getDouble("threshold"));
        assertEquals(4, alarm.getInt("interval"));
    }

    /** Test vehicle data alarm creation by exercising a series of create requests. */
    public void testAlarmCreationChange() throws Exception {
        // Successful creation
        ws.enqueue(new MockResponse().setResponseCode(201));

        MockWvaCallback<Void> cb = new MockWvaCallback<Void>("EngineTemperature test");
        wva.createVehicleDataAlarm("EngineTemperature", AlarmType.CHANGE, 45, 33, cb);
        cb.expectResponse();

        assertNull(cb.error);

        RecordedRequest req = ws.takeRequest();
        assertEquals("PUT", req.getMethod());
        assertEquals("/ws/alarms/EngineTemperature~change", req.getPath());

        JSONObject alarm = new JSONObject(req.getUtf8Body()).getJSONObject("alarm");
        assertEquals("vehicle/data/EngineTemperature", alarm.getString("uri"));
        assertEquals("change", alarm.getString("type"));
        assertEquals("queue", alarm.getString("buffer"));
        assertEquals(45.0, alarm.getDouble("threshold"));
        assertEquals(33, alarm.getInt("interval"));
    }

    /** Test vehicle data alarm creation by exercising a series of create requests. */
    public void testAlarmCreationDelta() throws Exception {
        // Successful creation
        ws.enqueue(new MockResponse().setResponseCode(201));

        MockWvaCallback<Void> cb = new MockWvaCallback<Void>("EngineTemperature test");
        wva.createVehicleDataAlarm("EngineTemperature", AlarmType.DELTA, 90, 4, cb);
        cb.expectResponse();

        assertNull(cb.error);

        RecordedRequest req = ws.takeRequest();
        assertEquals("PUT", req.getMethod());
        assertEquals("/ws/alarms/EngineTemperature~delta", req.getPath());

        JSONObject alarm = new JSONObject(req.getUtf8Body()).getJSONObject("alarm");
        assertEquals("vehicle/data/EngineTemperature", alarm.getString("uri"));
        assertEquals("delta", alarm.getString("type"));
        assertEquals("queue", alarm.getString("buffer"));
        assertEquals(90.0, alarm.getDouble("threshold"));
        assertEquals(4, alarm.getInt("interval"));
    }

    public void testAlarmCreationFailure() throws Exception {
        // Failure response
        ws.enqueue(new MockResponse().setResponseCode(400));

        MockWvaCallback<Void> cb = new MockWvaCallback<Void>("Alarm creation failure");
        wva.createVehicleDataAlarm("EngineTemperature", AlarmType.ABOVE, 88, 4, cb);
        cb.expectResponse();

        assertNotNull(cb.error);
    }

    public void testAlarmCatchAll() throws Exception {
        // Configure WVA object
        MockVehicleDataListener listener = new MockVehicleDataListener();
        wva.setVehicleDataListener(listener);

        // Connect and send test data
        MockStateListener state = new MockStateListener();
        wva.setEventChannelStateListener(state);
        wva.connectEventChannel(events.getPort());
        state.expectOnConnected();

        events.sendVehicleDataAlarm(AlarmType.ABOVE, "EngineSpeed", 42.0);
        VehicleDataEvent e = listener.expectEvent();
        assertEquals("EngineSpeed", e.getEndpoint());
        VehicleDataResponse rsp = e.getResponse();
        assertEquals(EventFactory.Type.ALARM, e.getType());
        assertEquals(42.0, rsp.getValue());

        // Should have consumed it all
        listener.verifyNoEvents();
    }
}
