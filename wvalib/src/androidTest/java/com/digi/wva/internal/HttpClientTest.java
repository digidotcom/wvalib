/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import com.digi.wva.exc.WvaHttpException;
import com.digi.wva.test_auxiliary.PassFailHttpCallback;
import com.squareup.okhttp.Authenticator;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import junit.framework.TestCase;

import org.json.JSONObject;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;

import javax.net.ssl.HostnameVerifier;

/**
 * Test the behavior of the WVALib HttpClient.
 */
public class HttpClientTest extends TestCase {
    public static final String TAG = "HttpClientTest";
    PassFailHttpCallback callback;
    MockWebServer server;
    HttpClient dut;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        server = new MockWebServer();
        server.play();

        callback = new PassFailHttpCallback();

        // Configure HTTP client to point to mock server
        dut = new HttpClient(server.getHostName());
        dut.useSecureHttp(false);
        dut.setHttpPort(server.getPort());
    }

    @Override
    protected void tearDown() throws Exception {
        server.shutdown();
    }

    private void verifyRequest(String requestLine, String body) {
        // Add HTTP/1.1 to request line
        requestLine += " HTTP/1.1";

        try {
            RecordedRequest request = server.takeRequest();

            assertEquals(requestLine, request.getRequestLine());

            // Test that the Accept header has json in it
            String accept = request.getHeader("Accept");
            assertTrue("No application/json in header: " + accept, accept.contains("application/json"));

            if (body != null) {
                assertEquals(body, request.getUtf8Body());
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testHttpClient() {
        HttpClient constructorTest = new HttpClient("HOSTNAME");
        assertNotNull(constructorTest);
    }

    public void testGet() {
        server.enqueue(new MockResponse().setBody("{}"));

        dut.get("url", callback);

        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertTrue(r.success);
        assertNotNull(r.response);

        verifyRequest("GET /ws/url", null);
    }

    public void testPut() {
        server.enqueue(new MockResponse().setBody("{}"));

        dut.put("url", new JSONObject(), callback);

        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertTrue(r.success);
        assertNotNull(r.response);

        verifyRequest("PUT /ws/url", "{}");
    }

    public void testPutNull() {
        server.enqueue(new MockResponse().setBody("{}"));

        dut.put("url", null, callback);

        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertTrue(r.success);
        assertNotNull(r.response);

        verifyRequest("PUT /ws/url", "");
    }

    public void testDelete() {
        server.enqueue(new MockResponse().setBody("{}"));

        dut.delete("url", callback);

        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertTrue(r.success);
        assertNotNull(r.response);

        verifyRequest("DELETE /ws/url", null);
    }

    public void testPost() {
        server.enqueue(new MockResponse().setBody("{}"));

        dut.post("url", new JSONObject(), callback);

        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertTrue(r.success);
        assertNotNull(r.response);

        verifyRequest("POST /ws/url", "{}");
    }

    public void testPostNull() {
        server.enqueue(new MockResponse().setBody("{}"));
        dut.post("url", null, callback);

        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertTrue(r.success);
        assertNotNull(r.response);

        verifyRequest("POST /ws/url", "");
    }

    public void testGetAbsoluteUrl() {
        dut.setHttpPort(5001);
        dut.setHttpsPort(5002);

        String host = server.getHostName();

        // Test HTTP, HTTP port setting
        dut.useSecureHttp(false); // Use HTTP port
        String absUrl = dut.getAbsoluteUrl("xyz").toString();
        assertEquals("http://" + host + ":5001/ws/xyz", absUrl);

        dut.setHttpPort(80);
        absUrl = dut.getAbsoluteUrl("xyz").toString();
        assertEquals("http://" + host + ":80/ws/xyz", absUrl);

        // Test HTTPS, HTTPS port setting
        dut.useSecureHttp(true);
        absUrl = dut.getAbsoluteUrl("xyz").toString();
        assertEquals("https://" + host + ":5002/ws/xyz", absUrl);

        dut.setHttpsPort(443);
        absUrl = dut.getAbsoluteUrl("xyz").toString();
        assertEquals("https://" + host + ":443/ws/xyz", absUrl);

        // Malformed URL handling
        dut.setHttpsPort(-99);
        try {
            URL result = dut.getAbsoluteUrl("xyz");
            fail("Expected getAbsoluteUrl to throw when port == -99. Got: " + result.toString());
        } catch (AssertionError e) {
            assertEquals("Malformed URL: https://localhost:-99/ws/xyz", e.getMessage());
        }
    }

    public void testUnderlyingClient() {
        OkHttpClient okhttp = dut.getUnderlyingClient();

        // Hostname verifier should return true for everything
        HostnameVerifier v = okhttp.getHostnameVerifier();
        assertTrue(v.verify("foo", null));
        assertTrue(v.verify("bar", null));
        assertTrue(v.verify("https://google.c", null));
    }

    public void test400BadRequest() {
        server.enqueue(new MockResponse().setResponseCode(400));

        dut.get("some_url", callback);
        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertFalse(r.success);
        assertNotNull(r.error);
        assertEquals(WvaHttpException.WvaHttpBadRequest.class, r.error.getClass());
    }

    public void test403Forbidden() {
        server.enqueue(new MockResponse().setResponseCode(403));

        dut.get("some_url", callback);
        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertFalse(r.success);
        assertNotNull(r.error);
        assertEquals(WvaHttpException.WvaHttpForbidden.class, r.error.getClass());
    }

    public void test404NotFound() {
        server.enqueue(new MockResponse().setResponseCode(404));

        dut.get("some_url", callback);
        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertFalse(r.success);
        assertNotNull(r.error);
        assertEquals(WvaHttpException.WvaHttpNotFound.class, r.error.getClass());
    }

    public void test414TooLong() {
        server.enqueue(new MockResponse().setResponseCode(414));

        dut.get("some_url", callback);
        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertFalse(r.success);
        assertNotNull(r.error);
        assertEquals(WvaHttpException.WvaHttpRequestUriTooLong.class, r.error.getClass());
    }

    public void test500InternalServerError() {
        server.enqueue(new MockResponse().setResponseCode(500));

        dut.get("some_url", callback);
        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertFalse(r.success);
        assertNotNull(r.error);
        assertEquals(WvaHttpException.WvaHttpInternalServerError.class, r.error.getClass());
    }

    public void test403ServiceUnavailable() {
        server.enqueue(new MockResponse().setResponseCode(503));

        dut.get("some_url", callback);
        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertFalse(r.success);
        assertNotNull(r.error);
        assertEquals(WvaHttpException.WvaHttpServiceUnavailable.class, r.error.getClass());
    }

    public void testOtherResponseCode() {
        // Enable logging to get code coverage of request/response logging in the cases of no
        // prior responses.
        dut.setLoggingEnabled(true);
        server.enqueue(new MockResponse().setResponseCode(418)); // I'm a teapot

        dut.get("some_url", callback);
        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertFalse(r.success);
        assertNotNull(r.error);
        assertEquals(WvaHttpException.class, r.error.getClass());
    }

    public void testFailure() {
        dut.get("this is an invalid url", callback);

        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertFalse(r.success);
        assertNotNull(r.error);
        assertEquals(IOException.class, r.error.getClass());
    }

    /**
     * Just for code coverage over the request/response logging and basic "is it working" test.
     * A redirect will trigger additional response logging logic.
     */
    public void testRedirect() {
        dut.setLoggingEnabled(true);

        String redirectLocation = "http://" + server.getHostName() + ":" + server.getPort() + "/some_other_url";
        // First redirect to "some_other_url"
        server.enqueue(new MockResponse().addHeader("Location", redirectLocation).setResponseCode(301));
        // Then redirect to "some_other_url2" (covers the branching on do/while over prior requests)
        server.enqueue(new MockResponse().addHeader("Location", redirectLocation + "2").setResponseCode(301));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        dut.get("some_url", callback);

        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertTrue(r.success);
        assertNull(r.error);
    }

    /**
     * Just for code coverage over the request/response logging and basic "is it working" test.
     * This will cover the code branches where the prior request was NOT a redirect.
     */
    public void testAuthChallenge() {
        dut.setLoggingEnabled(true);

        // First send an auth challenge
        server.enqueue(new MockResponse().addHeader("WWW-Authenticate", "Basic realm=\"foo\"").setResponseCode(401));
        // Then send an OK response
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        // Need to set up an Authenticator to handle the challenge though. But as was mentioned above,
        // this test is purely to exercise the code for a) coverage and b) assurance that it doesn't
        // error out in this case.
        dut.getUnderlyingClient().setAuthenticator(new Authenticator() {
            @Override
            public Request authenticate(Proxy proxy, Response response) throws IOException {
                // See https://github.com/square/okhttp/wiki/Recipes, "Handling authentication"
                String credentials = com.squareup.okhttp.Credentials.basic("user", "pass");
                return response.request().newBuilder().header("Authorization", credentials).build();
            }

            @Override
            public Request authenticateProxy(Proxy proxy, Response response) throws IOException {
                return null;
            }
        });
        dut.get("some_url", callback);

        PassFailHttpCallback.RecordedResponse r = callback.await();
        assertTrue(r.success);
        assertNull(r.error);
    }

    /**
     * Test the ability to set logging enabled/disabled on the HttpClient.
     * (Does not test the effect of setting this value, just verifies external API.)
     */
    public void testGetSetLoggingEnabled() {
        // Logging should default to disabled.
        assertFalse(dut.getLoggingEnabled());

        dut.setLoggingEnabled(true);
        assertTrue(dut.getLoggingEnabled());

        dut.setLoggingEnabled(false);
        assertFalse(dut.getLoggingEnabled());
    }
}
