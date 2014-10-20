/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import java.util.Set;

import junit.framework.TestCase;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.digi.wva.exc.WvaHttpException;
import com.digi.wva.util.WvaUtil;
import com.digi.wva.test_auxiliary.HttpClientSpoofer;
import com.digi.wva.test_auxiliary.JsonFactory;
import com.digi.wva.test_auxiliary.PassFailCallback;

public class HardwareTest extends TestCase {
    HttpClientSpoofer httpClient = new HttpClientSpoofer("hostname");
    JsonFactory jFactory = new JsonFactory();
    Hardware testHw = new Hardware(httpClient);

    protected void setUp() throws Exception {
        super.setUp();

        httpClient.success = true;
        httpClient.returnObject = jFactory.buttonEndpoints();
        testHw.fetchButtonNames(new PassFailCallback<Set<String>>());

        httpClient.returnObject = jFactory.ledEndpoints();
        testHw.fetchLedNames(new PassFailCallback<Set<String>>());
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testHardware() {
        Hardware constructorTest = new Hardware(httpClient);
        assertNotNull(constructorTest); // this one is a gimme
    }

    public void testFetchLedNames() throws Exception {
        Hardware testInit = new Hardware(httpClient);
        httpClient.returnObject = jFactory.ledEndpoints();
        PassFailCallback<Set<String>> cb1 = new PassFailCallback<Set<String>>();
        PassFailCallback<Set<String>> cb2 = new PassFailCallback<Set<String>>();
        PassFailCallback<Set<String>> cb3 = new PassFailCallback<Set<String>>();
        PassFailCallback<Set<String>> cb4 = new PassFailCallback<Set<String>>();

        // Failure
        httpClient.success = false;
        testInit.fetchLedNames(cb1);
        assertFalse(cb1.success);
        assertEquals(0, testInit.getCachedLedNames().size());

        // Success, but with junk data
        httpClient.success = true;
        httpClient.returnObject = jFactory.junk();
        testInit.fetchLedNames(cb2);
        assertFalse(cb2.success);
        assertEquals(0, testInit.getCachedLedNames().size());

        // Success
        httpClient.success = true;
        httpClient.returnObject = jFactory.ledEndpoints();
        testInit.fetchLedNames(cb3);
        assertTrue(cb3.success);
        assertEquals(httpClient.returnObject.getJSONArray("leds").length(), cb3.response.size());

        // Success, but "leds" does not map to an array
        httpClient.returnObject = new JSONObject().put("leds", new JSONObject());
        testInit.fetchLedNames(cb4);
        assertFalse(cb4.success);
        assertNull(cb4.response);
        assertEquals("Value {} at leds of type org.json.JSONObject cannot be converted to JSONArray", cb4.error.getMessage());
    }

    public void testFetchButtonNames() throws Exception {
        Hardware testInit = new Hardware(httpClient);
        PassFailCallback<Set<String>> cb1 = new PassFailCallback<Set<String>>();
        PassFailCallback<Set<String>> cb2 = new PassFailCallback<Set<String>>();
        PassFailCallback<Set<String>> cb3 = new PassFailCallback<Set<String>>();
        PassFailCallback<Set<String>> cb4 = new PassFailCallback<Set<String>>();

        // Failure
        httpClient.success = false;
        testInit.fetchLedNames(cb1);
        assertFalse(cb1.success);
        assertEquals(0, testInit.getCachedLedNames().size());

        // Success, but with junk data
        httpClient.success = true;
        httpClient.returnObject = jFactory.junk();
        testInit.fetchLedNames(cb2);
        assertFalse(cb2.success);
        assertEquals(0, testInit.getCachedButtonNames().size());

        // Success
        httpClient.success = true;
        httpClient.returnObject = jFactory.buttonEndpoints();
        testInit.fetchButtonNames(cb3);
        assertTrue(cb3.success);
        assertEquals(httpClient.returnObject.getJSONArray("buttons").length(), cb3.response.size());

        // Success, but "buttons" does not map to an array
        httpClient.returnObject = new JSONObject().put("buttons", new JSONObject());
        testInit.fetchButtonNames(cb4);
        assertFalse(cb4.success);
        assertNull(cb4.response);
        assertEquals("Value {} at buttons of type org.json.JSONObject cannot be converted to JSONArray", cb4.error.getMessage());
    }

    public void testGetCachedButtonNames() throws Exception {
        // setUp fetches the names for us. So we must assert that they contain the same names.
        JSONArray buttons = jFactory.buttonEndpoints().getJSONArray("buttons");

        Set<String> cached = testHw.getCachedButtonNames();

        assertEquals(buttons.length(), cached.size());

        for (int i = 0; i < buttons.length(); i++) {
            String name = WvaUtil.getEndpointFromUri(buttons.getString(i));
            assertTrue("Missing button name in cache: " + name, cached.contains(name));
        }
    }

    public void testGetCachedLedNames() throws Exception {
        // setUp fetches the names for us. So we must assert that they contain the same names.
        JSONArray leds = jFactory.ledEndpoints().getJSONArray("leds");

        Set<String> cached = testHw.getCachedLedNames();

        assertEquals(leds.length(), cached.size());

        for (int i = 0; i < leds.length(); i++) {
            String name = WvaUtil.getEndpointFromUri(leds.getString(i));
            assertTrue("Missing LED name in cache: " + name, cached.contains(name));
        }
    }

    public void testFetchButtonState() {
        PassFailCallback<Boolean> cb1 = new PassFailCallback<Boolean>();
        PassFailCallback<Boolean> cb2 = new PassFailCallback<Boolean>();
        PassFailCallback<Boolean> cb3 = new PassFailCallback<Boolean>();

        // Success
        httpClient.returnObject = jFactory.buttonState(true);
        testHw.fetchButtonState("big_red", cb1);
        assertTrue(cb1.success);
        assertTrue(cb1.response);

        httpClient.returnObject = jFactory.buttonState(false);
        testHw.fetchButtonState("big_red", cb1);
        assertTrue(cb1.success);
        assertFalse(cb1.response);

        // Failure
        httpClient.success = false;
        httpClient.failWith = new WvaHttpException.WvaHttpNotFound("hw/buttons/foo", "");
        testHw.fetchButtonState("foo", cb2);
        assertFalse(cb2.success);
        assertNull(cb2.response);
        assertEquals(httpClient.failWith, cb2.error);

        // Success, but with missing keys in body
        httpClient.success = true;
        httpClient.returnObject = new JSONObject();
        testHw.fetchButtonState("foo", cb3);
        assertFalse(cb3.success);
        assertNull(cb3.response);
        assertEquals("No value for button", cb3.error.getMessage());
    }

    public void testFetchLedState() {
        PassFailCallback<Boolean> cb1 = new PassFailCallback<Boolean>();
        PassFailCallback<Boolean> cb2 = new PassFailCallback<Boolean>();
        PassFailCallback<Boolean> cb3 = new PassFailCallback<Boolean>();

        // Success
        httpClient.returnObject = jFactory.ledState(true);
        testHw.fetchLedState("led0", cb1);
        assertTrue(cb1.success);
        assertTrue(cb1.response);

        httpClient.returnObject = jFactory.ledState(false);
        testHw.fetchLedState("led0", cb1);
        assertTrue(cb1.success);
        assertFalse(cb1.response);

        // Failure
        httpClient.success = false;
        httpClient.failWith = new WvaHttpException.WvaHttpNotFound("hw/leds/foo", "");
        testHw.fetchLedState("foo", cb2);
        assertFalse(cb2.success);
        assertNull(cb2.response);
        assertEquals(httpClient.failWith, cb2.error);

        // Success, but with missing keys in body
        httpClient.success = true;
        httpClient.returnObject = jFactory.junk();
        testHw.fetchLedState("foo", cb3);
        assertFalse(cb3.success);
        assertNull(cb3.response);
        assertEquals("No value for led", cb3.error.getMessage());
    }

    public void testSetLedState() throws Exception {
        PassFailCallback<Boolean> cb1 = new PassFailCallback<Boolean>();
        PassFailCallback<Boolean> cb2 = new PassFailCallback<Boolean>();
        PassFailCallback<Boolean> cb3 = new PassFailCallback<Boolean>();

        httpClient.success = true;
        httpClient.returnObject = null;

        testHw.setLedState("led0", true, cb1);
        assertTrue(cb1.success);
        assertEquals("PUT hw/leds/led0", httpClient.requestSummary);
        httpClient.requestSummary = null;

        testHw.setLedState("led0", false, cb2);
        assertTrue(cb2.success);
        assertEquals("PUT hw/leds/led0", httpClient.requestSummary);

        // Body should be empty.
        httpClient.returnObject = new JSONObject();
        testHw.setLedState("led0", true, cb3);
        assertFalse(cb3.success);
        assertEquals("Unexpected response body: {}", cb3.error.getMessage());
    }

    public void testFetchTime() {
        httpClient.returnObject = jFactory.time();
        httpClient.success = true;

        PassFailCallback<DateTime> cb1 = new PassFailCallback<DateTime>();
        PassFailCallback<DateTime> cb2 = new PassFailCallback<DateTime>();
        PassFailCallback<DateTime> cb3 = new PassFailCallback<DateTime>();

        // Success
        try {
            String jFactTimeString = jFactory.time().getString("time");
            DateTimeFormatter dtFormat = ISODateTimeFormat.dateTimeParser();
            DateTime testTime = dtFormat.parseDateTime(jFactTimeString);

            testHw.fetchTime(cb1);
            assertTrue(cb1.success);
            assertEquals(testTime, cb1.response);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Failure
        httpClient.success = false;
        httpClient.failWith = new WvaHttpException.WvaHttpInternalServerError("hw/time", "");
        testHw.fetchTime(cb2);
        assertFalse(cb2.success);
        assertNull(cb2.response);
        assertEquals(httpClient.failWith, cb2.error);

        // Success, but no time key
        httpClient.success = true;
        httpClient.returnObject = jFactory.junk();
        testHw.fetchTime(cb3);
        assertFalse(cb3.success);
        assertNull(cb3.response);
        assertEquals("No value for time", cb3.error.getMessage());
    }

    public void testSetTime() throws Exception {
        httpClient.returnObject = null;
        httpClient.success = true;

        PassFailCallback<DateTime> cb = new PassFailCallback<DateTime>();
        DateTime time = DateTime.now();

        // Success
        testHw.setTime(time, cb);
        assertTrue(cb.success);
        assertEquals(time.toDateTime(DateTimeZone.UTC), cb.response);
        assertEquals("PUT hw/time/", httpClient.requestSummary);

        // Non-empty body
        httpClient.returnObject = new JSONObject();
        testHw.setTime(time, cb);
        assertFalse(cb.success);
        assertNotNull(cb.error);
        assertEquals("Unexpected response body: {}", cb.error.getMessage());
        assertEquals(time.toDateTime(DateTimeZone.UTC), cb.response);

        // Failure
        httpClient.returnObject = null;
        httpClient.success = false;
        httpClient.failWith = new Exception("testSetTime error");
        testHw.setTime(time, cb);
        assertFalse(cb.success);
        assertNotNull(cb.error);
        assertEquals(httpClient.failWith, cb.error);
        assertEquals(time.toDateTime(DateTimeZone.UTC), cb.response);
    }
}
