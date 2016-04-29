/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import android.util.Log;

import com.digi.wva.exc.WvaHttpException;
import com.digi.wva.exc.WvaHttpException.WvaHttpBadRequest;
import com.digi.wva.exc.WvaHttpException.WvaHttpForbidden;
import com.digi.wva.exc.WvaHttpException.WvaHttpInternalServerError;
import com.digi.wva.exc.WvaHttpException.WvaHttpNotFound;
import com.digi.wva.exc.WvaHttpException.WvaHttpRequestUriTooLong;
import com.digi.wva.exc.WvaHttpException.WvaHttpServiceUnavailable;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Locale;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * The abstraction layer between HTTP operations by this library and the underlying HTTP services.
 */
@SuppressWarnings("UnusedDeclaration")
public class HttpClient {

    /**
     * Wrapper around OkHttp's {@link Callback} interface, specifically targeted at
     * expecting and handling JSON response bodies.
     */
    public static abstract class HttpCallback {
        /**
         * Called when an HTTP response was successfully returned from the WVA,
         * and the response was successfully parsed as a JSON object.
         *
         * @param response the JSON object from the HTTP response
         */
        public abstract void onSuccess(JSONObject response);

        /**
         * Called when the HTTP request failed or had an error response.
         *
         * Called in any of these cases:
         * <ul>
         *     <li>The request could not be executed due to cancellation, a connectivity problem, or a timeout.</li>
         *     <li>An HTTP response was received, but its status code did not indicate success (e.g. 404, 500).</li>
         *     <li>An HTTP response was received, but its body could not be parsed as a JSON object.</li>
         * </ul>
         *
         * @param error the {@link Throwable} causing the failure
         */
        public abstract void onFailure(Throwable error);

        /**
         * Invoked when the HTTP request was successful, but the response body could not
         * be parsed as a JSON object. The default implementation logs the error and
         * invokes {@link #onFailure(Throwable) onFailure(Throwable)}.
         *
         * @param error the {@link JSONException} raised while parsing the response body
         * @param rawBody the body which could not be parsed as JSON
         */
        public void onJsonParseError(JSONException error, String rawBody) {
            String errmsg = "Error parsing response as JSON: " + error.getMessage();
            Log.e("HttpCallback", errmsg + "\n" + rawBody);
            this.onFailure(error);
        }
    }

    /**
     * Version of {@link HttpCallback} which expects the HTTP response body to be empty.
     * Useful as a callback for requests which do not send a body back (such as performing
     * the PUT requests for subscriptions, etc.).
     */
    public static abstract class ExpectEmptyCallback extends HttpCallback {
        /**
         * Calls {@link #onBodyNotEmpty(String)}, with <b>response.toString()</b>.
         *
         * <p>This method is final so as to minimize user error.</p>
         */
        @Override
        public final void onSuccess(JSONObject response) {
            // Response was JSON. We did not expect this.
            Log.w("ExpectEmptyCallback", "Got a JSON response.");
            onBodyNotEmpty(response.toString());
        }

        /**
         * Called when the HTTP response was considered successful (status code in the 200s),
         * but the body was not empty.
         *
         * @param body the HTTP response body
         */
        public abstract void onBodyNotEmpty(String body);

        /**
         * Called when the HTTP response was successfully received from the WVA,
         * and the response body was empty (as expected).
         */
        public abstract void onSuccess();

        /**
         * Invoked when the HTTP request was successful, but the response body could not
         * be parsed as a JSON object.
         *
         * <p>This class makes this method final to minimize user error, because this is the point
         * where empty response bodies are handled (so as to call {@link #onSuccess()}).</p>
         *
         * @param error the {@link JSONException} raised while parsing the response body
         * @param rawBody the body which could not be parsed as JSON
         */
        @Override
        public final void onJsonParseError(JSONException error, String rawBody) {
            if (rawBody != null && rawBody.trim().length() == 0) {
                // JSON parsing failed because there was no content in the body. We're okay with this.
                onSuccess();
            } else {
                onBodyNotEmpty(rawBody);
            }
        }
    }

    private static final String TAG = "wvalib HttpClient";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /**
     * Provides the basic format of a WVA web service resource.
     * For instance: http://192.168.0.3/ws/vehicle/EngineSpeed
     */
    private String credentials;
    private int httpPort = 80, httpsPort = 443;
    private boolean useSecureHttp;

    /**
     * If true, we will log outgoing requests and incoming responses as follows:
     *
     * Outgoing:
     *     --> GET http://192.168.0.3/ws/vehicle/data
     * Incoming:
     *     <-- 200 GET http://192.168.0.3/ws/vehicle/data
     */
    private boolean doLogging = false;

    private final String hostname;
    private final OkHttpClient client;

	public class TLSSocketFactory extends SSLSocketFactory {
		private SSLSocketFactory internalSSLSocketFactory;

		public TLSSocketFactory() throws KeyManagementException, NoSuchAlgorithmException {
			SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new X509TrustManager[]{new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                }
            }}, null);
			internalSSLSocketFactory = context.getSocketFactory();
		}

		@Override
		public String[] getDefaultCipherSuites() {
			return internalSSLSocketFactory.getDefaultCipherSuites();
		}

		@Override
		public String[] getSupportedCipherSuites() {
			return internalSSLSocketFactory.getSupportedCipherSuites();
		}

		@Override
		public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
			return enableTLSOnSocket(internalSSLSocketFactory.createSocket(s, host, port, autoClose));
		}

		@Override
		public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
			return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
		}

		@Override
		public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
			return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port, localHost, localPort));
		}

		@Override
		public Socket createSocket(InetAddress host, int port) throws IOException {
			return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
		}

		@Override
		public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
			return enableTLSOnSocket(internalSSLSocketFactory.createSocket(address, port, localAddress, localPort));
		}

		private Socket enableTLSOnSocket(Socket socket) {
			if(socket != null && (socket instanceof SSLSocket)) {
				((SSLSocket)socket).setEnabledProtocols(new String[] {"TLSv1.2"});
			}
			return socket;
		}
	}

    /**
     * Returns an SSLSocketFactory which trusts any certificate. (Needed in order to connect
     * with the WVA when using HTTPS.)
     * @return an SSLSocketFactory which trusts all certificates
     */
    private SSLSocketFactory makeSSLSocketFactory() {
		SSLSocketFactory factory = null;

		try {
			factory = new TLSSocketFactory();
		} catch (NoSuchAlgorithmException e) {
		} catch (KeyManagementException e) {
		}

		return factory;
    }

    /** Constructor
     *
     * @param hostname The hostname/IP address of the WVA.
     */
    public HttpClient(String hostname) {
        this.client = new OkHttpClient();
        client.setSslSocketFactory(makeSSLSocketFactory())
              .setHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        this.hostname = hostname;
    }

    /**
     * @return true if HTTP logging is enabled
     */
    public boolean getLoggingEnabled() {
        return this.doLogging;
    }

    /**
     * Set whether all HTTP requests and responses should be logged to the standard Android logs
     * @param enabled true if requests/responses should be logged, false otherwise
     */
    public void setLoggingEnabled(boolean enabled) {
        this.doLogging = enabled;
    }

    /**
     * Create a new {@link com.squareup.okhttp.Request.Builder Request.Builder} for accessing the
     * given URL, and adds the {@code Accept: application/json} header, as well as an
     * {@code Authorization} header if authentication is being used.
     *
     * <p>This method is protected, rather than private, due to a bug between JaCoCo and
     * the Android build tools which causes the instrumented bytecode to be invalid when this
     * method is private:
     * <a href="http://stackoverflow.com/questions/17603192/dalvik-transformation-using-wrong-invoke-opcode" target="_blank">see StackOverflow question.</a>
     * </p>
     *
     * @param url the path, relative to {@code /ws/}, on which to perform a request
     * @return a corresponding {@link com.squareup.okhttp.Request.Builder Request.Builder} object
     */
    protected Request.Builder makeBuilder(String url) {
        Request.Builder builder = new Request.Builder().url(getAbsoluteUrl(url)).header("Accept", "application/json");
        if (credentials != null) {
            builder.header("Authorization", credentials);
        }
        return builder;
    }

    /**
     * Converts a JSONObject, to be used as the body of a request, to its corresponding
     * {@link RequestBody} representation.
     *
     * <p>Used by {@link #post} and {@link #put}, as well as their synchronous variants.</p>
     *
     * <p>This method is protected, rather than private, due to a bug between JaCoCo and
     * the Android build tools which causes the instrumented bytecode to be invalid when this
     * method is private:
     * <a href="http://stackoverflow.com/questions/17603192/dalvik-transformation-using-wrong-invoke-opcode" target="_blank">see StackOverflow question.</a>
     * </p>
     *
     * @param obj the JSON data to be used
     * @return the request body
     */
    protected RequestBody makeBody(JSONObject obj) {
        return obj == null ? null : RequestBody.create(JSON, obj.toString());
    }

    /**
     * Wrap an HttpCallback in an OkHttp {@link Callback} instance.
     * Also will automatically attempt to parse the response as JSON.
     *
     * <p>This method is protected, rather than private, due to a bug between JaCoCo and
     * the Android build tools which causes the instrumented bytecode to be invalid when this
     * method is private:
     * <a href="http://stackoverflow.com/questions/17603192/dalvik-transformation-using-wrong-invoke-opcode" target="_blank">see StackOverflow question.</a>
     * </p>

     * @param callback the {@link com.digi.wva.internal.HttpClient.HttpCallback} to wrap
     * @return a corresponding {@link Callback}
     */
    protected Callback wrapCallback(final HttpCallback callback) {
        return new Callback() {
            @Override
            public void onResponse(Response response) throws IOException {
                logResponse(response);

                Request request = response.request();
                String responseBody = response.body().string();
                if (response.isSuccessful()) {
                    // Request succeeded. Parse JSON response.
                    try {
                        JSONObject parsed = new JSONObject(responseBody);
                        callback.onSuccess(parsed);
                    } catch (JSONException e) {
                        callback.onJsonParseError(e, responseBody);
                    }
                } else {
                    int status = response.code();
                    String url = response.request().urlString();
                    Exception error;

                    // Generate an appropriate exception based on the status code
                    switch (status) {
                        case 400:
                            error = new WvaHttpBadRequest(url, responseBody);
                            break;
                        case 403:
                            error = new WvaHttpForbidden(url, responseBody);
                            break;
                        case 404:
                            error = new WvaHttpNotFound(url, responseBody);
                            break;
                        case 414:
                            error = new WvaHttpRequestUriTooLong(url, responseBody);
                            break;
                        case 500:
                            error = new WvaHttpInternalServerError(url, responseBody);
                            break;
                        case 503:
                            error = new WvaHttpServiceUnavailable(url, responseBody);
                            break;
                        default:
                            error = new WvaHttpException("HTTP " + status, url, responseBody);
                            break;
                    }

                    callback.onFailure(error);
                }
            }

            @Override
            public void onFailure(Request request, IOException exception) {
                callback.onFailure(exception);
            }
        };
    }

    /**
     * Asynchronously perform an HTTP GET request on the given path.
     *
     * @param url the path, relative to {@code /ws/}, on which to perform a GET request
     * @param callback a callback for when the request completes or is in error
     */
    public void get(String url, HttpCallback callback) {
        Request request = makeBuilder(url).get().build();
        logRequest(request);
        client.newCall(request).enqueue(wrapCallback(callback));
    }

    /**
     * Asynchronously perform an HTTP POST request on the given path,
     * with the given JSON data.
     *
     * @param url the path, relative to {@code /ws/}, on which to perform a POST request
     * @param obj the JSON object to POST to the device
     * @param callback a callback for when the request completes or is in error
     */
    public void post(String url, JSONObject obj, HttpCallback callback) {
        Request request = this.makeBuilder(url).post(this.makeBody(obj)).build();
        logRequest(request);
        client.newCall(request).enqueue(wrapCallback(callback));
    }

    /**
     * Asynchronously perform an HTTP PUT request on the given path,
     * with the given JSON data.
     *
     * @param url the path, relative to {@code /ws/}, on which to perform a PUT request
     * @param obj the JSON object to PUT to the device
     * @param callback a callback for when the request completes or is in error
     */
    public void put(String url, JSONObject obj, HttpCallback callback) {
        Request request = this.makeBuilder(url).put(this.makeBody(obj)).build();
        logRequest(request);
        client.newCall(request).enqueue(wrapCallback(callback));
    }

    /**
     * Asynchronously perform an HTTP DELETE request on the given path.
     *
     * @param url the path, relative to {@code /ws/}, on which to perform a DELETE request
     * @param callback a callback for when the request completes or is in error
     */
    public void delete(String url, HttpCallback callback) {
        Request request = this.makeBuilder(url).delete().build();
        logRequest(request);
        client.newCall(request).enqueue(wrapCallback(callback));
    }

    /**
     * Log information of OkHttp Request objects
     *
     * <p>This method is protected, rather than private, due to a bug between JaCoCo and
     * the Android build tools which causes the instrumented bytecode to be invalid when this
     * method is private:
     * <a href="http://stackoverflow.com/questions/17603192/dalvik-transformation-using-wrong-invoke-opcode" target="_blank">see StackOverflow question.</a>
     * </p>
     */
    protected void logRequest(Request request) {
        if (!doLogging) {
            // Logging is disabled - do nothing.
            return;
        }

        Log.i(TAG, "\u2192 " + request.method() + " " + request.urlString());
    }

    /**
     * Log information of OkHttp Response objects
     *
     * <p>This method is protected, rather than private, due to a bug between JaCoCo and
     * the Android build tools which causes the instrumented bytecode to be invalid when this
     * method is private:
     * <a href="http://stackoverflow.com/questions/17603192/dalvik-transformation-using-wrong-invoke-opcode" target="_blank">see StackOverflow question.</a>
     * </p>
     * @param response the HTTP response object to log
     */
    protected void logResponse(Response response) {
        if (!doLogging) {
            // Logging is disabled - do nothing.
            return;
        }

        Request request = response.request();

        StringBuilder log = new StringBuilder();
        log.append(
                // e.g. <-- 200 GET /ws/hw/leds/foo
                String.format("\u2190 %d %s %s",
                        response.code(), request.method(), request.urlString()));

        // Add on lines tracking any redirects that occurred.
        Response prior = response.priorResponse();
        if (prior != null) {
            // Call out that there were prior responses.
            log.append(" (prior responses below)");

            // Add a line to the log message for each prior response.
            // (For most if not all responses, there will likely be just one.)
            do {
                log.append(
                        String.format(
                                "\n... prior response: %d %s %s",
                                prior.code(), prior.request().method(), prior.request().urlString())
                );

                // If this is a redirect, log the URL we're being redirected to.
                if (prior.isRedirect()) {
                    log.append(", redirecting to ");
                    log.append(prior.header("Location", "[no Location header found?!]"));
                }

                prior = prior.priorResponse();
            } while (prior != null);
        }

        Log.i(TAG, log.toString());
    }

    /**
     * Given a relative path (such as {@code "config"}), return a {@link URL} object representing
     * the corresponding full path in the WVA's web services (such as
     * {@code "http://192.168.100.1/ws/config"}).
     *
     * <p>This method is used internally by {@link HttpClient} when constructing its HTTP requests.</p>
     *
     * @param relativePath the web services path to use
     * @return a {link URL} representing the full path to <b>relativePath</b>
     */
    public URL getAbsoluteUrl(String relativePath) {
        String scheme = (this.useSecureHttp ? "https" : "http");
        int port = (this.useSecureHttp ? httpsPort : httpPort);

        try {
            return new URL(scheme, this.hostname, port, "/ws/" + relativePath);
        } catch (MalformedURLException e) {
            String formatted = String.format(Locale.US, "%s://%s:%d/ws/%s", scheme, this.hostname, port, relativePath);
            Log.wtf(TAG, "Malformed URL: " + formatted);
            throw new AssertionError("Malformed URL: " + formatted);
        }
    }

    /**
     * Sets the basic authentication parameters to be added to every HTTP request made by this client.
     *
     * @param username the username for authentication
     * @param password the password for authentication
     */
    public void useBasicAuth(String username, String password) {
        credentials = Credentials.basic(username, password);
    }

    /**
     * Clears any previously-set basic authentication for HTTP requests.
     */
    public void clearBasicAuth() {
        credentials = null;
    }

    /**
     * Sets whether this HTTP client should communicate over HTTP or HTTPS.
     * @param secure true to use HTTPS, false to use HTTP
     */
    public void useSecureHttp(boolean secure) {
        this.useSecureHttp = secure;
    }

    /**
     * Sets the port to be used when making HTTP requests.
     *
     * @param port the port to use for HTTP
     */
    public void setHttpPort(int port) {
        this.httpPort = port;
    }

    /**
     * Sets the port to be used when making HTTPS requests.
     *
     * @param port the port to use for HTTPS
     */
    public void setHttpsPort(int port) {
        this.httpsPort = port;
    }

    /**
     * Gets the underlying HTTP client used by this HttpClient instance.
     *
     * @return the underlying HTTP client
     */
    OkHttpClient getUnderlyingClient() {
        return this.client;
    }
}

