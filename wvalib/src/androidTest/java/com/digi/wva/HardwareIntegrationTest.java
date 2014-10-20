/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva;

import android.util.Pair;

import com.digi.wva.exc.WvaHttpException;
import com.digi.wva.test_auxiliary.IntegrationTestCase;
import com.digi.wva.test_auxiliary.MockWvaCallback;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Integration testing of the portions of the WVA library that touch the hardware-related
 * web services (LEDs, buttons, etc.).
 */
public class HardwareIntegrationTest extends IntegrationTestCase {
    // List of (error code, error class to expect)
    private static List<Pair<Integer, Class<? extends WvaHttpException>>> httpErrors =
            new ArrayList<Pair<Integer, Class<? extends WvaHttpException>>>();

    static {
        httpErrors.add(new Pair<Integer, Class<? extends WvaHttpException>>(400, WvaHttpException.WvaHttpBadRequest.class));
        httpErrors.add(new Pair<Integer, Class<? extends WvaHttpException>>(403, WvaHttpException.WvaHttpForbidden.class));
        httpErrors.add(new Pair<Integer, Class<? extends WvaHttpException>>(404, WvaHttpException.WvaHttpNotFound.class));
        httpErrors.add(new Pair<Integer, Class<? extends WvaHttpException>>(414, WvaHttpException.WvaHttpRequestUriTooLong.class));
        httpErrors.add(new Pair<Integer, Class<? extends WvaHttpException>>(500, WvaHttpException.WvaHttpInternalServerError.class));
        httpErrors.add(new Pair<Integer, Class<? extends WvaHttpException>>(503, WvaHttpException.WvaHttpServiceUnavailable.class));
    }

    //=========================================================================
    // LED-related tests

    /**
     * Verify the HTTP interactions of the
     * {@link WVA#setLedState(String, boolean, com.digi.wva.async.WvaCallback)} method,
     * when successful.
     */
    public void testSetLedState() throws Exception {
        // Enqueue successful responses for both On and Off requests
        ws.enqueue(new MockResponse().setBody(""));
        ws.enqueue(new MockResponse().setBody(""));

        MockWvaCallback<Boolean> callbackOff = new MockWvaCallback<Boolean>("setLedState OFF");
        MockWvaCallback<Boolean> callbackOn = new MockWvaCallback<Boolean>("setLedState ON");

        //======================================================
        // Test turning an LED off

        // Make a request to turn the LED off
        wva.setLedState("yellow", false, callbackOff);
        callbackOff.expectResponse();
        // Verify that the request succeeded
        assertNull(callbackOff.error);
        assertFalse(callbackOff.response);

        // Verify that the request was correct
        RecordedRequest requestOff = ws.takeRequest();
        assertEquals("PUT /ws/hw/leds/yellow HTTP/1.1", requestOff.getRequestLine());
        assertEquals(new JSONObject().put("led", "off").toString(), requestOff.getUtf8Body());

        //======================================================
        // Test turning an LED on

        // Make a request to turn the LED on
        wva.setLedState("yellow", true, callbackOn);
        callbackOn.expectResponse();
        // Verify that the request succeeded
        assertNull(callbackOn.error);
        assertTrue(callbackOn.response);

        // Verify that the request was correct
        RecordedRequest requestOn = ws.takeRequest();
        assertEquals("PUT /ws/hw/leds/yellow HTTP/1.1", requestOn.getRequestLine());
        assertEquals(new JSONObject().put("led", "on").toString(), requestOn.getUtf8Body());
    }

    /**
     * Verify the behavior of setLedState when the WVA responds with non-successful
     * error codes or an invalid body.
     */
    public void testSetLedStateErrors() throws Exception {
        for (Pair<Integer, Class<? extends WvaHttpException>> error : httpErrors) {
            ws.enqueue(new MockResponse().setResponseCode(error.first));

            // Send the request
            MockWvaCallback<Boolean> cb = new MockWvaCallback<Boolean>("setLedState, error code " + error.first);
            wva.setLedState("foo", true, cb);
            cb.expectResponse();

            // Verify the callback got the right arguments
            assertNull(cb.response);
            assertNotNull(cb.error);
            assertEquals("Wrong exception class on error code " + error.first,
                         error.second, cb.error.getClass());

            // Verify the right URL was used in the request. The request body is verified in
            // testSetLedState
            assertEquals("PUT /ws/hw/leds/foo HTTP/1.1", ws.takeRequest().getRequestLine());
        }

        // Verify what happens if the response body is not empty. (Distant edge case)
        ws.enqueue(new MockResponse().setBody("Lorem Ipsum"));
        MockWvaCallback<Boolean> cb = new MockWvaCallback<Boolean>("setLedState, non-empty body");
        wva.setLedState("foo", true, cb);
        cb.expectResponse();

        assertNull(cb.response);
        assertEquals("Unexpected response body: Lorem Ipsum", cb.error.getMessage());
    }

    /**
     * Verify the HTTP interactions of the
     * {@link WVA#fetchLedState(String, com.digi.wva.async.WvaCallback)} method,
     * when successful.
     */
    public void testFetchLedState() throws Exception {
        // Enqueue successful responses for both values (on and off)
        ws.enqueue(new MockResponse().setBody(new JSONObject().put("led", "off").toString()));
        ws.enqueue(new MockResponse().setBody(new JSONObject().put("led", "on").toString()));

        MockWvaCallback<Boolean> callbackOff = new MockWvaCallback<Boolean>("fetchLedState OFF");
        MockWvaCallback<Boolean> callbackOn = new MockWvaCallback<Boolean>("fetchLedState ON");

        //======================================================
        // Test the LED being off

        // Make a request (when the LED is off)
        wva.fetchLedState("yellow", callbackOff);
        callbackOff.expectResponse();
        // Verify that the request succeeded
        assertNull(callbackOff.error);
        assertFalse(callbackOff.response);

        // Verify that the request was correct
        RecordedRequest requestOn = ws.takeRequest();
        assertEquals("GET /ws/hw/leds/yellow HTTP/1.1", requestOn.getRequestLine());
        assertEquals("", requestOn.getUtf8Body());

        //======================================================
        // Test the LED being on

        // Make a request (when the LED is on)
        wva.fetchLedState("yellow", callbackOn);
        callbackOn.expectResponse();
        // Verify that the request succeeded
        assertNull(callbackOn.error);
        assertTrue(callbackOn.response);

        // Verify that the request was correct
        RecordedRequest requestOff = ws.takeRequest();
        assertEquals("GET /ws/hw/leds/yellow HTTP/1.1", requestOff.getRequestLine());
        assertEquals("", requestOff.getUtf8Body());
    }

    /**
     * Verify the behavior of fetchLedState when the WVA responds with non-successful
     * error codes or an invalid body.
     */
    public void testFetchLedStateErrors() throws Exception {
        for (Pair<Integer, Class<? extends WvaHttpException>> error : httpErrors) {
            ws.enqueue(new MockResponse().setResponseCode(error.first));

            // Send the request
            MockWvaCallback<Boolean> cb = new MockWvaCallback<Boolean>("fetchLedState, error code " + error.first);
            wva.fetchLedState("foo", cb);
            cb.expectResponse();

            // Verify the callback got the right arguments
            assertNull(cb.response);
            assertNotNull(cb.error);
            assertEquals("Wrong exception class on error code " + error.first,
                    error.second, cb.error.getClass());

            // Verify the right URL was used in the request. The request body is verified in
            // testFetchLedState
            assertEquals("GET /ws/hw/leds/foo HTTP/1.1", ws.takeRequest().getRequestLine());
        }

        // Verify what happens if the response body is not valid JSON. (Distant edge case)
        ws.enqueue(new MockResponse().setBody("Lorem Ipsum"));
        MockWvaCallback<Boolean> cb = new MockWvaCallback<Boolean>("fetchLedState, invalid body");
        wva.fetchLedState("foo", cb);
        cb.expectResponse();

        assertNull(cb.response);
        assertEquals("Value Lorem of type java.lang.String cannot be converted to JSONObject", cb.error.getMessage());
    }


    //=========================================================================
    // Button-related tests

    /**
     * Verify the HTTP interactions of the
     * {@link WVA#fetchButtonState(String, com.digi.wva.async.WvaCallback)} method,
     * when successful.
     */
    public void testFetchButtonState() throws Exception {
        // Enqueue successful responses for both values (up and down)
        ws.enqueue(new MockResponse().setBody(new JSONObject().put("button", "down").toString()));
        ws.enqueue(new MockResponse().setBody(new JSONObject().put("button", "up").toString()));

        MockWvaCallback<Boolean> callbackDown = new MockWvaCallback<Boolean>("fetchButtonState DOWN");
        MockWvaCallback<Boolean> callbackUp = new MockWvaCallback<Boolean>("fetchButtonState UP");

        //======================================================
        // Test the button being down (depressed)

        // Make a request (when the button is down)
        wva.fetchButtonState("reset", callbackDown);
        callbackDown.expectResponse();
        // Verify that the request succeeded
        assertNull(callbackDown.error);
        assertFalse(callbackDown.response);

        // Verify that the request was correct
        RecordedRequest requestOn = ws.takeRequest();
        assertEquals("GET /ws/hw/buttons/reset HTTP/1.1", requestOn.getRequestLine());
        assertEquals("", requestOn.getUtf8Body());

        //======================================================
        // Test the button being up (un-depressed)

        // Make a request (when the button is up)
        wva.fetchButtonState("reset", callbackUp);
        callbackUp.expectResponse();
        // Verify that the request succeeded
        assertNull(callbackUp.error);
        assertTrue(callbackUp.response);

        // Verify that the request was correct
        RecordedRequest requestOff = ws.takeRequest();
        assertEquals("GET /ws/hw/buttons/reset HTTP/1.1", requestOff.getRequestLine());
        assertEquals("", requestOff.getUtf8Body());
    }

    /**
     * Verify the behavior of fetchButtonState when the WVA responds with non-successful
     * error codes or an invalid body.
     */
    public void testFetchButtonStateErrors() throws Exception {
        for (Pair<Integer, Class<? extends WvaHttpException>> error : httpErrors) {
            ws.enqueue(new MockResponse().setResponseCode(error.first));

            // Send the request
            MockWvaCallback<Boolean> cb = new MockWvaCallback<Boolean>("fetchButtonState, error code " + error.first);
            wva.fetchButtonState("reset", cb);
            cb.expectResponse();

            // Verify the callback got the right arguments
            assertNull(cb.response);
            assertNotNull(cb.error);
            assertEquals("Wrong exception class on error code " + error.first,
                    error.second, cb.error.getClass());

            // Verify the right URL was used in the request. The request body is verified in
            // testFetchLedState
            assertEquals("GET /ws/hw/buttons/reset HTTP/1.1", ws.takeRequest().getRequestLine());
        }

        // Verify what happens if the response body is not valid JSON. (Distant edge case)
        ws.enqueue(new MockResponse().setBody("Lorem Ipsum"));
        MockWvaCallback<Boolean> cb = new MockWvaCallback<Boolean>("fetchButtonState, invalid body");
        wva.fetchButtonState("reset", cb);
        cb.expectResponse();

        assertNull(cb.response);
        assertEquals("Value Lorem of type java.lang.String cannot be converted to JSONObject", cb.error.getMessage());
    }
}
