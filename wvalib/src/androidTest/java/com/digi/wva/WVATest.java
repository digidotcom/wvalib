/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva;

import android.util.Pair;

import com.digi.wva.async.AlarmType;
import com.digi.wva.async.FaultCodeCommon;
import com.digi.wva.async.FaultCodeListener;
import com.digi.wva.async.FaultCodeResponse;
import com.digi.wva.async.VehicleDataListener;
import com.digi.wva.async.VehicleDataResponse;
import com.digi.wva.async.WvaCallback;
import com.digi.wva.exc.WvaHttpException;
import com.digi.wva.internal.Ecus;
import com.digi.wva.internal.FaultCodes;
import com.digi.wva.internal.Hardware;
import com.digi.wva.internal.HttpClient;
import com.digi.wva.internal.VehicleData;
import com.digi.wva.test_auxiliary.HttpClientSpoofer;
import com.digi.wva.test_auxiliary.JsonFactory;
import com.digi.wva.test_auxiliary.PassFailCallback;

import junit.framework.TestCase;

import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class WVATest extends TestCase {
    private static String hostname = "hostname";
    private static int port = 65534;

    // Mocks
    HttpClientSpoofer httpClient = new HttpClientSpoofer(hostname);
    VehicleData mVeh = mock(VehicleData.class);
    Hardware mHw = mock(Hardware.class);
    Ecus mEcus = mock(Ecus.class);
    FaultCodes mFc = mock(FaultCodes.class);
    WvaCallback<Set<String>> mCbSet = mock(WvaCallback.class);
    VehicleDataListener mListener = mock(VehicleDataListener.class);
    FaultCodeListener mFListener = mock(FaultCodeListener.class);

    // Spies
    VehicleData vehSpy = null;

    // Auxiliary objects
    JsonFactory jsonFactory = new JsonFactory();

    WVA normalWva; // normal constructor
    WVA mockedWVA; // factory method, mocked internals
    WVA mockedWvaVehicleSpy; // factory method, vehSpy internals

    protected void setUp() throws Exception {
        normalWva = new WVA(hostname);
        mockedWVA = WVA.getDevice(hostname, httpClient, mVeh, mEcus, mHw, mFc);

        VehicleData mVeh2 = new VehicleData(httpClient);
        vehSpy = spy(mVeh2);
        mockedWvaVehicleSpy = WVA.getDevice(hostname, httpClient, vehSpy, mEcus, mHw, mFc);

        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testDevice() throws Exception{
        assertNotNull(normalWva);
        assertNotNull(mockedWVA);
    }

    public void testBasicAuthMethods() {
        HttpClient mockHttp = mock(HttpClient.class);
        WVA mock = WVA.getDevice(hostname, mockHttp, vehSpy, mEcus, mHw, mFc);

        mock.useBasicAuth("foo", "bar");
        verify(mockHttp).useBasicAuth("foo", "bar");

        mock.clearBasicAuth();
        verify(mockHttp).clearBasicAuth();
    }

    public void testHttpPortMethods() {
        HttpClient mockHttp = mock(HttpClient.class);
        WVA mock = WVA.getDevice(hostname, mockHttp, vehSpy, mEcus, mHw, mFc);

        mock.useSecureHttp(true);
        verify(mockHttp).useSecureHttp(true);
        mock.useSecureHttp(false);
        verify(mockHttp).useSecureHttp(false);

        mock.setHttpPort(111);
        verify(mockHttp).setHttpPort(111);
        mock.setHttpsPort(222);
        verify(mockHttp).setHttpsPort(222);
    }

    public void testFetchLedNames() throws Exception {
        mockedWVA.fetchLedNames(mCbSet);
        verify(mHw).fetchLedNames(any(WvaCallback.class));
    }

    public void testGetCachedLedNames() {
        Set<String> response = new HashSet<String>();
        when(mHw.getCachedLedNames()).thenReturn(response);
        assertEquals(response, mockedWVA.getCachedLedNames());
    }

    public void testFetchButtonNames() throws Exception {
        mockedWVA.fetchButtonNames(mCbSet);
        verify(mHw).fetchButtonNames(any(WvaCallback.class));
    }

    public void testGetCachedButtonNames() {
        Set<String> response = new HashSet<String>();
        when(mHw.getCachedButtonNames()).thenReturn(response);
        assertEquals(response, mockedWVA.getCachedButtonNames());
    }

    public void testFetchEcus() throws Exception {
        mockedWVA.fetchEcus(mCbSet);
        verify(mEcus).fetchEcus(any(WvaCallback.class));
    }

    public void testGetCachedEcus() {
        Set<String> response = new HashSet<String>();
        when(mEcus.getCachedEcus()).thenReturn(response);
        assertEquals(response, mockedWVA.getCachedEcus());
    }

    public void testIsWVA() throws Exception {
        PassFailCallback<Boolean> cb = new PassFailCallback<Boolean>() {
            @Override
            public boolean runsOnUiThread() {
                return false;
            }
        };

        // Success
        JSONObject resp = new JSONObject();
        JSONArray services = new JSONArray();
        String[] servicesList = new String[]{
                "vehicle", "hw", "config", "state", "files", "alarms", "subscriptions", "password"};
        for (String s : servicesList) {
            services.put(s);
        }
        resp.put("ws", services);
        httpClient.success = true;
        httpClient.returnObject = resp;

        mockedWVA.isWVA(cb);
        assertTrue(cb.success);
        assertNull(cb.error);
        assertTrue(cb.response);
        assertEquals("GET ", httpClient.requestSummary);

        // If we send back less values than expected, the callback response should be false
        resp = new JSONObject();
        services = new JSONArray();
        servicesList = new String[]{"vehicle", "hw", "config"};
        for (String s : servicesList) {
            services.put(s);
        }
        resp.put("ws", services);
        httpClient.returnObject = resp;
        mockedWVA.isWVA(cb);
        assertTrue(cb.success);
        assertFalse(cb.response);
        assertNull(cb.error);

        // If the list does not contain all the expected values, the callback values should be null,false
        services = new JSONArray(Arrays.asList("vehicle", "subscriptions", "alarms", "config", "files"));
        resp.put("ws", services);
        httpClient.returnObject = resp;
        mockedWVA.isWVA(cb);
        assertTrue(cb.success);
        assertFalse(cb.response);
        assertNull(cb.error);

        // If the 'ws' key does not map to a JSON array, we should get that exception
        resp.put("ws", new JSONObject());
        mockedWVA.isWVA(cb);
        assertFalse(cb.success);
        assertEquals(JSONException.class.getCanonicalName(), cb.error.getClass().getCanonicalName());
    }

    /**
     * Test the {@link WVA#configure(String, JSONObject, WvaCallback)} method, to verify that the
     * behavior documented in its Javadoc (wrapping the JSON object, etc.) is in fact the method's
     * behavior.
     */
    public void testConfigure() throws JSONException {
        PassFailCallback<Void> cb = new PassFailCallback<Void>() {
            @Override
            public boolean runsOnUiThread() {
                return false;
            }
        };

        JSONObject data = new JSONObject();
        data.put("enable", "off");
        data.put("port", 9999);

        // Success
        httpClient.success = true;
        httpClient.returnObject = null;
        mockedWVA.configure("https", data, cb);
        assertTrue(cb.success);
        assertNull(cb.response);
        assertNull(cb.error);
        assertEquals("PUT config/https", httpClient.requestSummary);
        assertTrue("Body has no https key", httpClient.requestBody.has("https"));
        assertEquals(data, httpClient.requestBody.getJSONObject("https"));

        // Non-empty body
        httpClient.success = true;
        httpClient.returnObject = new JSONObject();
        mockedWVA.configure("https", data, cb);
        assertFalse(cb.success);
        assertNotNull(cb.error);
        assertEquals("Unexpected response body: {}", cb.error.getMessage());

        // Error
        httpClient.success = false;
        httpClient.failWith = new WvaHttpException.WvaHttpNotFound("/ws/config/https", null);
        mockedWVA.configure("https", data, cb);
        assertFalse(cb.success);
        assertNotNull(cb.error);
        assertEquals(httpClient.failWith, cb.error);
    }

    public void testGetConfiguration() throws JSONException {
        PassFailCallback<JSONObject> cb = new PassFailCallback<JSONObject>() {
            @Override
            public boolean runsOnUiThread() {
                return false;
            }
        };

        JSONObject o = new JSONObject();
        JSONObject inner = new JSONObject();
        inner.put("enable", "on");
        inner.put("port", 80);
        o.put("http", inner);

        // Success
        httpClient.success = true;
        httpClient.returnObject = o;
        mockedWVA.getConfiguration("http", cb);
        assertTrue(cb.success);
        assertNull(cb.error);
        assertEquals(inner, cb.response);
        assertEquals("GET config/http", httpClient.requestSummary);

        // No http key
        httpClient.returnObject = new JSONObject();
        mockedWVA.getConfiguration("http", cb);
        assertFalse(cb.success);
        assertNotNull(cb.error);
        assertEquals("Configuration response is missing the 'http' key.", cb.error.getMessage());

        // 'http' key does not map to a JSON object
        httpClient.returnObject = new JSONObject().put("http", new JSONArray());
        mockedWVA.getConfiguration("http", cb);
        assertFalse(cb.success);
        assertNotNull(cb.error);
        assertEquals("Value [] at http of type org.json.JSONArray cannot be converted to JSONObject", cb.error.getMessage());

        // Error
        httpClient.success = false;
        httpClient.failWith = new WvaHttpException.WvaHttpNotFound("/ws/config/http", null);
        mockedWVA.getConfiguration("http", cb);
        assertFalse(cb.success);
        assertNotNull(cb.error);
        assertEquals(httpClient.failWith, cb.error);
    }

    public void testGetConfiguration_Null() {
        try {
            mockedWVA.getConfiguration("path", null);
            fail("getConfiguration should throw on null callback");
        } catch (NullPointerException e) {
            assertEquals("getConfiguration callback must not be null!", e.getMessage());
        }
    }

    public void testFetchVehicleDataEndpoints() throws Exception {
        mockedWVA.fetchVehicleDataEndpoints(mCbSet);
        verify(mVeh).fetchVehicleDataEndpoints(any(WvaCallback.class));
    }

    public void testSubscribeToVehicleData() throws Exception {
        mockedWVA.subscribeToVehicleData("EngineSpeed", 10, null);
        mockedWVA.subscribeToVehicleData("EngineSpeed", 10);

        verify(mVeh, times(2)).subscribe("EngineSpeed", 10, null);
    }

    public void testUnsubscribeFromVehicleData() throws Exception {
        mockedWVA.unsubscribeFromVehicleData("EngineSpeed", null);
        mockedWVA.unsubscribeFromVehicleData("EngineSpeed");

        verify(mVeh, times(2)).unsubscribe("EngineSpeed", null);
    }

    public void testCreateVehicleDataAlarm() throws Exception {
        mockedWVA.createVehicleDataAlarm("DriverIncome", AlarmType.ABOVE, 20, 10, null);
        mockedWVA.createVehicleDataAlarm("DriverIncome", AlarmType.ABOVE, 20, 10);

        verify(mVeh, times(2)).createAlarm("DriverIncome", AlarmType.ABOVE, 20, 10, null);
    }

    public void testDeleteVehicleDataAlarm() throws Exception {
        mockedWVA.deleteVehicleDataAlarm("DriverIncome", AlarmType.ABOVE, null);
        mockedWVA.deleteVehicleDataAlarm("DriverIncome", AlarmType.ABOVE);

        verify(mVeh, times(2)).deleteAlarm("DriverIncome", AlarmType.ABOVE, null);
    }

    public void testGetCachedVehicleData() throws Exception {
        VehicleDataResponse lastResp = new VehicleDataResponse(jsonFactory.valTimeObj());

        // If cached value is found, getCachedVehicleData returns it
        when(vehSpy.getCachedVehicleData("PassengerEuphoria")).thenReturn(lastResp);
        VehicleDataResponse newResp = mockedWvaVehicleSpy.getCachedVehicleData("PassengerEuphoria");
        assertEquals(lastResp, newResp);

        // If the cache is initialized but the endpoint is unrecognized (null is
        // returned), getCachedVehicleData also returns null
        when(vehSpy.getCachedVehicleData(anyString())).thenReturn(null);
        VehicleDataResponse badResp = mockedWvaVehicleSpy.getCachedVehicleData("not in endpoint set");
        assertNull(badResp);
    }

    public void testGetCachedVehicleDataEndpoints() {
        Set<String> response = new HashSet<String>();

        when(vehSpy.getCachedVehicleDataEndpoints()).thenReturn(response);
        assertEquals(response, mockedWvaVehicleSpy.getCachedVehicleDataEndpoints());
    }

    public void testFetchVehicleData() throws Exception {
        WvaCallback<VehicleDataResponse> cbVR = mock(WvaCallback.class);
        mockedWVA.fetchVehicleData("Endpoint", cbVR);
        verify(mVeh).fetchVehicleData("Endpoint", cbVR);
    }

    public void testSetVehicleDataListener() {
        mockedWVA.setVehicleDataListener("EngineSpeed", mListener);
        verify(mVeh).setVehicleDataListener("EngineSpeed", mListener);

        mockedWVA.setVehicleDataListener(mListener);
        verify(mVeh).setVehicleDataListener(mListener);

        mockedWVA.removeVehicleDataListener();
        verify(mVeh).removeVehicleDataListener();

        try {
            mockedWVA.setVehicleDataListener("EngineSpeed", null);
            fail("setVehicleDataListener with null listener should throw");
        } catch (NullPointerException e) {
            // The exception should point the user to removeVehicleDataListener
            assertTrue("No mention of removeVehicleDataListener in '" + e.getMessage() + "'",
                       e.getMessage().contains("removeVehicleDataListener"));

            verify(mVeh, never()).setVehicleDataListener("EngineSpeed", null);
        }

        mockedWVA.removeVehicleDataListener("EngineSpeed");
        verify(mVeh).removeVehicleDataListener("EngineSpeed");
    }

    public void testRemoveAllVehicleDataListeners() {
             mockedWVA.removeAllVehicleDataListeners();
             verify(mVeh).removeAllListeners();
    }

    public void testSetFaultCodeListener() {
        mockedWVA.setFaultCodeListener(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", mFListener);
        verify(mFc).setFaultCodeListener(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", mFListener);

        mockedWVA.setFaultCodeListener(mFListener);
        verify(mFc).setFaultCodeListener(mFListener);

        mockedWVA.removeFaultCodeListener();
        verify(mFc).removeFaultCodeListener();

        try {
            mockedWVA.setFaultCodeListener(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", null);
            fail("setFaultCodeListener with null listener should throw");
        } catch (NullPointerException e) {
            // The exception should point the user to removeFaultCodeListener
            assertTrue("No mention of removeFaultCodeListener in '" + e.getMessage() + "'",
                    e.getMessage().contains("removeFaultCodeListener"));

            verify(mFc, never()).setFaultCodeListener(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", null);
        }

        mockedWVA.removeFaultCodeListener(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0");
        verify(mFc).removeFaultCodeListener(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0");
    }

    public void testRemoveAllFaultCodeListeners() {
        mockedWVA.removeAllFaultCodeListeners();
        verify(mFc).removeAllListeners();
    }

    public void testFetchFaultCode() {
        mockedWVA.fetchFaultCode(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", null);
        verify(mFc).fetchFaultCode(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", null);
    }

    public void testSubscribeToFaultCodes() throws Exception {
        for (FaultCodeCommon.Bus b : FaultCodeCommon.Bus.values()) {
            for (FaultCodeCommon.FaultCodeType t : FaultCodeCommon.FaultCodeType.values()) {
                mockedWVA.subscribeToFaultCodes(b, t, "ecu0", 10, null);
                verify(mFc).subscribe(b, t, "ecu0", 10, null);
                reset(mFc);
            }
        }
    }

    public void testUnsubscribeFromFaultCodes() {
        mockedWVA.unsubscribeFromFaultCodes(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", null);
        verify(mFc).unsubscribe(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", null);
    }

    public void testCreateFaultCodeAlarm() throws Exception {
        mockedWVA.createFaultCodeAlarm(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", 10, null);
        verify(mFc).createAlarm(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", 10, null);
    }

    public void testDeleteFaultCodeAlarm() {
        mockedWVA.deleteFaultCodeAlarm(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", null);
        verify(mFc).deleteAlarm(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", null);
    }

    public void testGetCachedFaultCode() {
        FaultCodeResponse response = mock(FaultCodeResponse.class);

        when(mFc.getCachedFaultCode(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0")).thenReturn(response);

        assertEquals(response, mockedWVA.getCachedFaultCode(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0"));
    }

    public void testFetchFaultCodeEcuNames() {
        WvaCallback<Set<String>> cb = mock(WvaCallback.class);
        mockedWVA.fetchFaultCodeEcuNames(FaultCodeCommon.Bus.CAN0, cb);
        verify(mFc).fetchEcuNames(FaultCodeCommon.Bus.CAN0, cb);

        try {
            mockedWVA.fetchFaultCodeEcuNames(FaultCodeCommon.Bus.CAN0, null);
            fail("Expected fetchFaultCodeEcuNames to throw on null callback");
        } catch (NullPointerException e) {
            assertEquals("Callback should not be null!", e.getMessage());
        }
    }

    public void testSetTime() throws Exception {
        WvaCallback<DateTime> cbVR = mock(WvaCallback.class);
        DateTime time = new DateTime();
        mockedWVA.setTime(time, cbVR);
        verify(mHw).setTime(time, cbVR);
    }

    public void testFetchTime() throws Exception {
        WvaCallback<DateTime> cbDT = mock(WvaCallback.class);
        mockedWVA.fetchTime(cbDT);
        verify(mHw).fetchTime(cbDT);

        try {
            mockedWVA.fetchTime(null);
            fail("Expected fetchTime to throw on null callback");
        } catch (NullPointerException e) {
            assertEquals("Callback should not be null!", e.getMessage());
        }
    }

    public void testSetLedState() throws Exception {
        WvaCallback<Boolean> cbBool = mock(WvaCallback.class);
        mockedWVA.setLedState("ledName", true, cbBool);
        verify(mHw).setLedState("ledName", true, cbBool);
    }

    public void testFetchLedState() throws Exception {
        WvaCallback<Boolean> cbBool = mock(WvaCallback.class);
        mockedWVA.fetchLedState("ledName", cbBool);
        verify(mHw).fetchLedState("ledName", cbBool);

        try {
            mockedWVA.fetchLedState("foo", null);
            fail("Expected fetchLedState to throw on null callback");
        } catch (NullPointerException e) {
            assertEquals("Callback should not be null!", e.getMessage());
        }
    }

    public void testFetchButtonState() throws Exception {
        WvaCallback<Boolean> cbBool = mock(WvaCallback.class);
        mockedWVA.fetchButtonState("buttonName", cbBool);
        verify(mHw).fetchButtonState("buttonName", cbBool);

        try {
            mockedWVA.fetchButtonState("foo", null);
            fail("Expected fetchButtonState to throw on null callback");
        } catch (NullPointerException e) {
            assertEquals("Callback should not be null!", e.getMessage());
        }
    }

    public void testFetchEcuElements() throws Exception {
        WvaCallback<Set<String>> cbStr = mock(WvaCallback.class);
        mockedWVA.fetchEcuElements("ecuName", cbStr);
        verify(mEcus).fetchEcuElements("ecuName", cbStr);
    }

    public void testGetCachedEcuElements() {
        Set<String> response = new HashSet<String>();
        when(mEcus.getCachedEcuElements("foo")).thenReturn(response);
        assertEquals(response, mockedWVA.getCachedEcuElements("foo"));
    }

    public void testFetchEcuElementValue() {
        WvaCallback<String> cb = mock(WvaCallback.class);
        mockedWVA.fetchEcuElementValue("ecuName", "element", cb);
        verify(mEcus).fetchEcuElementValue("ecuName", "element", cb);
    }

    public void testGetCachedEcuElementValue() {
        String value = "foobarbaz";
        when(mEcus.getCachedEcuElementValue("foo", "bar")).thenReturn(value);
        assertEquals(value, mockedWVA.getCachedEcuElementValue("foo", "bar"));
    }

    public void testFetchAllEcuElementValues() throws Exception {
        WvaCallback<Pair<String, String>> cb = mock(WvaCallback.class);
        mockedWVA.fetchAllEcuElementValues("ecuName", cb);
        verify(mEcus).fetchAllEcuElementValues("ecuName", cb);
    }
}
