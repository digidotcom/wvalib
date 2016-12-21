/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import com.digi.wva.async.AlarmType;
import com.digi.wva.async.EventFactory;
import com.digi.wva.async.VehicleDataEvent;
import com.digi.wva.async.VehicleDataListener;
import com.digi.wva.async.VehicleDataResponse;
import com.digi.wva.exc.EndpointUnknownException;
import com.digi.wva.exc.WvaHttpException;
import com.digi.wva.test_auxiliary.HttpClientSpoofer;
import com.digi.wva.test_auxiliary.JsonFactory;
import com.digi.wva.test_auxiliary.PassFailCallback;

import junit.framework.TestCase;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class VehicleDataTest extends TestCase {
    HttpClientSpoofer httpClient;
    JsonFactory jFactory = new JsonFactory();
    VehicleData dut;


    protected void setUp() throws Exception {
        httpClient = new HttpClientSpoofer("hostname");
        dut = new VehicleData(httpClient);

        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testInitVehicle() {
        VehicleData newVehicleData = new VehicleData(new HttpClient("hostname2"));
        assertNotNull(newVehicleData);
    }

    public void testFetchVehicleDataEndpoints() {
        PassFailCallback<Set<String>> cb1 = new PassFailCallback<Set<String>>();
        PassFailCallback<Set<String>> cb2 = new PassFailCallback<Set<String>>();

        httpClient.returnObject = jFactory.vehicleEndpoints();
        httpClient.success = true;

        dut.fetchVehicleDataEndpoints(cb1);
        assertTrue(cb1.success);
        assertEquals("GET vehicle/data/", httpClient.requestSummary);

        httpClient.success = false;
        dut.fetchVehicleDataEndpoints(cb2);
        assertFalse(cb2.success);
    }

    public void testFetchVehicleDataEndpoints_BadBody() throws Exception {
        PassFailCallback<Set<String>> cb = new PassFailCallback<Set<String>>();

        // First test: 'data' key not an array
        JSONObject body = new JSONObject();
        body.put("data", new JSONObject()); // data is supposed to be an array, not an object
        httpClient.returnObject = body;
        httpClient.success = true;

        dut.fetchVehicleDataEndpoints(cb);
        assertFalse(cb.success);
        assertNotNull(cb.error);
        assertEquals("Value {} at data of type org.json.JSONObject cannot be converted to JSONArray", cb.error.getMessage());

        // Second test: no 'data' key present
        PassFailCallback<Set<String>> cb2 = new PassFailCallback<Set<String>>();
        body.remove("data");
        dut.fetchVehicleDataEndpoints(cb2);
        assertFalse(cb2.success);
        assertNotNull(cb2.error);
        assertEquals("No `data` key in fetchVehicleDataEndpoints HTTP response.", cb2.error.getMessage());
    }

    public void testSubscriptionAndAlarmData() throws JSONException {
        dut.removeAllListeners();

        VehicleDataEvent subEvent = (VehicleDataEvent) EventFactory.fromTCP(jFactory.data());
        VehicleDataEvent alarmEvent = (VehicleDataEvent) EventFactory.fromTCP(jFactory.alarm());

        final boolean[] gotHereList = new boolean[2];
        gotHereList[0] = false;
        gotHereList[1] = false;
        addGotHereListeners(subEvent.getEndpoint(), gotHereList);
        dut.notifyListeners(subEvent);

        assertFalse(gotHereList[0]);
        assertTrue(gotHereList[1]);

        dut.notifyListeners(alarmEvent);

        assertTrue(gotHereList[0]);
        assertTrue(gotHereList[1]);
    }

    public void testUpdateCached_And_GetCached() throws JSONException {
        assertNull(dut.getCachedVehicleData("EngineSpeed"));
        JSONObject valTimeObject = jFactory.valTimeObj();
        VehicleDataResponse resp = new VehicleDataResponse(valTimeObject);

        dut.updateCachedVehicleData("vehicle/data/EngineSpeed", resp);
        VehicleDataResponse cached = dut.getCachedVehicleData("EngineSpeed");
        assertNotNull(cached);
        assertEquals(cached.getTime(), resp.getTime());
        assertEquals(cached.getValue(), resp.getValue());
    }

    public void testUpdateCached_And_GetCached_URI() throws JSONException {
        assertNull(dut.getCachedDataAtUri("vehicle/ignition"));
        JSONObject valTimeObject = jFactory.valTimeObj();
        VehicleDataResponse resp = new VehicleDataResponse(valTimeObject);

        dut.updateCachedVehicleData("vehicle/ignition", resp);

        VehicleDataResponse cached = dut.getCachedDataAtUri("vehicle/ignition");
        assertNotNull(cached);
        assertEquals(cached.getTime(), resp.getTime());
        assertEquals(cached.getValue(), resp.getValue());
    }

    public void testFetchNew() {
        httpClient.success = true;
        httpClient.returnObject = jFactory.vehicleDataEndpoint();

        String endpoint = (String) (httpClient.returnObject.keys().next());
        assertEquals("EngineSpeed", endpoint);

        // valid endpoint, successful response
        PassFailCallback<VehicleDataResponse> cb1 = new PassFailCallback<VehicleDataResponse>();
        dut.fetchVehicleData(endpoint, cb1);

        // valid endpoint, unsuccessful response
        PassFailCallback<VehicleDataResponse> cb2 = new PassFailCallback<VehicleDataResponse>();
        httpClient.success = false;
        dut.fetchVehicleData(endpoint, cb2);
        assertFalse(cb2.success);

        // invalid endpoint
        PassFailCallback<VehicleDataResponse> cb3 = new PassFailCallback<VehicleDataResponse>();
        httpClient.success = true;
        dut.fetchVehicleData("not a valid endpoint", cb3);
        assertFalse(cb3.success);

        // 404 error - EndpointUnknownException is sent to caller
        PassFailCallback<VehicleDataResponse> cb4 = new PassFailCallback<VehicleDataResponse>();
        httpClient.success = false;
        httpClient.failWith = new WvaHttpException.WvaHttpNotFound("ws/vehicle/data/foo", "");

        dut.fetchVehicleData("foo", cb4);
        assertFalse(cb4.success);
        assertTrue(cb4.error instanceof EndpointUnknownException);
        assertEquals("No vehicle data endpoint foo", cb4.error.getMessage());
    }

    public void testSubscribe() throws Exception {
        PassFailCallback<Void> successCb = new PassFailCallback<Void>(),
                               nonEmptyCb = new PassFailCallback<Void>(),
                               failureCb = new PassFailCallback<Void>(),
                               fail404Cb = new PassFailCallback<Void>();

        // Success
        httpClient.success = true;
        httpClient.returnObject = null;
        dut.subscribe("EngineSpeed", 10, successCb);
        assertTrue(successCb.success);
        assertNull(successCb.error);
        assertEquals("PUT subscriptions/EngineSpeed~sub", httpClient.requestSummary);
        // Verify the request body
        assertTrue("No subscription key in body", httpClient.requestBody.has("subscription"));
        JSONObject sub = httpClient.requestBody.getJSONObject("subscription");
        assertEquals(10, sub.getInt("interval"));
        assertEquals("vehicle/data/EngineSpeed", sub.getString("uri"));

        // Non-empty body
        httpClient.success = true;
        httpClient.returnObject = new JSONObject();
        dut.subscribe("EngineSpeed", 10, nonEmptyCb);
        assertFalse(nonEmptyCb.success);
        assertNotNull(nonEmptyCb.error);
        assertEquals("Unexpected response body: {}", nonEmptyCb.error.getMessage());

        // Error
        httpClient.success = false;
        httpClient.failWith = new Exception("Subscribe Failure");
        dut.subscribe("EngineSpeed", 10, failureCb);
        assertFalse(failureCb.success);
        assertNotNull(failureCb.error);
        assertEquals(httpClient.failWith, failureCb.error);

        // 404 error - they're handled specially
        httpClient.success = false;
        httpClient.failWith = new WvaHttpException.WvaHttpNotFound("/ws/subscriptions/EngineSpeed~sub", null);
        dut.subscribe("EngineSpeed", 10, fail404Cb);
        assertFalse(fail404Cb.success);
        assertNotNull(fail404Cb.error);
        assertEquals(EndpointUnknownException.class, fail404Cb.error.getClass());
        assertEquals("Vehicle data endpoint EngineSpeed does not exist.", fail404Cb.error.getMessage());
    }

    public void testSubscribeToUri() throws Exception {
        PassFailCallback<Void> successCb = new PassFailCallback<Void>(),
                nonEmptyCb = new PassFailCallback<Void>(),
                failureCb = new PassFailCallback<Void>(),
                fail404Cb = new PassFailCallback<Void>();

        // Success
        httpClient.success = true;
        httpClient.returnObject = null;
        dut.subscribeToUri("vehicle/ignition", 10, successCb);
        assertTrue(successCb.success);
        assertNull(successCb.error);
        assertEquals("PUT subscriptions/vehicle~ignition~sub", httpClient.requestSummary);
        // Verify the request body
        assertTrue("No subscription key in body", httpClient.requestBody.has("subscription"));
        JSONObject sub = httpClient.requestBody.getJSONObject("subscription");
        assertEquals(10, sub.getInt("interval"));
        assertEquals("vehicle/ignition", sub.getString("uri"));

        // Non-empty body
        httpClient.success = true;
        httpClient.returnObject = new JSONObject();
        dut.subscribeToUri("vehicle/ignition", 10, nonEmptyCb);
        assertFalse(nonEmptyCb.success);
        assertNotNull(nonEmptyCb.error);
        assertEquals("Unexpected response body: {}", nonEmptyCb.error.getMessage());

        // Error
        httpClient.success = false;
        httpClient.failWith = new Exception("subscribeToUri Failure");
        dut.subscribeToUri("vehicle/ignition", 10, failureCb);
        assertFalse(failureCb.success);
        assertNotNull(failureCb.error);
        assertEquals(httpClient.failWith, failureCb.error);

        // 404 error - they're handled specially
        httpClient.success = false;
        httpClient.failWith = new WvaHttpException.WvaHttpNotFound("/ws/subscriptions/vehicle~ignition~sub", null);
        dut.subscribeToUri("vehicle/ignition", 10, fail404Cb);
        assertFalse(fail404Cb.success);
        assertNotNull(fail404Cb.error);
        assertEquals(EndpointUnknownException.class, fail404Cb.error.getClass());
        assertEquals("URI vehicle/ignition does not exist.", fail404Cb.error.getMessage());

        // Purely for code coverage, since we don't/can't analyze log messages.
        dut.subscribeToUri("vehicle/data/foo", 10, null);
    }

    public void testUnsubscribe() throws Exception {
        PassFailCallback<Void> successCb = new PassFailCallback<Void>(),
                nonEmptyCb = new PassFailCallback<Void>(),
                failureCb = new PassFailCallback<Void>(),
                fail404Cb = new PassFailCallback<Void>();

        // Success
        httpClient.success = true;
        httpClient.returnObject = null;
        dut.unsubscribe("EngineSpeed", successCb);
        assertTrue(successCb.success);
        assertNull(successCb.error);
        assertEquals("DELETE subscriptions/EngineSpeed~sub", httpClient.requestSummary);

        // Non-empty body
        httpClient.success = true;
        httpClient.returnObject = new JSONObject();
        dut.unsubscribe("EngineSpeed", nonEmptyCb);
        assertFalse(nonEmptyCb.success);
        assertNotNull(nonEmptyCb.error);
        assertEquals("Unexpected response body: {}", nonEmptyCb.error.getMessage());

        // Error
        httpClient.success = false;
        httpClient.failWith = new Exception("Unsubscribe Failure");
        dut.unsubscribe("EngineSpeed", failureCb);
        assertFalse(failureCb.success);
        assertNotNull(failureCb.error);
        assertEquals(httpClient.failWith, failureCb.error);

        // 404 error - they're handled specially
        httpClient.failWith = new WvaHttpException.WvaHttpNotFound("subscriptions/EngineSpeed~sub", "");
        dut.unsubscribe("EngineSpeed", fail404Cb);
        assertFalse(fail404Cb.success);
        assertNotNull(fail404Cb.error);
        assertEquals(httpClient.failWith, fail404Cb.error);
    }

    public void testUnsubscribeFromUri() throws Exception {
        PassFailCallback<Void> successCb = new PassFailCallback<Void>(),
                nonEmptyCb = new PassFailCallback<Void>(),
                failureCb = new PassFailCallback<Void>(),
                fail404Cb = new PassFailCallback<Void>();

        // Success
        httpClient.success = true;
        httpClient.returnObject = null;
        dut.unsubscribeFromUri("vehicle/ignition", successCb);
        assertTrue(successCb.success);
        assertNull(successCb.error);
        assertEquals("DELETE subscriptions/vehicle~ignition~sub", httpClient.requestSummary);

        // Non-empty body
        httpClient.success = true;
        httpClient.returnObject = new JSONObject();
        dut.unsubscribeFromUri("vehicle/ignition", nonEmptyCb);
        assertFalse(nonEmptyCb.success);
        assertNotNull(nonEmptyCb.error);
        assertEquals("Unexpected response body: {}", nonEmptyCb.error.getMessage());

        // Error
        httpClient.success = false;
        httpClient.failWith = new Exception("unsubscribeFromUri Failure");
        dut.unsubscribeFromUri("vehicle/ignition", failureCb);
        assertFalse(failureCb.success);
        assertNotNull(failureCb.error);
        assertEquals(httpClient.failWith, failureCb.error);

        // 404 error - they're handled specially
        httpClient.failWith = new WvaHttpException.WvaHttpNotFound("subscriptions/vehicle~ignition~sub", "");
        dut.unsubscribeFromUri("vehicle/ignition", fail404Cb);
        assertFalse(fail404Cb.success);
        assertNotNull(fail404Cb.error);
        assertEquals(httpClient.failWith, fail404Cb.error);

        // Purely for code coverage, since we don't/can't analyze log messages.
        dut.unsubscribeFromUri("vehicle/data/foo", null);
    }

    public void testCreateAlarm() throws Exception {
        PassFailCallback<Void> successCb = new PassFailCallback<Void>(),
                nonEmptyCb = new PassFailCallback<Void>(),
                failureCb = new PassFailCallback<Void>(),
                fail404Cb = new PassFailCallback<Void>();

        // Success
        httpClient.success = true;
        httpClient.returnObject = null;
        dut.createAlarm("EngineSpeed", AlarmType.ABOVE, 0, 10, successCb);
        assertTrue(successCb.success);
        assertNull(successCb.error);
        assertEquals("PUT alarms/EngineSpeed~above", httpClient.requestSummary);
        // Verify the request body
        assertTrue("No alarm key in body", httpClient.requestBody.has("alarm"));
        JSONObject alarm = httpClient.requestBody.getJSONObject("alarm");
        assertEquals(10, alarm.getInt("interval"));
        assertEquals("vehicle/data/EngineSpeed", alarm.getString("uri"));
        assertEquals("above", alarm.getString("type"));
        assertEquals(0, alarm.getInt("threshold"));

        // Non-empty body
        httpClient.success = true;
        httpClient.returnObject = new JSONObject();
        dut.createAlarm("EngineSpeed", AlarmType.ABOVE, 0, 10, nonEmptyCb);
        assertFalse(nonEmptyCb.success);
        assertNotNull(nonEmptyCb.error);
        assertEquals("Unexpected response body: {}", nonEmptyCb.error.getMessage());

        // Error
        httpClient.success = false;
        httpClient.failWith = new Exception("CreateAlarm Failure");
        dut.createAlarm("EngineSpeed", AlarmType.ABOVE, 0, 10, failureCb);
        assertFalse(failureCb.success);
        assertNotNull(failureCb.error);
        assertEquals(httpClient.failWith, failureCb.error);

        // 404 error - they're handled specially
        httpClient.success = false;
        httpClient.failWith = new WvaHttpException.WvaHttpNotFound("/ws/alarms/EngineSpeed~above", null);
        dut.createAlarm("EngineSpeed", AlarmType.ABOVE, 0, 10, fail404Cb);
        assertFalse(fail404Cb.success);
        assertNotNull(fail404Cb.error);
        assertEquals(EndpointUnknownException.class, fail404Cb.error.getClass());
        assertEquals("Vehicle data endpoint EngineSpeed does not exist.", fail404Cb.error.getMessage());
    }

    public void testCreateUriAlarm() throws Exception {
        PassFailCallback<Void> successCb = new PassFailCallback<Void>(),
                nonEmptyCb = new PassFailCallback<Void>(),
                failureCb = new PassFailCallback<Void>(),
                fail404Cb = new PassFailCallback<Void>();

        // Success
        httpClient.success = true;
        httpClient.returnObject = null;
        dut.createUriAlarm("vehicle/ignition", AlarmType.CHANGE, 0, 10, successCb);
        assertTrue(successCb.success);
        assertNull(successCb.error);
        assertEquals("PUT alarms/vehicle~ignition~change", httpClient.requestSummary);
        // Verify the request body
        assertTrue("No alarm key in body", httpClient.requestBody.has("alarm"));
        JSONObject alarm = httpClient.requestBody.getJSONObject("alarm");
        assertEquals(10, alarm.getInt("interval"));
        assertEquals("vehicle/ignition", alarm.getString("uri"));
        assertEquals("change", alarm.getString("type"));
        assertEquals(0, alarm.getInt("threshold"));

        // Non-empty body
        httpClient.success = true;
        httpClient.returnObject = new JSONObject();
        dut.createUriAlarm("vehicle/ignition", AlarmType.CHANGE, 0, 10, nonEmptyCb);
        assertFalse(nonEmptyCb.success);
        assertNotNull(nonEmptyCb.error);
        assertEquals("Unexpected response body: {}", nonEmptyCb.error.getMessage());

        // Error
        httpClient.success = false;
        httpClient.failWith = new Exception("createUriAlarm Failure");
        dut.createUriAlarm("vehicle/ignition", AlarmType.CHANGE, 0, 10, failureCb);
        assertFalse(failureCb.success);
        assertNotNull(failureCb.error);
        assertEquals(httpClient.failWith, failureCb.error);

        // 404 error - they're handled specially
        httpClient.success = false;
        httpClient.failWith = new WvaHttpException.WvaHttpNotFound("/ws/alarms/vehicle~ignition~change", null);
        dut.createUriAlarm("vehicle/ignition", AlarmType.CHANGE, 0, 10, fail404Cb);
        assertFalse(fail404Cb.success);
        assertNotNull(fail404Cb.error);
        assertEquals(EndpointUnknownException.class, fail404Cb.error.getClass());
        assertEquals("URI vehicle/ignition does not exist.", fail404Cb.error.getMessage());

        // Purely for code coverage, since we don't/can't analyze log messages.
        dut.createUriAlarm("vehicle/data/foo", AlarmType.CHANGE, 0, 10, null);
    }

    public void testDeleteAlarm() throws Exception {
        PassFailCallback<Void> successCb = new PassFailCallback<Void>(),
                nonEmptyCb = new PassFailCallback<Void>(),
                failureCb = new PassFailCallback<Void>(),
                fail404Cb = new PassFailCallback<Void>();

        // Success
        httpClient.success = true;
        httpClient.returnObject = null;
        dut.deleteAlarm("EngineSpeed", AlarmType.ABOVE, successCb);
        assertTrue(successCb.success);
        assertNull(successCb.error);
        assertEquals("DELETE alarms/EngineSpeed~above", httpClient.requestSummary);

        // Non-empty body
        httpClient.success = true;
        httpClient.returnObject = new JSONObject();
        dut.deleteAlarm("EngineSpeed", AlarmType.ABOVE, nonEmptyCb);
        assertFalse(nonEmptyCb.success);
        assertNotNull(nonEmptyCb.error);
        assertEquals("Unexpected response body: {}", nonEmptyCb.error.getMessage());

        // Error
        httpClient.success = false;
        httpClient.failWith = new Exception("DeleteAlarm Failure");
        dut.deleteAlarm("EngineSpeed", AlarmType.ABOVE, failureCb);
        assertFalse(failureCb.success);
        assertNotNull(failureCb.error);
        assertEquals(httpClient.failWith, failureCb.error);

        // 404 error - they're handled specially
        httpClient.failWith = new WvaHttpException.WvaHttpNotFound("alarms/EngineSpeed~above", "");
        dut.deleteAlarm("EngineSpeed", AlarmType.ABOVE, fail404Cb);
        assertFalse(fail404Cb.success);
        assertNotNull(fail404Cb.error);
        assertEquals(httpClient.failWith, fail404Cb.error);
    }

    public void testDeleteUriAlarm() throws Exception {
        PassFailCallback<Void> successCb = new PassFailCallback<Void>(),
                nonEmptyCb = new PassFailCallback<Void>(),
                failureCb = new PassFailCallback<Void>(),
                fail404Cb = new PassFailCallback<Void>();

        // Success
        httpClient.success = true;
        httpClient.returnObject = null;
        dut.deleteUriAlarm("vehicle/ignition", AlarmType.CHANGE, successCb);
        assertTrue(successCb.success);
        assertNull(successCb.error);
        assertEquals("DELETE alarms/vehicle~ignition~change", httpClient.requestSummary);

        // Non-empty body
        httpClient.success = true;
        httpClient.returnObject = new JSONObject();
        dut.deleteUriAlarm("vehicle/ignition", AlarmType.CHANGE, nonEmptyCb);
        assertFalse(nonEmptyCb.success);
        assertNotNull(nonEmptyCb.error);
        assertEquals("Unexpected response body: {}", nonEmptyCb.error.getMessage());

        // Error
        httpClient.success = false;
        httpClient.failWith = new Exception("deleteUriAlarm Failure");
        dut.deleteUriAlarm("vehicle/ignition", AlarmType.CHANGE, failureCb);
        assertFalse(failureCb.success);
        assertNotNull(failureCb.error);
        assertEquals(httpClient.failWith, failureCb.error);

        // 404 error - they're handled specially
        httpClient.failWith = new WvaHttpException.WvaHttpNotFound("alarms/vehicle~ignition~change", "");
        dut.deleteUriAlarm("vehicle/ignition", AlarmType.CHANGE, fail404Cb);
        assertFalse(fail404Cb.success);
        assertNotNull(fail404Cb.error);
        assertEquals(httpClient.failWith, fail404Cb.error);

        // Purely for code coverage, since we don't/can't analyze log messages.
        dut.deleteUriAlarm("vehicle/data/foo", AlarmType.CHANGE, null);
    }

    public void testUnsubscribe_old() throws JSONException {

        dut.removeAllListeners();

        httpClient.success = true;
        httpClient.returnObject = null;
        VehicleDataEvent e = (VehicleDataEvent) EventFactory.fromTCP(jFactory.data());

        final boolean[] gotHereList = new boolean[2];
        gotHereList[0] = false; // alarm triggered
        gotHereList[1] = false; // subscription received

        addGotHereListeners(e.getEndpoint(), gotHereList);

        dut.unsubscribe(e.getEndpoint(), null);
        dut.notifyListeners(e);
        assertFalse(gotHereList[0]);
        assertTrue(gotHereList[1]);


        gotHereList[0] = false;
        gotHereList[1] = false;
        dut.unsubscribe(e.getEndpoint(), null);
        dut.removeVehicleDataListener(e.getEndpoint());
        dut.notifyListeners(e);
        assertFalse(gotHereList[0]);
        assertFalse("Subscription listener triggered after removal", gotHereList[1]);
    }

    public void testDeleteAlarm_old() throws JSONException {
        dut.removeAllListeners();

        httpClient.success = true;
        VehicleDataEvent e = (VehicleDataEvent) EventFactory.fromTCP(jFactory.alarm());

        final boolean[] gotHereList = {false, false}; // alarm triggered, subscription received

        addGotHereListeners(e.getEndpoint(), gotHereList);

        httpClient.returnObject = null;

        assertFalse(gotHereList[0]);
        assertFalse(gotHereList[1]);

        dut.deleteAlarm(e.getEndpoint(), AlarmType.ABOVE, null);
        // If we call notifyListeners, we should still update the alarm listener
        dut.notifyListeners(e);
        assertTrue("Alarm listener not triggered", gotHereList[0]);
        assertFalse(gotHereList[1]);


        gotHereList[0] = false;
        gotHereList[1] = false;
        dut.deleteAlarm(e.getEndpoint(), AlarmType.ABOVE, null);
        dut.removeVehicleDataListener(e.getEndpoint());
        dut.notifyListeners(e);
        assertFalse("Alarm listener was triggered after removal", gotHereList[0]);
        assertFalse(gotHereList[1]);

    }

    public void testRemoveAllListeners() {
        Set<String> endpoints = dut.getCachedVehicleDataEndpoints();
        int numEndpoints = endpoints.size();
        boolean[][] gotHereLists;
        gotHereLists = new boolean[numEndpoints][2]; //booleans initialize to false

        int count = 0;
        for (String endpoint : endpoints) {
            addGotHereListeners(endpoint, gotHereLists[count++]);
        }

        dut.removeAllListeners();

        for (boolean[] list : gotHereLists) {
            assertFalse(list[0]);
            assertFalse(list[1]);
        }
    }

    /**
     * takes an endpoint (should be a part of dut), and adds a
     * subscription and an alarm listener. These listeners switch their
     * respective gotHereList index to `true' when executed.
     * @param endpoint
     * @param gotHereList
     */
    private void addGotHereListeners(final String endpoint, final boolean[] gotHereList) {
        VehicleDataListener listener = new VehicleDataListener() {
            @Override
            public void onEvent(VehicleDataEvent e) {
                switch (e.getType()) {
                    case ALARM:
                        gotHereList[0] = !gotHereList[0];
                        break;
                    case SUBSCRIPTION:
                        gotHereList[1] = !gotHereList[1];
                        break;
                }
            }
        };

        dut.setVehicleDataListener(endpoint, listener);
    }

    public void testListenerPropagation() {
        VehicleDataListener allListener = mock(VehicleDataListener.class);
        final VehicleDataListener specificListener = mock(VehicleDataListener.class);

        // Ensure the callbacks are not pushed off onto the UI thread, so that we don't
        // need to await their calls in order to test.
        doReturn(false).when(allListener).runsOnUiThread();
        doReturn(false).when(specificListener).runsOnUiThread();

        dut.setVehicleDataListener(allListener);
        dut.setVehicleDataListener("EngineSpeed", specificListener);

        final VehicleDataEvent e1 = new VehicleDataEvent(
                EventFactory.Type.SUBSCRIPTION, "vehicle/data/NotEngineSpeed", "NotEngineSpeed",
                DateTime.now().toDateTime(DateTimeZone.UTC), "NotEngineSpeed~sub", null);
        final VehicleDataEvent e2 = new VehicleDataEvent(
                EventFactory.Type.SUBSCRIPTION, "vehicle/data/EngineSpeed", "EngineSpeed",
                DateTime.now().toDateTime(DateTimeZone.UTC), "EngineSpeed~sub", null);

        dut.notifyListeners(e1);

        // e1 is not for EngineSpeed, so we shouldn't trigger the EngineSpeed listener.
        // But we should trigger the catch-all listener.
        verify(allListener).onEvent(e1);
        verify(specificListener, never()).onEvent(any(VehicleDataEvent.class));

        reset(allListener);

        // Set up to test call order.
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                // Specific listener must be called first.
                verify(specificListener).onEvent(e2);
                return null;
            }
        }).when(allListener).onEvent(e2);

        dut.notifyListeners(e2);

        verify(specificListener).onEvent(e2);
        verify(allListener).onEvent(e2);
    }

    public void testListenerPropagation_with_uri() {
        // Test handling of URI listeners.
        VehicleDataListener allListener = mock(VehicleDataListener.class);
        final VehicleDataListener specificListener = mock(VehicleDataListener.class);

        // Ensure the callbacks are not pushed off onto the UI thread, so that we don't
        // need to await their calls in order to test.
        doReturn(false).when(allListener).runsOnUiThread();
        doReturn(false).when(specificListener).runsOnUiThread();

        dut.setVehicleDataListener(allListener);
        dut.setUriListener("vehicle/ignition", specificListener);

        final VehicleDataEvent e1 = new VehicleDataEvent(
                EventFactory.Type.SUBSCRIPTION, "something/else/entirely", "entirely",
                DateTime.now().toDateTime(DateTimeZone.UTC), "something~else~entirely~sub", null);
        final VehicleDataEvent e2 = new VehicleDataEvent(
                EventFactory.Type.SUBSCRIPTION, "vehicle/ignition", "ignition",
                DateTime.now().toDateTime(DateTimeZone.UTC), "vehicle~ignition~sub", null);

        dut.notifyListeners(e1);

        // e1 is not for ignition, so we shouldn't trigger the ignition listener.
        // But we should trigger the catch-all listener.
        verify(allListener).onEvent(e1);
        verify(specificListener, never()).onEvent(any(VehicleDataEvent.class));

        reset(allListener);

        // Set up to test call order.
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                // Specific listener must be called first.
                verify(specificListener).onEvent(e2);
                return null;
            }
        }).when(allListener).onEvent(e2);

        dut.notifyListeners(e2);

        verify(specificListener).onEvent(e2);
        verify(allListener).onEvent(e2);

        reset(specificListener);
        reset(allListener);
        // And if we remove the URI-specific listener, we still call the catch-all.
        dut.removeUriListener("vehicle/ignition");

        dut.notifyListeners(e1);
        dut.notifyListeners(e2);

        verify(specificListener, never()).onEvent(any(VehicleDataEvent.class));
        verify(allListener).onEvent(e1);
        verify(allListener).onEvent(e2);
    }

    public void testUpdateCached_Null() {
        VehicleData data = mock(VehicleData.class);
        doCallRealMethod().when(data).updateCachedVehicleData(any(VehicleDataEvent.class));

        data.updateCachedVehicleData(null);
        verify(data, never()).notifyListeners(any(VehicleDataEvent.class));
    }
}
