/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva;

import android.util.Base64;

import com.digi.wva.test_auxiliary.IntegrationTestCase;
import com.digi.wva.test_auxiliary.MockStateListener;
import com.digi.wva.test_auxiliary.MockWvaCallback;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;

/**
 * Integration tests for the general WVA class API.
 *
 * <p>
 *     Any {@link Thread#sleep(long) Thread.sleep} calls or other timing manipulation in these
 *     tests is considered to be a code smell. The WVA library does not intrinsically care about
 *     the timing of data arriving via the event channel or asynchronous HTTP calls.
 *     {@link MockStateListener} and {@link MockWvaCallback} use a timeout of 5 seconds.
 * </p>
 *
 * <p>
 *     If we do end up writing tests which are impacted by timing (e.g.
 *     {@link com.digi.wva.async.EventChannelStateListener#reconnectAfter(WVA, long, int) EventChannelStateListener.reconnectAfter}),
 *     then these tests should be written to explicitly record the time difference and use some
 *     synchronization technique such as a {@link java.util.concurrent.CountDownLatch
 *     CountDownLatch}:
 *
 *     <pre>
 *         CountDownLatch onConnected = new CountDownLatch(1);
 *         long failureTime, connectedTime;
 *
 *         wva.setEventChannelStateListener(new ... {
 *             public void onFailedConnection(...) {
 *                 failureTime = System.currentTimeMillis();
 *                 reconnectAfter(device, 15000, events.getPort());
 *             }
 *
 *             public void onConnected(...) {
 *                 connectedTime = System.currentTimeMillis();
 *                 onConnected.countDown();
 *             }
 *         });
 *
 *         onConnected.await(20, TimeUnit.SECONDS);
 *         long difference = connectedTime - failureTime;
 *
 *         // Check that reconnection occurred within 1 second of 15 seconds after failure
 *         assertTrue(Math.abs(difference - 15000) < 1000);
 *     </pre>
 * </p>
 */
public class WVAIntegrationTest extends IntegrationTestCase {

    /**
     * Test the behavior of the {@link WVA#isWVA(com.digi.wva.async.WvaCallback) isWVA} method.
     */
    public void testIsWVA() throws Exception {
        // Prepare the HTTP response
        JSONArray services = new JSONArray(
                Arrays.asList("vehicle", "hw", "config", "state", "files", "alarms",
                              "subscriptions", "password"));
        JSONObject body = new JSONObject().put("ws", services);
        ws.enqueue(new MockResponse().setBody(body.toString()));

        // Make the isWVA call
        MockWvaCallback<Boolean> callback = new MockWvaCallback<Boolean>("isWVA");
        wva.isWVA(callback);

        // Wait for the response
        callback.expectResponse();

        // Verify the response is good
        assertTrue(callback.response);
        assertNull(callback.error);

        // Verify the request was good as well
        RecordedRequest request = ws.takeRequest();
        assertEquals("GET /ws/ HTTP/1.1", request.getRequestLine());
        assertEquals("", request.getUtf8Body());
    }

    /**
     * Test the overall behavior of the {@link WVA#useBasicAuth(String, String) useBasicAuth} and
     * {@link WVA#clearBasicAuth() clearBasicAuth} methods.
     */
    public void testBasicAuth() throws Exception {
        MockWvaCallback<Boolean> callback;
        RecordedRequest request;

        // Enqueue a response. We aren't going to look at it though.
        ws.enqueue(new MockResponse().setBody(""));

        wva.useBasicAuth("test_username", "password");
        callback = new MockWvaCallback<Boolean>("basic auth test");
        wva.isWVA(callback);
        callback.expectResponse();

        // Verify the HTTP request Authorization header
        request = ws.takeRequest();
        String auth = request.getHeader("Authorization");
        assertNotNull(auth);
        // base64-encode the authentication
        assertEquals("Basic " + Base64.encodeToString("test_username:password".getBytes(), Base64.NO_WRAP),
                     auth);

        // Now test without basic auth
        ws.enqueue(new MockResponse().setBody(""));

        wva.clearBasicAuth();
        callback = new MockWvaCallback<Boolean>("no-basic-auth test");
        wva.isWVA(callback);
        callback.expectResponse();

        // Verify that there is no Authorization header
        request = ws.takeRequest();
        assertNull(request.getHeader("Authorization"));
    }
}
