/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import com.digi.wva.async.EventFactory;
import com.digi.wva.async.FaultCodeCommon;
import com.digi.wva.async.FaultCodeEvent;
import com.digi.wva.async.FaultCodeListener;
import com.digi.wva.async.FaultCodeResponse;
import com.digi.wva.exc.NotListeningToECUException;
import com.digi.wva.exc.WvaHttpException;
import com.digi.wva.test_auxiliary.HttpClientSpoofer;
import com.digi.wva.test_auxiliary.JsonFactory;
import com.digi.wva.test_auxiliary.PassFailCallback;

import junit.framework.TestCase;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class FaultCodesTest extends TestCase {
    HttpClientSpoofer httpClient = new HttpClientSpoofer("hostname");
    JsonFactory jFactory = new JsonFactory();
    FaultCodes fc;

    protected void setUp() throws Exception {
        httpClient.returnObject = jFactory.faultCodeCAN0EcuList();
        httpClient.requestSummary = null;
        httpClient.success = true;

        fc = new FaultCodes(httpClient);
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testEnumsToString() {
        assertEquals("can0", FaultCodeCommon.Bus.CAN0.toString());
        assertEquals("can1", FaultCodeCommon.Bus.CAN1.toString());
        assertEquals("active", FaultCodeCommon.FaultCodeType.ACTIVE.toString());
        assertEquals("inactive", FaultCodeCommon.FaultCodeType.INACTIVE.toString());
    }

    public void testConstructor() {
        assertNotNull(fc);
    }

    public void testFetchEcuNames() throws JSONException {
        PassFailCallback<Set<String>> cb = new PassFailCallback<Set<String>>();

        // First, test success.
        httpClient.returnObject = jFactory.faultCodeCAN0EcuList();
        httpClient.success = true;
        fc.fetchEcuNames(FaultCodeCommon.Bus.CAN0, cb);
        assertTrue(cb.success);
        assertNotNull(cb.response);
        assertEquals("GET vehicle/dtc/can0_active", httpClient.requestSummary);
        assertTrue(cb.response.contains("ecu0"));
        assertTrue(cb.response.contains("ecu1"));

        // Test failure (error)
        httpClient.returnObject = jFactory.junk();
        httpClient.success = false;
        httpClient.requestSummary = null;
        fc.fetchEcuNames(FaultCodeCommon.Bus.CAN0, cb);
        assertFalse(cb.success);
        assertNull(cb.response);
        assertEquals("GET vehicle/dtc/can0_active", httpClient.requestSummary);

        // Test with junk data (no 'can0_active' key)
        httpClient.success = true;
        httpClient.requestSummary = null;
        httpClient.returnObject = new JSONObject();
        fc.fetchEcuNames(FaultCodeCommon.Bus.CAN0, cb);
        assertFalse(cb.success);
        assertNull(cb.response);
        assertEquals("GET vehicle/dtc/can0_active", httpClient.requestSummary);

        // Test with non-string and invalid URL values
        httpClient.returnObject.put("can0_active", new JSONArray(
                Arrays.asList(null, new JSONArray(), "foo")));
        fc.fetchEcuNames(FaultCodeCommon.Bus.CAN0, cb);
        // Treated the same as if it were an empty list
        assertTrue(cb.success);
        assertTrue("Returned set is not empty!", cb.response.isEmpty());
    }

    public void testFetchFaultCode() {
        PassFailCallback<FaultCodeResponse> cb = new PassFailCallback<FaultCodeResponse>();

        // First, test success.
        httpClient.returnObject = jFactory.faultCodeValueObj("ecu0");
        httpClient.success = true;
        fc.fetchFaultCode(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", cb);
        assertTrue(cb.success);
        assertNotNull(cb.response);
        assertEquals("GET vehicle/dtc/can0_active/ecu0", httpClient.requestSummary);
        assertEquals("00ff00000000ffff", cb.response.getValue());
        assertEquals(ISODateTimeFormat.dateTimeNoMillis().parseDateTime("2007-03-01T13:00:00Z"),
                     cb.response.getTime());

        // Test failure (some exception)
        httpClient.returnObject = jFactory.junk();
        httpClient.success = false;
        httpClient.requestSummary = null;
        fc.fetchFaultCode(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", cb);
        assertFalse(cb.success);
        assertNull(cb.response);
        assertEquals("GET vehicle/dtc/can0_active/ecu0", httpClient.requestSummary);

        // Test with junk data (no 'data' key)
        httpClient.success = true;
        httpClient.requestSummary = null;
        fc.fetchFaultCode(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", cb);
        assertFalse(cb.success);
        assertNull(cb.response);
        assertEquals("GET vehicle/dtc/can0_active/ecu0", httpClient.requestSummary);

        // Test 404 error
        httpClient.success = false;
        httpClient.failWith = new WvaHttpException.WvaHttpNotFound("vehicle/dtc/can0_active/ecu0", "");
        fc.fetchFaultCode(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", cb);
        assertFalse(cb.success);
        assertNull(cb.response);
        assertEquals(NotListeningToECUException.class, cb.error.getClass());

        // Test 503 error
        httpClient.failWith = new WvaHttpException.WvaHttpServiceUnavailable("vehicle/dtc/can0_active/ecu0", "");
        fc.fetchFaultCode(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", cb);
        // 503 error means the referenced ECU is expected to be valid, but there have been no messages yet.
        assertTrue(cb.success);
        assertNull(cb.error);
    }

    public void testSubscribe() throws Exception {
        PassFailCallback<Void> cb = new PassFailCallback<Void>();

        // First, test success
        httpClient.success = true;
        httpClient.returnObject = null;
        fc.subscribe(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", 10, cb);
        assertTrue(cb.success);
        assertNull(cb.response);
        assertEquals("PUT subscriptions/can0_active~ecu0~dtcsub", httpClient.requestSummary);

        // Test failure (error)
        httpClient.returnObject = jFactory.junk();
        httpClient.success = false;
        httpClient.requestSummary = null;
        fc.subscribe(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", 10, cb);
        assertFalse(cb.success);
        assertNull(cb.response);
        assertEquals("PUT subscriptions/can0_active~ecu0~dtcsub", httpClient.requestSummary);

        // Test non-empty body (edge case)
        httpClient.returnObject = new JSONObject();
        httpClient.success = true;
        httpClient.requestSummary = null;
        fc.subscribe(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", 10, cb);
        assertFalse(cb.success);
        assertNull(cb.response);
        assertEquals("PUT subscriptions/can0_active~ecu0~dtcsub", httpClient.requestSummary);
        assertEquals("Unexpected response body: {}", cb.error.getMessage());
    }


    public void testUnsubscribe() throws Exception {
        PassFailCallback<Void> cb = new PassFailCallback<Void>();

        // First, test success
        httpClient.success = true;
        httpClient.returnObject = null;
        fc.unsubscribe(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", cb);

        assertTrue(cb.success);
        assertNull(cb.response);
        assertEquals("DELETE subscriptions/can0_active~ecu0~dtcsub", httpClient.requestSummary);

        // Test failure (error)
        httpClient.returnObject = jFactory.junk();
        httpClient.success = false;
        httpClient.requestSummary = null;
        fc.unsubscribe(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", cb);
        assertFalse(cb.success);
        assertNull(cb.response);
        assertEquals("DELETE subscriptions/can0_active~ecu0~dtcsub", httpClient.requestSummary);

        // Test non-empty body (edge case)
        httpClient.returnObject = new JSONObject();
        httpClient.success = true;
        httpClient.requestSummary = null;
        fc.unsubscribe(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", cb);
        assertFalse(cb.success);
        assertNull(cb.response);
        assertEquals("DELETE subscriptions/can0_active~ecu0~dtcsub", httpClient.requestSummary);
        assertEquals("Unexpected response body: {}", cb.error.getMessage());
    }

    public void testCreateAlarm() throws Exception {
        PassFailCallback<Void> cb = new PassFailCallback<Void>();

        // First, test success
        httpClient.success = true;
        httpClient.returnObject = null;
        fc.createAlarm(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", 10, cb);

        assertTrue(cb.success);
        assertNull(cb.response);
        assertEquals("PUT alarms/can0_active~ecu0~change", httpClient.requestSummary);

        // Test failure (error)
        httpClient.returnObject = jFactory.junk();
        httpClient.success = false;
        httpClient.requestSummary = null;
        fc.createAlarm(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", 10, cb);
        assertFalse(cb.success);
        assertNull(cb.response);
        assertEquals("PUT alarms/can0_active~ecu0~change", httpClient.requestSummary);

        // Test non-empty body (edge case)
        httpClient.returnObject = new JSONObject();
        httpClient.success = true;
        httpClient.requestSummary = null;
        fc.createAlarm(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", 10, cb);
        assertFalse(cb.success);
        assertNull(cb.response);
        assertEquals("PUT alarms/can0_active~ecu0~change", httpClient.requestSummary);
        assertEquals("Unexpected response body: {}", cb.error.getMessage());
    }


    public void testDeleteAlarm() throws Exception {
        PassFailCallback<Void> cb = new PassFailCallback<Void>();

        // First, test success
        httpClient.success = true;
        httpClient.returnObject = null;

        fc.deleteAlarm(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", cb);
        assertTrue(cb.success);
        assertNull(cb.response);
        assertEquals("DELETE alarms/can0_active~ecu0~change", httpClient.requestSummary);

        // Test failure (error)
        httpClient.returnObject = jFactory.junk();
        httpClient.success = false;
        httpClient.requestSummary = null;
        fc.deleteAlarm(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", cb);
        assertFalse(cb.success);
        assertNull(cb.response);
        assertEquals("DELETE alarms/can0_active~ecu0~change", httpClient.requestSummary);

        // Test non-empty body (edge case)
        httpClient.returnObject = new JSONObject();
        httpClient.success = true;
        httpClient.requestSummary = null;
        fc.deleteAlarm(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", cb);
        assertFalse(cb.success);
        assertNull(cb.response);
        assertEquals("DELETE alarms/can0_active~ecu0~change", httpClient.requestSummary);
        assertEquals("Unexpected response body: {}", cb.error.getMessage());
    }

    public void testListenerPropagation_Both() {
        FaultCodeListener allListener = mock(FaultCodeListener.class);
        final FaultCodeListener specificListener = mock(FaultCodeListener.class);

        // Ensure the callbacks are not pushed off onto the UI thread, so that we don't
        // need to await their calls in order to test.
        doReturn(false).when(allListener).runsOnUiThread();
        doReturn(false).when(specificListener).runsOnUiThread();

        fc.setFaultCodeListener(allListener);
        fc.setFaultCodeListener(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", specificListener);

        final FaultCodeEvent event = new FaultCodeEvent(
                EventFactory.Type.SUBSCRIPTION, "vehicle/dtc/can0_active/ecu0", "ecu0",
                DateTime.now().toDateTime(DateTimeZone.UTC), "can1_inactive~ecu0~dtcsub", null);

        // Set up to test call order.
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                // Specific listener must be called first.
                verify(specificListener).onEvent(event);
                return null;
            }
        }).when(allListener).onEvent(event);

        // Send the event through
        fc.notifyListeners(event);

        // We should have triggered both listeners.
        verify(allListener).onEvent(event);
        verify(specificListener).onEvent(event);
    }

    public void testListenerPropagation_OnlyCatchAll() {
        FaultCodeListener allListener = mock(FaultCodeListener.class);
        final FaultCodeListener specificListener = mock(FaultCodeListener.class);

        // Ensure the callbacks are not pushed off onto the UI thread, so that we don't
        // need to await their calls in order to test.
        doReturn(false).when(allListener).runsOnUiThread();
        doReturn(false).when(specificListener).runsOnUiThread();

        fc.setFaultCodeListener(allListener);
        fc.setFaultCodeListener(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu1", specificListener);

        // Event should only reach the catch-all listener
        final FaultCodeEvent event = new FaultCodeEvent(
                EventFactory.Type.SUBSCRIPTION, "vehicle/dtc/can0_active/ecu0", "ecu0",
                DateTime.now().toDateTime(DateTimeZone.UTC), "can0_inactive~ecu0~dtcsub", null);

        fc.notifyListeners(event);

        // event is not for ecu1, so we shouldn't trigger the ecu1 listener.
        // But we should trigger the catch-all listener.
        verify(allListener).onEvent(event);
        verify(specificListener, never()).onEvent(any(FaultCodeEvent.class));

        // Now remove the specific listener, and verify it doesn't get called
        fc.removeFaultCodeListener(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu1");

        FaultCodeEvent event2 = new FaultCodeEvent(
                EventFactory.Type.SUBSCRIPTION, "vehicle/dtc/can0_active/ecu1", "ecu1",
                DateTime.now(DateTimeZone.UTC), "can0_active~ecu1~dtcsub", null);

        reset(allListener, specificListener);

        fc.notifyListeners(event2);

        // event2 is for ecu1, but we removed the specific listener.
        verify(specificListener, never()).onEvent(any(FaultCodeEvent.class));
        verify(allListener).onEvent(event2);
    }

    public void testListenerPropagation_OnlySpecific() {
        final FaultCodeListener specificListener = mock(FaultCodeListener.class);
        final FaultCodeListener catchAll = mock(FaultCodeListener.class);

        // Ensure the callbacks are not pushed off onto the UI thread, so that we don't
        // need to await their calls in order to test.
        doReturn(false).when(specificListener).runsOnUiThread();
        doReturn(false).when(catchAll).runsOnUiThread();

        fc.setFaultCodeListener(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", specificListener);
        fc.setFaultCodeListener(catchAll);

        // Event should reach the specific listener
        final FaultCodeEvent event = new FaultCodeEvent(
                EventFactory.Type.SUBSCRIPTION, "vehicle/dtc/can0_active/ecu0", "ecu0",
                DateTime.now().toDateTime(DateTimeZone.UTC), "can1_inactive~ecu0~dtcsub", null);

        fc.notifyListeners(event);

        // event should reach both
        verify(catchAll).onEvent(event);
        verify(specificListener).onEvent(event);

        // Remove the catch-all listener, check that it doesn't get called
        fc.removeFaultCodeListener();
        reset(catchAll, specificListener);

        fc.notifyListeners(event);
        verify(catchAll, never()).onEvent(any(FaultCodeEvent.class));
        verify(specificListener).onEvent(event);
    }

    public void testRemoveAllListeners() {
        FaultCodeListener allListener = mock(FaultCodeListener.class);
        final FaultCodeListener specificListener = mock(FaultCodeListener.class);

        // Ensure the callbacks are not pushed off onto the UI thread, so that we don't
        // need to await their calls in order to test.
        doReturn(false).when(allListener).runsOnUiThread();
        doReturn(false).when(specificListener).runsOnUiThread();

        // Set up the listeners
        fc.setFaultCodeListener(allListener);
        fc.setFaultCodeListener(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0", specificListener);

        // Event should reach both listeners
        final FaultCodeEvent event = new FaultCodeEvent(
                EventFactory.Type.SUBSCRIPTION, "vehicle/dtc/can0_active/ecu0", "ecu0",
                DateTime.now().toDateTime(DateTimeZone.UTC), "can1_inactive~ecu0~dtcsub", null);

        fc.notifyListeners(event);
        verify(allListener).onEvent(event);
        verify(specificListener).onEvent(event);

        reset(allListener, specificListener);

        fc.removeAllListeners();

        fc.notifyListeners(event);
        verify(allListener, never()).onEvent(event);
        verify(specificListener, never()).onEvent(event);
    }

    public void testCachedDataAndListener() throws Exception {
        // Verify that if updateCachedFaultCode is called, then the listener is called
        // and the cached value is updated.
        assertNull(fc.getCachedFaultCode(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0"));

        FaultCodeListener all = mock(FaultCodeListener.class);
        doReturn(false).when(all).runsOnUiThread();

        fc.setFaultCodeListener(all);

        JSONObject dataObj = new JSONObject();
        dataObj.put("value", "0");
        dataObj.put("timestamp", DateTime.now(DateTimeZone.UTC).toString(ISODateTimeFormat.dateTimeNoMillis()));

        FaultCodeEvent evt = new FaultCodeEvent(
                EventFactory.Type.SUBSCRIPTION, "vehicle/dtc/can0_active/ecu0", "ecu0",
                DateTime.now(DateTimeZone.UTC), "can0_active~ecu0~dtcsub",
                new FaultCodeResponse(dataObj));

        fc.updateCachedFaultCode(evt);

        verify(all).onEvent(evt);
        assertEquals(evt.getResponse(),
                     fc.getCachedFaultCode(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0"));
    }

    public void testUpdateCached_Null() {
        FaultCodes fc = mock(FaultCodes.class);
        doCallRealMethod().when(fc).updateCachedFaultCode(any(FaultCodeEvent.class));

        fc.updateCachedFaultCode(null);
        verify(fc, never()).notifyListeners(any(FaultCodeEvent.class));
    }

    // TODO: Test listener related code (updateCached, etc.) and cached data related
    // code (getCached, etc.)
}
