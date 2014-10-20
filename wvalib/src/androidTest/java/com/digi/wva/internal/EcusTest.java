/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import android.util.Pair;

import com.digi.wva.async.WvaCallback;
import com.digi.wva.test_auxiliary.HttpClientSpoofer;
import com.digi.wva.test_auxiliary.JsonFactory;
import com.digi.wva.test_auxiliary.PassFailCallback;

import junit.framework.TestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class EcusTest extends TestCase {
    HttpClientSpoofer httpClient = new HttpClientSpoofer("hostname");
    JsonFactory jFactory = new JsonFactory();
    Ecus testEcus = new Ecus(httpClient);

    protected void setUp() throws Exception {
        httpClient.returnObject = jFactory.ecuNames();
        httpClient.success = true;
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testEcu() {
        Ecus testConstructor = new Ecus(httpClient);
        assertNotNull(testConstructor);
    }

    public void testFetchEcus() throws JSONException {
        PassFailCallback<Set<String>> cb1 = new PassFailCallback<Set<String>>();
        PassFailCallback<Set<String>> cb2 = new PassFailCallback<Set<String>>();
        PassFailCallback<Set<String>> cb3 = new PassFailCallback<Set<String>>();
        PassFailCallback<Set<String>> cb4 = new PassFailCallback<Set<String>>();
        Ecus ecus = new Ecus(httpClient);

        // Failure due to onFailure being called (success = false)
        httpClient.returnObject = jFactory.ecuNames();
        httpClient.success = false;
        ecus.fetchEcus(cb1);
        assertFalse(cb1.success);
        assertNull(cb1.response);
        assertEquals("GET vehicle/ecus/", httpClient.requestSummary);

        // Failure due to bad data
        httpClient.returnObject = jFactory.junk();
        httpClient.success = true;
        ecus.fetchEcus(cb2);
        assertFalse(cb2.success);
        assertNull(cb2.response);

        // Success (good data, onSuccess called)
        httpClient.returnObject = jFactory.ecuNames();
        httpClient.success = true;
        ecus.fetchEcus(cb3);
        assertTrue(cb3.success);
        assertNotNull(cb3.response);
        assertTrue(cb3.response.size() > 0);
        assertTrue("No can0ecu0 in response", cb3.response.contains("can0ecu0"));
        assertTrue("No can0ecu295 in response", cb3.response.contains("can0ecu295"));

        // Failure due to "ecus" key not mapping to a JSON array (code coverage case)
        httpClient.returnObject = new JSONObject().put("ecus", new JSONObject());
        ecus.fetchEcus(cb4);
        assertFalse(cb4.success);
        assertNull(cb4.response);
        assertEquals("Value {} at ecus of type org.json.JSONObject cannot be converted to JSONArray", cb4.error.getMessage());
    }

    public void testGetCachedEcus() {
        Ecus ecus = new Ecus(httpClient);
        PassFailCallback<Set<String>> cb1 = new PassFailCallback<Set<String>>();

        Set<String> cachedBefore = ecus.getCachedEcus();
        assertTrue(cachedBefore.isEmpty());

        httpClient.returnObject = jFactory.ecuNames();
        httpClient.success = true;
        ecus.fetchEcus(cb1);
        assertTrue(cb1.success);
        assertNotNull(cb1.response);
        assertEquals("GET vehicle/ecus/", httpClient.requestSummary);

        httpClient.requestSummary = null;

        Set<String> cached = ecus.getCachedEcus();
        assertNotNull(cached);
        // Check that the cached value is the fetched value
        assertTrue(cached.containsAll(cb1.response));
        assertTrue(cb1.response.containsAll(cached));

        // Cached request doesn't hit the network
        assertNull(httpClient.requestSummary);
    }

    public void testFetchEcuElements() throws JSONException {
        httpClient.returnObject = jFactory.ecuNames();
        testEcus.fetchEcus(null);

        PassFailCallback<Set<String>> cb1 = new PassFailCallback<Set<String>>();
        PassFailCallback<Set<String>> cb2 = new PassFailCallback<Set<String>>();
        PassFailCallback<Set<String>> cb3 = new PassFailCallback<Set<String>>();
        PassFailCallback<Set<String>> cb4 = new PassFailCallback<Set<String>>();
        PassFailCallback<Set<String>> cb5 = new PassFailCallback<Set<String>>();

        // Failure due to onFailure being called (success = false)
        httpClient.returnObject = jFactory.ecuEndpoints();
        httpClient.success = false;
        testEcus.fetchEcuElements("can0ecu0", cb1);
        assertFalse(cb1.success);
        assertNull(cb1.response);
        assertEquals("GET vehicle/ecus/can0ecu0", httpClient.requestSummary);

        // Failure due to missing key
        httpClient.returnObject = jFactory.ecuEndpoints(); // has can0ecu0 key
        httpClient.success = true;
        testEcus.fetchEcuElements("not_can0ecu0", cb2);
        assertFalse(cb2.success);
        assertNull(cb2.response);
        assertTrue(cb2.error instanceof JSONException);
        assertTrue(cb2.error.getMessage().contains("'not_can0ecu0'"));

        // Failure due to bad data
        httpClient.returnObject = jFactory.junk();
        httpClient.success = true;
        testEcus.fetchEcuElements("can0ecu0", cb3);
        assertFalse(cb3.success);
        assertNull(cb3.response);
        assertTrue(cb3.error instanceof JSONException);
        assertTrue(cb3.error.getMessage().contains("'can0ecu0'"));

        // Success (good data + onSuccess)
        httpClient.returnObject = jFactory.ecuEndpoints();
        httpClient.success = true;
        testEcus.fetchEcuElements("can0ecu0", cb4);
        assertTrue(cb4.success);
        assertTrue(cb4.response.size() > 0);
        assertTrue(cb4.response.contains("name"));
        assertTrue(cb4.response.contains("model"));

        // Failure due to <ecu> key not mapping to JSON array
        httpClient.returnObject = new JSONObject().put("can0ecu0", new JSONObject());
        testEcus.fetchEcuElements("can0ecu0", cb5);
        assertFalse(cb5.success);
        assertNull(cb5.response);
        assertEquals("Value {} at can0ecu0 of type org.json.JSONObject cannot be converted to JSONArray", cb5.error.getMessage());
    }

    public void testGetCachedEcuElements() {
        assertNull(testEcus.getCachedEcuElements("can0ecu0"));

        // Populate cache
        PassFailCallback<Set<String>> cb1 = new PassFailCallback<Set<String>>();
        httpClient.returnObject = jFactory.ecuEndpoints();
        httpClient.success = true;
        testEcus.fetchEcuElements("can0ecu0", cb1);
        assertTrue(cb1.success);
        assertEquals(4, cb1.response.size());

        assertNotNull(testEcus.getCachedEcuElements("can0ecu0"));
        assertEquals(4, testEcus.getCachedEcuElements("can0ecu0").size());
    }

    public void testFetchEcuElementValue() throws JSONException {
        httpClient.returnObject = jFactory.ecuNames();
        testEcus.fetchEcus(null);

        PassFailCallback<String> cb1 = new PassFailCallback<String>();
        PassFailCallback<String> cb2 = new PassFailCallback<String>();
        PassFailCallback<String> cb3 = new PassFailCallback<String>();
        PassFailCallback<String> cb4 = new PassFailCallback<String>();
        PassFailCallback<String> cb5 = new PassFailCallback<String>();

        // Failure due to onFailure being called (success = false)
        httpClient.returnObject = jFactory.ecuEndpoint();
        httpClient.success = false;
        testEcus.fetchEcuElementValue("can0ecu0", "name", cb1);
        assertFalse(cb1.success);
        assertNull(cb1.response);
        assertEquals("GET vehicle/ecus/can0ecu0/name", httpClient.requestSummary);

        // Failure due to missing key
        httpClient.returnObject = jFactory.ecuEndpoint(); // has name key
        httpClient.success = true;
        testEcus.fetchEcuElementValue("can0ecu0", "not_name", cb2);
        assertFalse(cb2.success);
        assertNull(cb2.response);
        assertTrue(cb2.error instanceof JSONException);
        assertEquals("GET vehicle/ecus/can0ecu0/not_name", httpClient.requestSummary);
        assertTrue(cb2.error.getMessage().contains("not_name"));

        // Failure due to bad data
        httpClient.returnObject = jFactory.junk();
        httpClient.success = true;
        testEcus.fetchEcuElementValue("can0ecu0", "name", cb3);
        assertFalse(cb3.success);
        assertNull(cb3.response);
        assertTrue(cb3.error instanceof JSONException);
        assertTrue(cb3.error.getMessage().contains("name"));

        // Success (good data + onSuccess)
        httpClient.returnObject = jFactory.ecuEndpoint();
        httpClient.success = true;
        testEcus.fetchEcuElementValue("can0ecu0", "name", cb4);
        assertEquals("Indigo Montoya", cb4.response);
        assertTrue(cb4.success);

        // Failure due to <element> value not being able to be coerced to String
        httpClient.returnObject = new JSONObject().put("name", new JSONArray() {
            @Override
            public String toString() {
                return null;
            }
        });
        testEcus.fetchEcuElementValue("can0ecu0", "name", cb5);
        assertFalse(cb5.success);
        assertNull(cb5.response);
        assertEquals(JSONException.class.getCanonicalName(), cb5.error.getClass().getCanonicalName());
    }

    public void testGetCachedEcuElementValue() {
        assertNull(testEcus.getCachedEcuElementValue("can0ecu0", "name"));

        // Populate cache
        PassFailCallback<String> cb1 = new PassFailCallback<String>();
        httpClient.returnObject = jFactory.ecuEndpoint();
        httpClient.success = true;
        testEcus.fetchEcuElementValue("can0ecu0", "name", cb1);
        assertTrue(cb1.success);
        assertEquals("Indigo Montoya", cb1.response);

        assertEquals("Indigo Montoya", testEcus.getCachedEcuElementValue("can0ecu0", "name"));
    }

    @SuppressWarnings("unchecked")
    public void testFetchAllEcuElementValues() {
        httpClient.returnObject = jFactory.ecuNames();
        httpClient.success = true;
        testEcus.fetchEcus(null);
        httpClient.returnObject = jFactory.ecuEndpoints();
        testEcus.fetchEcuElements("can0ecu0", null);

        httpClient.returnObject = jFactory.ecuEndpoint();
        httpClient.success = true;
        WvaCallback<Pair<String, String>> callback = mock(WvaCallback.class);
        ArgumentCaptor<Pair> captor = ArgumentCaptor.forClass(Pair.class);

        testEcus.fetchAllEcuElementValues("can0ecu0", callback);

        verify(callback, times(4)).onResponse((Throwable) isNull(), captor.capture());

        List<Pair> capturedPairs = captor.getAllValues();
        List<Pair<String, String>> expected = new ArrayList<Pair<String, String>>();
        expected.add(new Pair<String, String>("name", "Indigo Montoya"));
        expected.add(new Pair<String, String>("VIN", "123456"));
        expected.add(new Pair<String, String>("make", "Lamborghini"));
        expected.add(new Pair<String, String>("model", "Murcielago"));

        for (Pair<String, String> e : expected) {
            assertTrue("Missing value: " + e.toString(), capturedPairs.contains(e));
        }
    }

    public void testFetchAllEcuElementValues_uninitialized() throws Exception {
        // First, test when there is nothing in the cache.
        PassFailCallback<Pair<String, String>> callback = new PassFailCallback<Pair<String, String>>();

        try {
            testEcus.fetchAllEcuElementValues("can0ecu0", callback);
            fail("Expected fetchAllEcuElementValues to throw when there's no cached list");
        } catch (IllegalStateException e) {
            // Expected this.
            assertEquals("No ECU element names in cache.", e.getMessage());
        }

        // Next, test when the cached list is empty.
        PassFailCallback<Pair<String, String>> callback2 = new PassFailCallback<Pair<String, String>>();

        // Initialize the cache
        httpClient.success = true;
        httpClient.returnObject = new JSONObject().put("can0ecu0", new JSONArray());
        PassFailCallback<Set<String>> fetchcb = new PassFailCallback<Set<String>>();
        testEcus.fetchEcuElements("can0ecu0", fetchcb);
        assertTrue(fetchcb.success);

        try {
            testEcus.fetchAllEcuElementValues("can0ecu0", callback2);
            fail("Expected fetchAllEcuElementValues to throw when the cached list is empty");
        } catch (IllegalStateException e) {
            // Expected this.
            assertEquals("No ECU element names in cache.", e.getMessage());
        }
    }
}
