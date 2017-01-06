/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.widget.TextView;

import com.digi.wva.async.AlarmType;
import com.digi.wva.async.EventChannelStateListener;
import com.digi.wva.async.FaultCodeCommon;
import com.digi.wva.async.FaultCodeListener;
import com.digi.wva.async.FaultCodeResponse;
import com.digi.wva.async.VehicleDataListener;
import com.digi.wva.async.VehicleDataResponse;
import com.digi.wva.async.WvaCallback;
import com.digi.wva.internal.Ecus;
import com.digi.wva.internal.EventChannel;
import com.digi.wva.internal.EventDispatcher;
import com.digi.wva.internal.FaultCodes;
import com.digi.wva.internal.Hardware;
import com.digi.wva.internal.HttpClient;
import com.digi.wva.internal.HttpClient.ExpectEmptyCallback;
import com.digi.wva.internal.VehicleData;
import com.digi.wva.util.WvaUtil;

import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.Inet4Address;
import java.util.Set;

/**
 * Configures and manages interactions with the WVA device. Most applications can use just
 * a single WVA object to manage interactions with the WVA.
 *
 * <h2>General Usage</h2>
 *
 * <p>
 *     To start the TCP event channel connection, use
 *     {@link #connectEventChannel(int, com.digi.wva.async.EventChannelStateListener)} or
 *     {@link #connectEventChannel(int)}.  This ensures that asynchronous events are delivered to
 *     your application.
 * </p>
 *
 * <p>
 *     If a method takes a {@link com.digi.wva.async.WvaCallback WvaCallback} argument, then it
 *     performs some HTTP operation asynchronously, and the given callback will be invoked upon
 *     completion or failure of the request. If a method does not take a {@code WvaCallback}
 *     as an argument, then it either does no network operations (such as
 *     {@link #useBasicAuth(String, String) useBasicAuth}) or interacts with the TCP event channel
 *     and uses another feedback mechanism
 *     (such as {@link #connectEventChannel(int, EventChannelStateListener) connectEventChannel}
 *     and the {@code EventChannelStateListener}).
 * </p>
 *
 * <p>
 *     Digi's WVA Android library is designed to allow fine-grained management of asynchronous
 *     event listeners.
 *     This gives you the flexibility to choose the best way to incorporate your event handling
 *     into the Android
 *     <a
 *       href="http://developer.android.com/guide/components/activities.html#Lifecycle"
 *       target="_blank">activity lifecycle
 *     </a> whether your events are handled for the life of the entire application or solely
 *     during a single {@code Activity}.
 * </p>
 *
 * <p>
 *     Suppose you need your application to update a line graph with new EngineSpeed values while
 *     the user is inside the app, but while the app is in the background, you only need to write
 *     those values to a database.
 *     To accomplish this, your application could use two listeners:
 *     <ol>
 *         <li>A listener managed by the {@code Activity} displaying the graph using the
 *         appropriate set/remove calls for that event.  The events from
 *         this listener run on the UI thread. (default Listener behavior).</li>
 *         <li>Another listener, active for the life of the entire application, logging to the
 *         database in the background.  This Listener would override {@link
 *         VehicleDataListener#runsOnUiThread() runsOnUIThread} to return false</li>
 *     </ol>
 * </p>
 *
 * <p>
 *     {@link #setVehicleDataListener(String, VehicleDataListener) Vehicle data listeners},
 *     {@link
 *       #setFaultCodeListener(com.digi.wva.async.FaultCodeCommon.Bus,
 *          com.digi.wva.async.FaultCodeCommon.FaultCodeType,
 *          String,
 *          com.digi.wva.async.FaultCodeListener)
 *        fault code listeners},
 *     and the {@link #setEventChannelStateListener(com.digi.wva.async.EventChannelStateListener) event channel state listener}
 *     can all be managed in this way.
 *     {@link com.digi.wva.async.WvaCallback WvaCallbacks} also support the
 *     {@link WvaCallback#runsOnUiThread() runsOnUiThread} method.
 * </p>
 *
 * <h4>Propagation of events</h4>
 * <p>
 *     For both fault codes and vehicle data, there are two methods for setting listeners:
 *     {@link #setVehicleDataListener(VehicleDataListener)} and {@link #setVehicleDataListener(String, VehicleDataListener)}
 *     for vehicle data, and {@link #setFaultCodeListener(FaultCodeListener) similarly-named}
 *     {@link
 *       #setFaultCodeListener(
 *           com.digi.wva.async.FaultCodeCommon.Bus,
 *           com.digi.wva.async.FaultCodeCommon.FaultCodeType, String,
 *           com.digi.wva.async.FaultCodeListener)
 *         methods}
 *     for fault codes. The former (taking in only a listener) will set the listener invoked on
 *     <i>all</i> events generated by the library, while the latter will set an additional listener
 *     to be invoked only on more specific events.
 * </p>
 *
 * <p>
 *     Imagine, for example, you wish to build an application which logs subscription vehicle
 *     data to the application logs, but also updates the UI to display the most recent
 *     EngineSpeed value. This can be accomplished with code similar to the following:
 *
 *     <pre>
 *         WVA wva = new WVA("192.168.100.1");
 *         int event_channel_port = 5000;
 *
 *         final TextView engineSpeed = (TextView) findViewById(R.id.engineSpeed);
 *
 *         // Set up non-UI thread logging listener (for all events)
 *         wva.setVehicleDataListener(new VehicleDataListener() {
 *             {@literal @}Override
 *             public boolean runsOnUiThread() {
 *                 return false;
 *             }
 *
 *             {@literal @}Override
 *             public void onEvent(VehicleDataEvent e) {
 *                 Log.i("My App", "New vehicle data: " + e.getEndpoint() + " = " + e.getResponse().getValue());
 *             }
 *         });
 *
 *         // Set up UI thread listener to update TextView for EngineSpeed
 *         wva.setVehicleDataListener("EngineSpeed", new VehicleDataListener() {
 *             {@literal @}Override
 *             public void onEvent(VehicleDataEvent e) {
 *                 engineSpeed.setText(Double.toString(e.getResponse().getValue()));
 *             }
 *         });
 *
 *         // Subscribe to EngineSpeed
 *         wva.subscribeToVehicleData("EngineSpeed", 10, new WvaCallback&lt;Void&gt;() {
 *             // ...
 *         });
 *
 *         // Connect to the event channel
 *         wva.connectEventChannel(event_channel_port);
 *     </pre>
 *
 *     Note that this {@code EngineSpeed} listener is something you would want to control throughout
 *     the
 *     activity's lifecycle, as explained
 *     above. (If your {@code WVA} object is defined globally to your application,
 *     you can set the
 *     global listener once, and manage the {@code EngineSpeed} listener when necessary.)
 * </p>
 */
public class WVA {
    private static final String TAG = "com.digi.wva.WVA";
    private String hostname;
    private VehicleData vehicleData;
    private Hardware hardware;
    private Ecus ecus;
    private FaultCodes faultCodes;

    private HttpClient httpClient;
    private EventChannel eventChannel;
    private EventDispatcher eventDispatcher;
    private EventChannelStateListener eventChannelStateListener;

    // Handler object that's attached to the UI thread.
    private final Handler uiThreadHandler = new Handler(Looper.getMainLooper());

    private WVA() {

    }

    /**
     * WVA Object Factory. Useful for unit-testing, or if you plan on subclassing
     * any of the sub-components of the WVA object.
     *
     * @return the WVA
     */
    static WVA getDevice(String hostname, HttpClient client, VehicleData vehicleData,
                                   Ecus ecus, Hardware hw, FaultCodes fc) {
        WVA dev = new WVA();
        dev.hostname = hostname;
        dev.httpClient = ((client != null)  ? client  : new HttpClient(hostname));
        dev.vehicleData = ((vehicleData != null) ? vehicleData : new VehicleData(client));
        dev.ecus       = ((ecus != null)     ? ecus : new Ecus(client));
        dev.hardware   = ((hw != null)      ? hw      : new Hardware(client));
        dev.faultCodes = ((fc != null)      ? fc      : new FaultCodes(client));

        return dev;
    }

    /**
     * WVA Constructor.
     *
     * <p><b>Note:</b> By default, interactions with the WVA over HTTP will be done
     * using HTTP (not HTTPS), and without any basic authentication. The methods
     * {@link #useBasicAuth(String, String)} and {@link #useSecureHttp(boolean)}
     * can be used to configure this object to use the correct settings.</p>
     *
     * @param hostname the hostname/IP address of the WVA device
     */
    public WVA(String hostname) {
        this.hostname = hostname;
        this.httpClient = new HttpClient(hostname);
        this.vehicleData = new VehicleData(httpClient);
        this.ecus = new Ecus(httpClient);
        this.hardware = new Hardware(httpClient);
        this.faultCodes = new FaultCodes(httpClient);
    }

    /**
     * WVA Constructor. Added for convenience when using Inet4Address in an application.
     *
     * @since 2.1.0
     *
     * @see #WVA(String)
     */
    public WVA(Inet4Address host) {
        // Call into the other constructor with the string representation of the given inet address.
        this(host.getHostAddress());
    }

    /**
     * @since 2.1.0
     * @return the hostname this WVA object is associated with
     */
    public String getHostName() {
        return this.hostname;
    }

    /**
     * Sets the basic authentication credentials (username and password) to be used
     * when interacting with the WVA over HTTP or HTTPS.
     *
     * <p>Basic authentication can be removed using {@link #clearBasicAuth()}.</p>
     *
     * @param username the username
     * @param password the password
     * @return this WVA object, for chaining purposes
     */
    public WVA useBasicAuth(String username, String password) {
        this.httpClient.useBasicAuth(username, password);
        return this;
    }

    /**
     * Clears any previously-set basic authentication for HTTP or HTTPS requests.
     *
     * <p>Basic authentication can be set using {@link #useBasicAuth(String, String)}.</p>
     *
     * @return this WVA object, for chaining purposes
     */
    public WVA clearBasicAuth() {
        this.httpClient.clearBasicAuth();
        return this;
    }

    /**
     * Sets whether HTTP communication with the WVA should be done through HTTP or HTTPS.
     *
     * @see #setHttpPort(int)
     * @see #setHttpsPort(int)
     *
     * @param secure true to use HTTPS, false to use HTTP
     * @return this WVA object, for chaining purposes
     */
    public WVA useSecureHttp(boolean secure) {
        this.httpClient.useSecureHttp(secure);
        return this;
    }

    /**
     * Sets the port on the physical WVA hardware that this WVA instance will use for
     * HTTP requests.
     *
     * <p>If this value is set to {@code -1}, then the default HTTP port (80) will be used.</p>
     *
     * @param port the port to use
     * @return this WVA object, for chaining purposes
     * @see #setHttpsPort(int)
     * @see #useSecureHttp(boolean)
     */
    public WVA setHttpPort(int port) {
        this.httpClient.setHttpPort(port);
        return this;
    }

    /**
     * Sets the port on the physical WVA hardware that this WVA instance will use for
     * HTTPS requests.
     *
     * <p>If this value is set to {@code -1}, then the default HTTPS port (443) will be used.</p>
     *
     * @param port the port to use
     * @return this WVA object, for chaining purposes
     * @see #setHttpPort(int)
     * @see #useSecureHttp(boolean)
     */
    public WVA setHttpsPort(int port) {
        this.httpClient.setHttpsPort(port);
        return this;
    }

    /**
     * Sets a flag controlling whether all HTTP requests and responses should be logged.
     *
     * <p>The logging that occurs in these cases simply tracks methods, URLs and response codes.
     * Some example log messages can be seen here:
     *  <ul>
     *      <li><code>\u2192 GET http://192.168.0.3/ws/vehicle/data/EngineSpeed</code></li>
     *      <li><code>\u2190 200 GET http://192.168.0.3/ws/vehicle/data/EngineSpeed</code></li>
     *      <li>
     *          <pre>\u2190 200 GET https://192.168.0.3/ws/vehicle/data (prior responses below)</pre>
     *          <pre>... prior response: 301 GET http://192.168.0.3:80/ws/vehicle/data, redirecting to https://192.168.0.3/ws/vehicle/data</pre>
     *      </li>
     *  </ul>
     * </p>
     *
     * @since 2.1.0
     * @param enabled true to enable HTTP logging, false to disable HTTP logging (the default value)
     * @return this WVA object, for chaining purposes
     */
    public WVA setHttpLoggingEnabled(boolean enabled) {
        this.httpClient.setLoggingEnabled(enabled);
        return this;
    }

    /**
     * @since 2.1.0
     * @return true if HTTP logging is enabled, false if HTTP logging is disabled
     */
    public boolean getHttpLoggingEnabled() {
        return this.httpClient.getLoggingEnabled();
    }

    /**
     * Sets the listener to be used to receive information about the connection state of the event
     * channel.
     *
     * <p>If the event channel has been created and has not been disconnected, this method
     * will also immediately set this listener to be used there. Otherwise, it holds onto this
     * listener object, so that it can be passed into a future {@link EventChannel} instance, such as
     * when calling {@link #connectEventChannel(int)}.</p>
     *
     * @param listener the state listener to use with the event channel
     */
    public synchronized void setEventChannelStateListener(EventChannelStateListener listener) {
        this.eventChannelStateListener = listener;

        if (this.eventChannel != null) {
            this.eventChannel.setStateListener(listener);
        }
    }

    /**
      * Turns on the TCP stream which conveys subscription and alarm data.
      * Subscriptions and alarms can be created without the stream, but no
      * data will be received by the library until the event channel has been connected.
     *
     * <p>
     *     This calls {@link #disconnectEventChannel()} first, to ensure that we only have
     *     one {@link com.digi.wva.internal.EventChannel} and {@link com.digi.wva.internal.EventDispatcher} active at any one time.
     * </p>
     *
     * <p>
     *     The default listener:
     *     <ul>
     *         <li>Does nothing in
     *              {@link EventChannelStateListener#onConnected onConnected},
     *              {@link EventChannelStateListener#onError onError}, or
     *              {@link EventChannelStateListener#onFailedConnection onFailedConnection}
     *         </li>
     *         <li>
     *             Calls {@link EventChannelStateListener#reconnectAfter reconnectAfter}
     *             with 15000 (15 seconds) in {@link EventChannelStateListener#onRemoteClose onRemoteClose},
     *             so that it automatically attempts to keep a connection open
     *         </li>
     *     </ul>
     * </p>
     *
     * @param port the TCP port to connect to for the event channel
     * @param listener a listener for the connection state. If null, this method will use the most recent
     *     value passed into {@link #setEventChannelStateListener(EventChannelStateListener)},
     *     or the default listener if no previous listener has been set.
     */
    public synchronized void connectEventChannel(final int port, EventChannelStateListener listener) {
        this.disconnectEventChannel();

        if (listener == null) {
            listener = this.eventChannelStateListener;
        }

        eventChannel = new EventChannel(this, hostname, port, EventChannelStateListener.wrap(listener, this.uiThreadHandler));
        eventDispatcher = new EventDispatcher(eventChannel, this.vehicleData, this.faultCodes);
        eventChannel.start();
        eventDispatcher.start();
    }

    /**
     * Convenience method for {@link #connectEventChannel(int, EventChannelStateListener)}, passing
     * in <b>null</b> for the listener.
     */
    public void connectEventChannel(final int port) {
        connectEventChannel(port, null);
    }

    /**
     * Disconnects from the event channel conveying subscription and alarm data. This
     * does not remove the subscriptions or alarms at the device level.
     *
     * @param disallowReconnect if set to true, calls
     *                          {@link EventChannelStateListener#stopReconnects()} on the currently-set
     *                          state listener, so that if it calls {@code reconnectAfter}, it will not
     *                          actually attempt to reconnect
     */
    public synchronized void disconnectEventChannel(boolean disallowReconnect) {
        if (eventChannelStateListener != null && disallowReconnect) {
            this.eventChannelStateListener.stopReconnects();
        }

        if (this.eventChannel != null) {
            // Make sure the current event channel doesn't get triggered anymore.
            // Otherwise, if you call disconnect.. and connect.. in quick succession, you could
            // get into a situation where the original channel is not closed down
            this.eventChannel.setStateListener(new EventChannelStateListener() {
                @Override
                public void onRemoteClose(WVA device, int port) {
                    // Ignore.
                }
            });

            this.eventChannel.stopThread();
        }
        if (this.eventDispatcher != null) {
            this.eventDispatcher.stopThread();
        }

        this.eventChannel = null;
        this.eventDispatcher = null;
    }

    /**
     * Disconnects from the event channel.
     * Calls {@link #disconnectEventChannel(boolean)} with {@code false}, thus allowing future reconnects
     * to occur. If you do not wish to connect again in the future, call
     * {@link #disconnectEventChannel(boolean)} with {@code true}.
     */
    public void disconnectEventChannel() {
        this.disconnectEventChannel(false);
    }

    /**
     * Returns true if the WVA's {@link com.digi.wva.internal.EventChannel} is currently stopped, or non-existent, implying
     * that no data is coming into the system.
     *
     * @return true if the event channel has been stopped (or has not been created yet)
     */
    public synchronized boolean isEventChannelDisconnected() {
        return ((this.eventChannel == null || this.eventChannel.isStopped()));
    }

    //========================================================================//
    //                             GENERAL USE                                //
    //========================================================================//

    /**
     * Applies the given JSON object as configuration parameters on the specified configuration
     * web services path.
     *
     * <p>
     *     This method will intelligently wrap the given {@link JSONObject} inside of another object,
     *     with the key extracted from <b>configPath</b>. For example, this means that in order to
     *     change the settings under {@code canbus/1}, <b>configObject</b> should look like this:
     *
     *     <pre>
     *     {
     *         "enable": "on",
     *         "rate": 250000
     *     }
     *     </pre>
     *
     *     as opposed to this:
     *
     *     <pre>
     *     {
     *         "canbus": {
     *             "enable": "on",
     *             "rate": 250000
     *         }
     *     }
     *     </pre>
     * </p>
     *
     * <p>
     *     An example of using this API is as follows:
     *
     *     <pre>
     *     private void configureHttpsServer(final boolean enable, final int port) {
     *         JSONObject data = new JSONObject();
     *         try {
     *             data.put("enable", enable ? "on" : "off");
     *             data.put("port", port);
     *         } catch (JSONException e) {
     *             Log.e("example", "JSON exception", e);
     *             return;
     *         }
     *
     *         myWVA.configure("https", data, new WvaCallback&lt;Void&gt;() {
     *             // ...
     *         });
     *     }
     *     </pre>
     * </p>
     *
     * @param configPath the {@code ws/config/} path to configure, e.g. {@code "ws_events"} or
     *                      {@code "canbus/1"}
     * @param configObject the configuration to be applied, in JSON format
     * @param callback Executed when the HTTP response is received, or if there is an exception before
     *                  executing the HTTP request.
     */
    public void configure(final String configPath, final JSONObject configObject, final WvaCallback<Void> callback) {
        final WvaCallback<Void> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);

        final String applyPath = "config/" + configPath;

        final String configKey = WvaUtil.getConfigKeyFromUri(configPath);
        final JSONObject wrappedObject = new JSONObject();
        try {
            wrappedObject.put(configKey, configObject);
        } catch (JSONException e) {
            Log.e(TAG, "Exception wrapping configuration object with key " + configKey + " to " + applyPath, e);

            if (wrapped != null)
                wrapped.onResponse(e, null);
            return;
        }

        httpClient.put("config/" + configPath, wrappedObject, new ExpectEmptyCallback() {
            @Override
            public void onBodyNotEmpty(String body) {
                Log.w(TAG, "configure() got unexpected response body content:\n" + body);
                onFailure(new Exception("Unexpected response body: " + body));
            }

            @Override
            public void onSuccess() {
                if (wrapped != null) {
                    wrapped.onResponse(null, null);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                Log.e(TAG, String.format("configure - failure applying config to %s: %s", applyPath, error.getMessage()));

                if (wrapped != null) {
                    wrapped.onResponse(error, null);
                }
            }
        });
    }

    /**
     * Fetches the current value of the given configuration item.
     *
     * <p>
     *     This method will intelligently unwrap the configuration object from a successful response,
     *     in the same way that {@link #configure(String, JSONObject, WvaCallback) configure} wraps its
     *     JSONObject argument before sending it down via web services. For example, if a query to
     *     {@code "http"} returns
     *
     *     <pre>
     *     {
     *         "http": {
     *             "enable": "on",
     *             "port": 80
     *         }
     *     }
     *     </pre>
     *
     *     then <b>callback</b>'s {@link WvaCallback#onResponse onResponse} method will be given just
     *
     *     <pre>
     *     {
     *         "enable": "on",
     *         "port": 80
     *     }
     *     </pre>
     * </p>
     *
     * @param configPath the {@code ws/config/} path to query, e.g. {@code "ws_events"} or
     *                      {@code "canbus/1"}
     * @param callback callback to be executed once the request is completed
     */
    public void getConfiguration(final String configPath, final WvaCallback<JSONObject> callback) {
        if (callback == null) {
            // It would make no sense to have a null callback here.
            throw new NullPointerException("getConfiguration callback must not be null!");
        }

        final WvaCallback<JSONObject> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);

        final String getPath = "config/" + configPath;
        final String configKey = WvaUtil.getConfigKeyFromUri(configPath);

        httpClient.get(getPath, new HttpClient.HttpCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!response.has(configKey)) {
                    Log.e(TAG, "getConfiguration - web services response has no " + configKey + " key");
                    onFailure(new JSONException("Configuration response is missing the '" + configKey + "' key."));
                    return;
                }

                try {
                    wrapped.onResponse(null, response.getJSONObject(configKey));
                } catch (JSONException e) {
                    // We already checked that the mapping exists, so the value must not be a JSON object.
                    Log.e(TAG, "getConfiguration - " + configKey + " key does not map to an object");
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                wrapped.onResponse(error, null);
            }
        });
    }

    /**
     * Fetches the web services response to performing a GET at the given URI.
     *
     * @param wsPath the {@code ws/} path to query, e.g. {@code "vehicle/ignition"}
     * @param callback callback to be executed once the request is completed
     */
    public void uriGet(final String wsPath, final WvaCallback<JSONObject> callback) {
        if (callback == null) {
            // It would make no sense to have a null callback here.
            throw new NullPointerException("uriGet callback must not be null!");
        }

        final WvaCallback<JSONObject> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);

        httpClient.get(wsPath, new HttpClient.HttpCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                wrapped.onResponse(null, response);
            }

            @Override
            public void onFailure(Throwable error) {
                wrapped.onResponse(error, null);
            }
        });
    }

    /**
     * Perform an HTTP PUT request on the given web services path.
     *
     * @param wsPath the {@code ws/} path to PUT to, e.g. {@code "hw/reboot"}
     * @param data the JSON content to send as the body of the request. May be null.
     * @param callback callback to be executed once the request is completed
     */
    public void uriPut(final String wsPath, final JSONObject data, final WvaCallback<JSONObject> callback) {
        final WvaCallback<JSONObject> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);

        httpClient.put(wsPath, data, new HttpClient.HttpCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (wrapped != null) {
                    wrapped.onResponse(null, response);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                if (wrapped != null) {
                    wrapped.onResponse(error, null);
                }
            }
        });
    }

    /**
     * Perform an HTTP DELETE request on the given web services path.
     *
     * @param wsPath the {@code ws/} path to PUT to, e.g. {@code "files/userfs/WEB/python/file.txt"}
     * @param callback callback to be executed once the request is completed
     */
    public void uriDelete(final String wsPath, final WvaCallback<Void> callback) {
        final WvaCallback<Void> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);

        httpClient.delete(wsPath, new ExpectEmptyCallback() {
            @Override
            public void onFailure(Throwable error) {
                if (wrapped != null) {
                    wrapped.onResponse(error, null);
                }
            }

            @Override
            public void onBodyNotEmpty(String body) {
                Log.e(TAG, "uriDelete got unexpected response body content:\n" + body);
                onFailure(new Exception("Unexpected response body: " + body));
            }

            @Override
            public void onSuccess() {
                if (wrapped != null) {
                    wrapped.onResponse(null, null);
                }
            }
        });
    }

    /**
     * Attempts to determine whether the device with which we are communicating
     * is in fact a WVA. This is done by querying {@code /ws/} and comparing the list of
     * web services listed to services we know are present on a WVA.
     *
     * <p>
     *     {@code callback} {@link WvaCallback#onResponse onResponse} arguments
     *     will be as follows:
     *     <ul>
     *         <li><b>null, true</b> if the {@code /ws/} response indicates that this is
     *              in fact a WVA device
     *         </li>
     *         <li><b>null, false</b> if the {@code /ws/} response does not seem to indicate that this is a WVA device</li>
     *         <li><b>&lt;error&gt;, false</b> if an exception is encountered while parsing the response
     *             as JSON, or </li>
     *     </ul>
     * </p>
     *
     * @param callback a callback to be executed once we have decided
     * whether this is a WVA or not
     */
    public void isWVA(WvaCallback<Boolean> callback) {
        final WvaCallback<Boolean> cb = WvaCallback.wrap(callback, this.uiThreadHandler);

        httpClient.get("", new HttpClient.HttpCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray services = response.getJSONArray("ws");
                    boolean mightBeWVA = false;
                    if (services.length() < 4) {
                        Log.w(TAG, "isWVA - not enough services under /ws/");
                    } else {
                        String[] expected = new String[]{"vehicle", "subscriptions", "alarms", "hw"};
                        int found = 0;
                        for (int i = 0; i < services.length(); i++) {
                            for (String e : expected) {
                                if (e.equals(services.getString(i))) {
                                    found++;
                                    break;
                                }
                            }
                        }
                        mightBeWVA = (found == 4);
                    }

                    cb.onResponse(null, mightBeWVA);
                } catch (JSONException e) {
                    cb.onResponse(e, false);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                cb.onResponse(error, false);
            }
        });
    }

    //========================================================================//
    //                             VEHICLE DATA                               //
    //========================================================================//

    /**
     * Asynchronously queries the WVA for vehicle data endpoint names and populates the cache of
     * same.
     *
     * @see #getCachedVehicleDataEndpoints()
     *
     * @param onInitialized callback to be executed once the request has completed
     */
    public void fetchVehicleDataEndpoints(final WvaCallback<Set<String>> onInitialized) {
        vehicleData.fetchVehicleDataEndpoints(WvaCallback.wrap(onInitialized, this.uiThreadHandler));
    }

    /**
     * Asynchronously queries the WVA for the newest data at the given endpoint. If the query is
     * successful, the value will be cached and passed into the given callback.
     *
     * <p>Note that this is a relatively resource-intensive request and not preferred for regular
     * access such as polling. Instead, {@link #subscribeToVehicleData(String, int, WvaCallback) create a subscription}
     * to receive new information as it becomes available.</p>
     *
     * @see #getCachedVehicleData(String)
     *
     * @param endpoint The data endpoint to query
     * @param callback The callback to handle the response.
     */
    public void fetchVehicleData(final String endpoint, final WvaCallback<VehicleDataResponse> callback) {
        vehicleData.fetchVehicleData(endpoint, WvaCallback.wrap(callback, this.uiThreadHandler));
    }

    /**
     * Sets the {@link com.digi.wva.async.VehicleDataListener} to be invoked when
     * <i>any</i> vehicle data arrives via the event channel.
     *
     * @see #setVehicleDataListener(String, com.digi.wva.async.VehicleDataListener)
     * @see #removeVehicleDataListener()
     *
     * @param listener the listener to be invoked with all event channel vehicle data
     */
    public void setVehicleDataListener(VehicleDataListener listener) {
        if (listener == null) {
            Log.w(TAG, "Use removeVehicleDataListener() instead of setVehicleDataListener(null)");
        }
        vehicleData.setVehicleDataListener(VehicleDataListener.wrap(listener, this.uiThreadHandler));
    }

    /**
     * Removes any "catch-all" {@link com.digi.wva.async.VehicleDataListener} that has been set
     * to be invoked on new vehicle data.
     *
     * @see #setVehicleDataListener(com.digi.wva.async.VehicleDataListener)
     */
    public void removeVehicleDataListener() {
        vehicleData.removeVehicleDataListener();
    }

    /**
     * Sets the {@link com.digi.wva.async.VehicleDataListener} to be invoked when a new vehicle data
     * event pertaining to the given endpoint arrives via the event channel. These events can be
     * subscription or alarm updates.
     *
     * <p>
     *     If you have configured both a listener for a given endpoint ("EngineSpeed", for instance),
     *     and the catch-all listener
     *     (using {@link #setVehicleDataListener(com.digi.wva.async.VehicleDataListener)}), then
     *     the listener set using this method will be invoked first on new events.
     * </p>
     *
     * @see #removeVehicleDataListener(String)
     *
     * @param endpoint the endpoint with which to associate this listener
     * @param listener the listener to be invoked with matching event channel vehicle data
     *
     * @throws NullPointerException if <b>listener</b> is null.
     *                              (Use {@link #removeVehicleDataListener(String)} for that purpose)
     */
    public void setVehicleDataListener(String endpoint, VehicleDataListener listener) {
        if (listener == null) {
            Log.e(TAG, "Null listeners are not allowed in setVehicleDataListener. Use removeVehicleDataListener instead.");
            throw new NullPointerException("Null listeners are not allowed in setVehicleDataListener. Use removeVehicleDataListener instead.");
        }

        vehicleData.setVehicleDataListener(endpoint, VehicleDataListener.wrap(listener, this.uiThreadHandler));
    }

    /**
     * Removes any {@link com.digi.wva.async.VehicleDataListener} that has been set to be
     * invoked on new vehicle data pertaining to the given endpoint.
     *
     * @see #setVehicleDataListener(String, com.digi.wva.async.VehicleDataListener)
     *
     * @param endpoint the endpoint whose listener is to be removed
     */
    public void removeVehicleDataListener(String endpoint) {
        vehicleData.removeVehicleDataListener(endpoint);
    }

    /**
     * Sets the {@link com.digi.wva.async.VehicleDataListener} to be invoked when a new vehicle data
     * event pertaining to the given URI arrives via the event channel. These events can be
     * subscription or alarm updates.
     *
     * <p>
     *     If you have configured both a listener for a given URI ("vehicle/ignition", for instance),
     *     and the catch-all listener
     *     (using {@link #setVehicleDataListener(com.digi.wva.async.VehicleDataListener)}), then
     *     the listener set using this method will be invoked first on new events.
     * </p>
     *
     * <p>
     *     <strong>NOTE:</strong> While it is technically possible to use this method to register
     *     a listener for vehicle data endpoints (e.g. vehicle/data/EngineSpeed), doing so will
     *     add a warning to your application's logs, as you should register/unregister these
     *     listeners using {@link #setVehicleDataListener(String, VehicleDataListener)} and
     *     {@link #removeVehicleDataListener(String)}. This method, by contrast, is intended to
     *     be used to add listeners for URIs such as {@code "vehicle/ignition"} which do not fit
     *     into the "vehicle data" model but are subscribable anyway.
     * </p>
     *
     * @see #removeUriListener(String)
     * @since 2.1.0
     *
     * @param uri the web services URI with which to associate this listener
     * @param listener the listener to be invoked with matching event channel vehicle data
     *
     * @throws NullPointerException if <b>listener</b> is null.
     *                              (Use {@link #removeVehicleDataListener(String)} for that purpose)
     */
    public void setUriListener(String uri, VehicleDataListener listener) {
        if (listener == null) {
            Log.e(TAG, "Null listeners are not allowed in setUriListener. Use removeUriListener instead.");
            throw new NullPointerException("Null listeners are not allowed in setUriListener. Use removeUriListener instead.");
        }

        vehicleData.setUriListener(uri, VehicleDataListener.wrap(listener, this.uiThreadHandler));
    }

    /**
     * Removes any {@link com.digi.wva.async.VehicleDataListener} that has been set to be
     * invoked on new vehicle data pertaining to the given URI.
     *
     * @see #setUriListener(String, VehicleDataListener)
     * @since 2.1.0
     *
     * @param uri the web services URI whose listener is to be removed
     */
    public void removeUriListener(String uri) {
        vehicleData.removeUriListener(uri);
    }

    /**
     * Removes all listeners
     * {@link #setVehicleDataListener(String, com.digi.wva.async.VehicleDataListener) associated}
     * with any endpoint name or URI, as well as the
     * {@link #setVehicleDataListener(com.digi.wva.async.VehicleDataListener) "catch-all" listener},
     * if any.
     *
     * <p>
     *     This only removes listeners from the library's internal maps. It does not delete any
     *     subscriptions or alarms from the WVA device itself.
     * </p>
     */
    public void removeAllVehicleDataListeners() {
        vehicleData.removeAllListeners();
    }

    /**
     * Calls {@link #subscribeToVehicleData(String, int, WvaCallback)} with a null callback,
     * meaning no direct feedback as to the success or failure of the request will be available.
     */
    public void subscribeToVehicleData(final String endpoint, final int interval) {
        subscribeToVehicleData(endpoint, interval, null);
    }

    /**
     * Subscribe to the given vehicle data endpoint on the WVA.
     *
     * <p>
     *     When a subscription is created for an endpoint, that endpoint will
     *     automatically update at regular intervals. This is the preferred method
     *     of receiving vehicle data from the WVA device because it does not have to
     *     create an HTTP connection for every piece of data received.
     * </p>
     *
     * <p>
     *     Note that the WVA Android library will maintain only one subscription record
     *     for each vehicle data endpoint, meaning that if you subscribe to "EngineSpeed" once
     *     at an interval of 15 seconds, then later subscribe with an interval of 10 seconds,
     *     that original subscription record will be overwritten.
     * </p>
     *
     * <p>
     *     See {@link #setVehicleDataListener(String, VehicleDataListener)} for information on
     *     configuring a callback to be invoked each time subscription data arrives for the given
     *     endpoint.
     * </p>
     *
     * @param endpoint The type of information.
     * @param interval The interval of time between updates
     * @param callback callback to give feedback on whether the subscription call succeeds or not
     */
    public void subscribeToVehicleData(final String endpoint, final int interval, final WvaCallback<Void> callback) {
        WvaCallback<Void> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);

        try {
            vehicleData.subscribe(endpoint, interval, wrapped);
        } catch (JSONException e) {
            e.printStackTrace();
            if (wrapped != null) wrapped.onResponse(e, null);
        }
    }

    /**
     * Calls {@link #subscribeToUri(String, int, WvaCallback)} with a null callback,
     * meaning no direct feedback as to the success or failure of the request will be available.
     */
    public void subscribeToUri(final String uri, final int interval) {
        subscribeToUri(uri, interval, null);
    }

    /**
     * Subscribe to the given web services URI on the WVA.
     *
     * <p>
     *     See {@link #subscribeToVehicleData(String, int, WvaCallback)} for notes on how the WVA
     *     Android library manages subscriptions (e.g. only one subscription per URI, etc.).
     * </p>
     *
     * <p>
     *     See {@link #setUriListener(String, VehicleDataListener)} for information on
     *     configuring a callback to be invoked each time subscription data arrives for the given
     *     URI.
     * </p>
     *
     * <p>
     *     <strong>NOTE:</strong> While it is technically possible to use this method to register
     *     a subscription for vehicle data endpoints (e.g. vehicle/data/EngineSpeed), doing so will
     *     add a warning to your application's logs, as you should create/delete these
     *     listeners using {@link #subscribeToVehicleData(String, int, WvaCallback)} and
     *     {@link #unsubscribeFromVehicleData(String, WvaCallback)}. This method, by contrast, is
     *     intended to be used to subscribe to URIs such as {@code "vehicle/ignition"} which do
     *     not fit into the "vehicle data" model but are subscribable anyway.
     * </p>
     *
     * @since 2.1.0
     *
     * @param uri The web services URI to subscribe to
     * @param interval The interval of time between updates
     * @param callback callback to give feedback on whether the subscription call succeeds or not
     */
    public void subscribeToUri(final String uri, final int interval, final WvaCallback<Void> callback) {
        WvaCallback<Void> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);

        try {
            vehicleData.subscribeToUri(uri, interval, wrapped);
        } catch (JSONException e) {
            e.printStackTrace();
            if (wrapped != null) wrapped.onResponse(e, null);
        }
    }

    /**
     * Unsubscribes from the given endpoint on the WVA, by deleting the subscription record on
     * the device.
     *
     * @param endpoint The name of the data endpoint for which to unsubscribe
     * @param callback callback to give feedback on whether the unsubscribe call succeeds or not
     */
    public void unsubscribeFromVehicleData(final String endpoint, final WvaCallback<Void> callback) {
        vehicleData.unsubscribe(endpoint, WvaCallback.wrap(callback, this.uiThreadHandler));
    }

    /**
     * Calls {@link #unsubscribeFromVehicleData(String, WvaCallback)} with a null callback,
     * meaning no direct feedback as to the success or failure of the request will be available.
     */
    public void unsubscribeFromVehicleData(final String endpoint) {
        vehicleData.unsubscribe(endpoint, null);
    }

    /**
     * Unsubscribes from the given endpoint on the WVA, by deleting the subscription record on
     * the device.
     *
     * @since 2.1.0
     *
     * @param uri The web services URI on which to unsubscribe
     * @param callback callback to give feedback on whether the unsubscribe call succeeds or not
     */
    public void unsubscribeFromUri(final String uri, final WvaCallback<Void> callback) {
        vehicleData.unsubscribeFromUri(uri, WvaCallback.wrap(callback, this.uiThreadHandler));
    }

    /**
     * Calls {@link #unsubscribeFromUri(String, WvaCallback)} with a null callback,
     * meaning no direct feedback as to the success or failure of the request will be available.
     */
    public void unsubscribeFromUri(final String uri) {
        vehicleData.unsubscribeFromUri(uri, null);
    }

    /**
     * Create an alarm on the given vehicle data endpoint on the WVA. Alarms are similar to
     * subscriptions, but they do not occur at regular intervals. Instead, alarms produce data
     * when special conditions occur; see the JavaDoc for {@link AlarmType} for more information
     * about the capabilities of alarms.
     *
     * <p>
     *     Note that the WVA Android library will maintain only one alarm record for each alarm type
     *     on each vehicle data endpoint, meaning that if you create an alarm for "EngineSpeed"
     *     going above 50, then later create an alarm for "EngineSpeed" going above 30, the original
     *     alarm record (for above-50) will be overwritten.
     * </p>
     *
     * <p>
     *     See {@link #setVehicleDataListener(String, VehicleDataListener)} for information on
     *     configuring a callback to be invoked each time alarm data arrives for the given
     *     endpoint.
     * </p>
    *
    * @param endpoint The name of the data endpoint to add an alarm to
    * @param type The type of alarm to create. One endpoint can't have two
    *             alarms of the same type
    * @param threshold The threshold for the alarm. The meaning of this value depends on the alarm type
    * @param seconds The minimum number of seconds before two alarms of the same
    *             type will be generated (for instance, only send an alarm for
    *             speeding once in a five-minute period)
    * @param callback callback to give feedback on whether the alarm creation succeeds or not
    */
    public void createVehicleDataAlarm(final String endpoint, final AlarmType type, final float threshold,
                         final int seconds, final WvaCallback<Void> callback) {
        WvaCallback<Void> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);

        try {
            vehicleData.createAlarm(endpoint, type, threshold, seconds, wrapped);
        } catch(JSONException e) {
            Log.e(TAG, "Incorrect formatting in WVA.createVehicleDataAlarm", e);
            if (wrapped != null) wrapped.onResponse(e, null);
        }
    }

    /**
     * Calls {@link #createVehicleDataAlarm(String, AlarmType, float, int, WvaCallback)} with a null callback,
     * meaning no direct feedback as to the success or failure of the request will be available.
     */
    public void createVehicleDataAlarm(final String endpoint, final AlarmType type, final float threshold,
                                        final int seconds) {
        createVehicleDataAlarm(endpoint, type, threshold, seconds, null);
    }

    /**
     * Create an alarm on the given web services URI on the WVA. Alarms are similar to
     * subscriptions, but they do not occur at regular intervals. Instead, alarms produce data
     * when special conditions occur; see the JavaDoc for {@link AlarmType} for more information
     * about the capabilities of alarms.
     *
     * <p>
     *     Note that the WVA Android library will maintain only one alarm record for each alarm type
     *     on each web services URI, meaning that calling createUriAlarm with the same URI will
     *     overwrite the previous alarm configuration on the WVA.
     * </p>
     *
     * <p>
     *     <strong>NOTE:</strong> While it is technically possible to use this method to register
     *     an alarm for vehicle data (e.g. vehicle/data/EngineSpeed), doing so will
     *     add a warning to your application's logs, as you should create/delete these
     *     alarms using {@link #createVehicleDataAlarm(String, AlarmType, float, int, WvaCallback)}
     *     and {@link #deleteVehicleDataAlarm(String, AlarmType, WvaCallback)}. This method,
     *     by contrast, is intended to be used to add akarms to URIs such as {@code "vehicle/ignition"}
     *     which do not fit into the "vehicle data" model but can be used for alarms anyway.
     * </p>
     *
     * <p>
     *     See {@link #setUriListener(String, VehicleDataListener)} for information on
     *     configuring a callback to be invoked each time alarm data arrives for the given
     *     URI.
     * </p>
     *
     * @since 2.1.0
     *
     * @param uri The web services URI to add an alarm to
     * @param type The type of alarm to create.
     * @param threshold The threshold for the alarm. The meaning of this value depends on the alarm type
     * @param seconds The minimum number of seconds before two alarms of the same
     *             type will be generated (for instance, only send an alarm for
     *             speeding once in a five-minute period)
     * @param callback callback to give feedback on whether the alarm creation succeeds or not
     */
    public void createUriAlarm(final String uri, final AlarmType type, final float threshold,
                               final int seconds, final WvaCallback<Void> callback) {
        WvaCallback<Void> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);

        try {
            vehicleData.createUriAlarm(uri, type, threshold, seconds, wrapped);
        } catch (JSONException e) {
            Log.e(TAG, "Incorrect formatting in WVA.createUriAlarm", e);
            if (wrapped != null) wrapped.onResponse(e, null);
        }
    }

    /**
     * Calls {@link #createUriAlarm(String, AlarmType, float, int, WvaCallback)} with a null callback,
     * meaning no direct feedback as to the success or failure of the request will be available.
     */
    public void createUriAlarm(final String uri, final AlarmType type, final float threshold,
                               final int seconds) {
        createUriAlarm(uri, type, threshold, seconds, null);
    }

    /**
     * Removes the alarm on the given vehicle data endpoint on the WVA, by deleting
     * the alarm record on the device.
     *
     * @param endpoint The name of the data endpoint to remove an alarm from
     * @param type The type of alarm which should be removed
     * @param callback callback to give feedback on whether the alarm deletion succeeds or not
     */
    public void deleteVehicleDataAlarm(final String endpoint, final AlarmType type,  WvaCallback<Void> callback) {
        vehicleData.deleteAlarm(endpoint, type, WvaCallback.wrap(callback, this.uiThreadHandler));
    }

    /**
     * Calls {@link #deleteVehicleDataAlarm(String, AlarmType, WvaCallback)} with a null callback,
     * meaning no direct feedback as to the success or failure of the request will be available.
     */
    public void deleteVehicleDataAlarm(final String endpoint, final AlarmType type) {
        deleteVehicleDataAlarm(endpoint, type, null);
    }

    /**
     * Removes the alarm on the given web services URI on the WVA, by deleting
     * the alarm record on the device.
     *
     * @since 2.1.0
     *
     * @param uri The web services URI to remove an alarm from
     * @param type The type of alarm which should be removed
     * @param callback callback to give feedback on whether the alarm deletion succeeds or not
     */
    public void deleteUriAlarm(final String uri, final AlarmType type, WvaCallback<Void> callback) {
        vehicleData.deleteUriAlarm(uri, type, WvaCallback.wrap(callback, this.uiThreadHandler));
    }

    /**
     * Calls {@link #deleteUriAlarm(String, AlarmType, WvaCallback)} with a null callback,
     * meaning no direct feedback as to the success or failure of the request will be available.
     */
    public void deleteUriAlarm(final String uri, final AlarmType type) {
        deleteUriAlarm(uri, type, null);
    }

    /**
     * Synchronously returns the last value received by this library for a
     * given vehicle data endpoint. No networking is involved in this request.
     *
     * <p>This cached value is only updated by subscriptions, alarms, and
     * direct querying of the data through the library.</p>
     *
     * @see #subscribeToVehicleData(String, int, WvaCallback)
     * @see #createVehicleDataAlarm(String, AlarmType, float, int, WvaCallback)
     * @see #fetchVehicleData(String, WvaCallback)
     *
     * @param endpoint the name of the vehicle data endpoint to look up
     * @return most recent, cached value for the given vehicle data endpoint,
     * or null if no value is cached currently
     */
    public VehicleDataResponse getCachedVehicleData(final String endpoint) {
        return vehicleData.getCachedVehicleData(endpoint);
    }

    /**
     * Synchronously returns the last value received by this library for a given
     * URI. No network is involved in this request.
     *
     * <p>This cached value is only updated by subscriptions, alarms, and direct querying
     * of the data through the library.</p>
     *
     * @see #subscribeToUri(String, int, WvaCallback)
     * @see #createUriAlarm(String, AlarmType, float, int, WvaCallback)
     * @see #uriGet(String, WvaCallback)
     *
     * @since 2.1.0
     *
     * @param uri the web services URI to look up
     * @return most recent, cached value for the given URI, or null if no value is cached
     *          currently
     */
    public VehicleDataResponse getCachedDataAtUri(final String uri) {
        return vehicleData.getCachedDataAtUri(uri);
    }

    /**
     * Returns the library's cached list of vehicle data endpoints.
     *
     * @see #fetchVehicleDataEndpoints(WvaCallback)
     *
     * @return the cached list of vehicle data endpoints, or an empty set if no cached values are available
     */
    public Set<String> getCachedVehicleDataEndpoints() {
        return vehicleData.getCachedVehicleDataEndpoints();
    }

    //========================================================================//
    //                              FAULT CODES                               //
    //========================================================================//

    /**
     * Asynchronously queries the WVA for the most recent Diagnostic Trouble Code (DTC) report of
     * the given type
     * (active or inactive) on the given CAN bus. If the query is
     * successful, the value will be cached and passed into the given callback.
     *
     * <p>
     *     The arguments to the {@link WvaCallback#onResponse onResponse(error,
     *     response)} method of {@code callback}
     *     will be as follows:
     *     <ul>
     *         <li>
     *             <b>null, &lt;response&gt;</b> if a fault code is successfully retrieved
     *         </li>
     *         <li>
     *             <b>&lt;exception&gt;, null</b> if an exception is encountered while performing the
     *             query or processing its response
     *         </li>
     *         <li>
     *             <b>null, null</b> if the WVA responded with an HTTP 503 error, indicating that
     *             the referenced ECU is expected to be valid, but no PGN 65227 message has yet been received
     *         </li>
     *         <li>
     *             <b>NotListeningToECUException, null</b> if the WVA responded with an HTTP 404 error,
     *             generally indicating that the system is not listening for trouble codes on the
     *             referenced ECU.
     *         </li>
     *     </ul>
     * </p>
     *
     * <p>Note that this is a relatively resource-intensive request and not preferred for regular
     * access such as polling. Instead,
     * {@link #subscribeToFaultCodes(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String, int, com.digi.wva.async.WvaCallback)
     * create a subscription}
     * to receive new information as it becomes available.
     * </p>
     *
     * @param bus which CAN bus to look at
     * @param type the message type to query for (active or inactive)
     * @param ecu the ECU name/identifier whose most recent fault code (if any) is being queried
     * @param callback the callback to give feedback on whether the request succeeded, and the
     *                  retrieved value if any
     */
    public void fetchFaultCode(final FaultCodeCommon.Bus bus, final FaultCodeCommon.FaultCodeType type, final String ecu, final WvaCallback<FaultCodeResponse> callback) {
        faultCodes.fetchFaultCode(bus, type, ecu, WvaCallback.wrap(callback, this.uiThreadHandler));
    }

    /**
     * Subscribe to fault code information from the specified ECU on the WVA.
     *
     * <p>
     *     When a subscription is created for fault codes, that value will
     *     automatically update at regular intervals. This is the preferred method
     *     of periodically receiving fault code data from the WVA device because it does not have to
     *     create an HTTP connection for every piece of data received.
     * </p>
     *
     * <p>
     *     Note that the WVA Android library will maintain only one subscription record
     *     for any given bus/type/ecu combination, meaning that if you subscribe to
     *     ({@link com.digi.wva.async.FaultCodeCommon.Bus#CAN0 CAN0}, {@link com.digi.wva.async.FaultCodeCommon.FaultCodeType#ACTIVE ACTIVE}, "ecu0")
     *     with an interval of 15 seconds, and then later subscribe with an interval of 10 seconds,
     *     that original subscription record will be overwritten.
     * </p>
     *
     * <p>
     *     See {@link #setFaultCodeListener(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String,FaultCodeListener)}
     *     for information on configuring a callback to be invoked each time that matching fault
     *     code data arrives via the event channel.
     * </p>
     *
     * @param bus which CAN bus to look at
     * @param type the message type to query for (active or inactive)
     * @param ecu the ECU name/identifier whose most recent fault code (if any) is being queried
     * @param interval the subscription interval, in seconds
     * @param callback the callback to be executed when this HTTP request completes
     * @throws JSONException If an error occurs while creating the request
     */
    public void subscribeToFaultCodes(final FaultCodeCommon.Bus bus, final FaultCodeCommon.FaultCodeType type, final String ecu, final int interval, final WvaCallback<Void> callback) throws JSONException {
        faultCodes.subscribe(bus, type, ecu, interval, WvaCallback.wrap(callback, this.uiThreadHandler));
    }

    /**
     * Sets the {@link FaultCodeListener} to be invoked when <i>any</i> fault code data arrives
     * via the event channel.
     *
     * @see #setFaultCodeListener(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String, com.digi.wva.async.FaultCodeListener)
     * @see #removeFaultCodeListener()
     *
     * @param listener the listener to be invoked with all event channel fault code data
     */
    public void setFaultCodeListener(FaultCodeListener listener) {
        if (listener == null) {
            Log.w(TAG, "Use removeFaultCodeListener() instead of setFaultCodeListener(null)");
        }
        faultCodes.setFaultCodeListener(FaultCodeListener.wrap(listener, this.uiThreadHandler));
    }

    /**
     * Removes any "catch-all" {@link com.digi.wva.async.FaultCodeListener} that has been set to be
     * invoked on new fault code data.
     *
     * @see #setFaultCodeListener(com.digi.wva.async.FaultCodeListener)
     */
    public void removeFaultCodeListener() {
        faultCodes.removeFaultCodeListener();
    }

    /**
     * Sets the {@link FaultCodeListener} to be invoked when a new fault code event, matching the
     * given bus, type and ECU, arrives via the event channel.
     *
     * <p>
     *     If you have configured both a listener for a given ECU, and the catch-all listener (using
     *     {@link #setFaultCodeListener(FaultCodeListener)}), then the listener set using this method
     *     will be invoked first.
     * </p>
     *
     * @see #removeFaultCodeListener(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String)
     *
     * @param bus which CAN bus to look at
     * @param type the message type
     * @param ecu the ECU name
     *
     * @param listener the listener to be invoked with matching event channel fault code data
     *
     * @throws NullPointerException if <b>listener</b> is null.
     *  (Use {@link #removeFaultCodeListener(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String)}
     *  for that purpose)
     */
    public void setFaultCodeListener(FaultCodeCommon.Bus bus, FaultCodeCommon.FaultCodeType type, String ecu, FaultCodeListener listener) {
        if (listener == null) {
            Log.e(TAG, "Null listeners are not allowed in setFaultCodeListener. Use removeFaultCodeListener instead.");
            throw new NullPointerException("Null listeners are not allowed in setFaultCodeListener. Use removeFaultCodeListener instead.");
        }

        faultCodes.setFaultCodeListener(bus, type, ecu, FaultCodeListener.wrap(listener, this.uiThreadHandler));
    }

    /**
     * Removes any {@link com.digi.wva.async.FaultCodeListener} that has been set to be
     * invoked on new fault code data matching the given bus, type and ECU.
     *
     * @see #setFaultCodeListener(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String, com.digi.wva.async.FaultCodeListener)
     * @param bus the CAN bus
     * @param type the message type
     * @param ecu the ECU name
     */
    public void removeFaultCodeListener(FaultCodeCommon.Bus bus, FaultCodeCommon.FaultCodeType type, String ecu) {
        faultCodes.removeFaultCodeListener(bus, type, ecu);
    }

    /**
     * Removes all
     * {@link #setFaultCodeListener listeners associated} with any ECU, as well as the
     * {@link #setFaultCodeListener(com.digi.wva.async.FaultCodeListener) "catch-all" listener},
     * if any.
     *
     * <p>
     *     This only removes listeners from the library's internal maps. It does not delete any
     *     subscriptions or alarms from the WVA device itself.
     * </p>
     */
    public void removeAllFaultCodeListeners() {
        faultCodes.removeAllListeners();
    }

    /**
     * Unsubscribes from the specified fault code information on the WVA, by deleting the
     * subscription record on the device.
     *
     * @param bus the CAN bus
     * @param type the message type
     * @param ecu the ECU name
     * @param callback callback to give feedback on whether the unsubscribe call succeeds or not
     */
    public void unsubscribeFromFaultCodes(final FaultCodeCommon.Bus bus, final FaultCodeCommon.FaultCodeType type, final String ecu, final WvaCallback<Void> callback) {
        faultCodes.unsubscribe(bus, type, ecu, WvaCallback.wrap(callback, this.uiThreadHandler));
    }

    /**
     * Create an alarm for fault code data from the given ECU on the WVA. Alarms are similar to
     * subscriptions, but they do not occur at regular intervals. Instead, alarms produce data
     * when special conditions occur; see the JavaDoc for {@link AlarmType} for more information
     * about the capabilities of alarms.
     *
     * <p>"Change" ({@link AlarmType#CHANGE}) is the only reasonable alarm type
     * for fault code data. As such, this method does not take in any alarm type
     * or threshold parameters; instead, it defaults to the "Change" alarm type.</p>
     *
     * <p>
     *     Note that the WVA Android library will maintain only one alarm record for each fault code
     *     ECU (as specified by CAN bus identifier, message type, and ECU name), meaning that if
     *     you create an alarm on (CAN0, ACTIVE, "ecu0") with an interval of 30 seconds, then later
     *     create another such alarm with an interval of 60 seconds, that original alarm record
     *     will be overwritten.
     * </p>
     *
     * <p>
     *     See {@link #setFaultCodeListener(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String, com.digi.wva.async.FaultCodeListener)}
     *     for information on configuring a callback to be invoked each time
     *     alarm data arrives for the given ECU.
     * </p>
     *
     * @param bus which CAN bus to look at
     * @param type the message type to query for (active or inactive)
     * @param ecu the ECU name/identifier whose most recent fault code (if any) is being queried
     * @param seconds the minimum number of seconds before two alarms
     * will be generated for this ECU (for instance, only send an alarm for
     * ecu0 fault codes once every 300 seconds)
     * @param callback callback to give feedback on whether the alarm creation succeeds or not
     * @throws JSONException if an error occurs in generating the JSON data
     * being sent to the WVA to create the alarm
     */
    public void createFaultCodeAlarm(final FaultCodeCommon.Bus bus, final FaultCodeCommon.FaultCodeType type, final String ecu, int seconds, final WvaCallback<Void> callback) throws JSONException {
        faultCodes.createAlarm(bus, type, ecu, seconds, WvaCallback.wrap(callback, this.uiThreadHandler));
    }

    /**
     * Removes the alarm on the given fault code data on the WVA, by deleting
     * the alarm record on the device.
     *
     * @param bus the CAN bus
     * @param type the message type
     * @param ecu the ECU name
     * @param callback callback to give feedback on whether the alarm deletion succeeds or not
     */
    public void deleteFaultCodeAlarm(final FaultCodeCommon.Bus bus, final FaultCodeCommon.FaultCodeType type, final String ecu, final WvaCallback<Void> callback) {
        faultCodes.deleteAlarm(bus, type, ecu, WvaCallback.wrap(callback, this.uiThreadHandler));
    }

    /**
     * Synchronously returns the last fault code received by this library for a
     * given ECU. No networking is involved in this request.
     *
     * <p>This cached value is only updated by subscriptions, alarms, and
     * direct querying of the data through the library.</p>
     *
     * @see #subscribeToFaultCodes(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String, int, WvaCallback)
     * @see #createVehicleDataAlarm(String, AlarmType, float, int, WvaCallback)
     * @see #fetchFaultCode(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String, WvaCallback)
     *
     * @param bus which CAN bus the desired fault code was provided on
     * @param type the message type to look for (active or inactive)
     * @param ecu the name of the ECU
     * @return most recent, cached value for the given ECU (specified by bus, type, and ECU),
     *          or null if no fault code information has been cached for this ECU
     */
    public FaultCodeResponse getCachedFaultCode(final FaultCodeCommon.Bus bus, final FaultCodeCommon.FaultCodeType type, final String ecu) {
        return faultCodes.getCachedFaultCode(bus, type, ecu);
    }

    /**
     * Asynchronously queries the WVA for the list of ECUs that the system knows exist
     * on the given CAN bus, since they might be providing active or inactive DTC messages.
     *
     * <p>This will perform an HTTP GET request on, for example,
     * {@code vehicle/dtc/can0_active} if called with CAN0.
     * This method does not need to be parameterized on message type,
     * because the _active and _inactive web service URIs will return
     * the same list of ECUs.</p>
     *
     * @param bus the CAN bus whose ECU list is to be fetched
     * @param callback the callback to be executed when the list of ECUs is fetched.
     *                  The strings will be truncated to just the ECU name. For example,
     *                  if the web service response contains the value "vehicle/dtc/can0_active/ecu0"
     *                  the set will contain "ecu0"
     *
     * @throws NullPointerException if <b>callback</b> is null
     */
    public void fetchFaultCodeEcuNames(final FaultCodeCommon.Bus bus, final WvaCallback<Set<String>> callback) {
        if (callback == null) {
            throw new NullPointerException("Callback should not be null!");
        }
        faultCodes.fetchEcuNames(bus, WvaCallback.wrap(callback, this.uiThreadHandler));
    }

    //========================================================================//
    //                                  OTHER                                 //
    //========================================================================//

    /**
     * Asynchronously sends a request to the WVA to set its time to match the given value.
     *
     * <p>The WVA Android library will automatically convert the {@link DateTime} object
     * passed in here to UTC time, so that it is compatible with the WVA's web services.</p>
     *
     * @param time The time to send to the WVA device (for example, {@link DateTime#now()})
     * @param callback callback to be executed once the request has completed. The value passed in
     *                  is the value that was sent to the WVA (<b>time</b> converted to UTC)
     */
    public void setTime(final DateTime time, final WvaCallback<DateTime> callback) {
        WvaCallback<DateTime> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);
        try {
            hardware.setTime(time, wrapped);
        } catch (JSONException e) {
            if (callback != null) wrapped.onResponse(e, null);
        }
    }

    /**
     * Asynchronously queries the WVA for its current time. This is a network call, so
     * the time received by the callback could be stale by a few seconds.
     *
     * @param callback callback to be executed once the request has completed
     *
     * @throws NullPointerException if <b>callback</b> is null
     */
    public void fetchTime(final WvaCallback<DateTime> callback) {
        if (callback == null) throw new NullPointerException("Callback should not be null!");
        hardware.fetchTime(WvaCallback.wrap(callback, this.uiThreadHandler));
    }

    /**
     * Asynchronously queries the WVA for the list of addressable LEDs manageable by web services.
     * The callback provided will be passed the full set of LED names in the library's cache, after
     * successfully parsing the web services response and updating the cache.
     *
     * <p>
     *     If your code loses the string set passed into the callback, simply call
     *     {@link #getCachedLedNames()} to retrieve the cached list.
     * </p>
     *
     * @param onInitialized callback to be executed once the request has completed
     */
    public void fetchLedNames(final WvaCallback<Set<String>> onInitialized) {
        hardware.fetchLedNames(WvaCallback.wrap(onInitialized, this.uiThreadHandler));
    }

    /**
     * Returns the cached list of addressable LEDs on the WVA. This will be empty unless you
     * have called {@link #fetchLedNames(WvaCallback)} first.
     *
     * @return the cached list of button names
     */
    public Set<String> getCachedLedNames() {
        return hardware.getCachedLedNames();
    }

    /**
     * Asynchronously queries the WVA for the list of addressable buttons manageable by web services.
     * The callback provided will be passed the full set of button names in the library's cache, after
     * successfully parsing the web services response and updating the cache.
     *
     * <p>
     *     If your code loses the string set passed into the callback, simply call
     *     {@link #getCachedButtonNames()} to retrieve the cached list.
     * </p>
     *
     * @param onInitialized callback to be executed once the request has completed
     */
    public void fetchButtonNames(final WvaCallback<Set<String>> onInitialized) {
        hardware.fetchButtonNames(WvaCallback.wrap(onInitialized, this.uiThreadHandler));
    }

    /**
     * Returns the cached list of addressable buttons on the WVA. This will be empty unless you
     * have called {@link #fetchButtonNames(WvaCallback)} first.
     *
     * @return the cached list of button names
     */
    public Set<String> getCachedButtonNames() {
        return hardware.getCachedButtonNames();
    }

    /**
     * Asynchronously sends a request to the WVA to set the state of the given LED.
     *
     * @see #fetchButtonNames(WvaCallback)
     * @see #fetchLedState(String, WvaCallback)
     *
     * @param ledName The name of the LED to modify
     * @param state Whether or not the LED should be turned on (true means 'on')
     * @param callback callback to be executed once the request has completed. If the request succeeds,
     *                  <b>error</b> will be null and <b>response</b> will be the value of the
     *                  {@code state} param; otherwise, <b>response</b> will be null and <b>error</b>
     *                  will indicate the problem
     */
    public void setLedState(final String ledName, final boolean state, final WvaCallback<Boolean> callback) {
        WvaCallback<Boolean> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);

        try {
            hardware.setLedState(ledName, state, wrapped);
        } catch (JSONException e) {
            if (callback != null) wrapped.onResponse(e, null);
        }
    }

    /**
     * Asynchronously queries the WVA for the state of the given LED.
     *
     * <p><b>true</b> means the LED is on, <b>false</b> means it is off.</p>
     *
     * @see #fetchButtonNames(WvaCallback)
     *
     * @param ledName The name of the LED to query
     * @param callback Executed once the LED has been queried. True means the LED is on.
     *
     * @throws NullPointerException if <b>callback</b> is null
     */
    public void fetchLedState(final String ledName, final WvaCallback<Boolean> callback) {
        if (callback == null) throw new NullPointerException("Callback should not be null!");
        WvaCallback<Boolean> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);

        hardware.fetchLedState(ledName, wrapped);
    }

    /**
     * Asynchronously queries the WVA for the state of the given button.
     *
     * <p><b>false</b> means the button is depressed.</p>
     *
     * The sensor on the device has a margin of error, and this method is intended to be used
     * in long-press situations such as "Hold the ___ button for five seconds".
     * Furthermore, the hardware does not currently support subscriptions, so
     * the button must be polled; it is not recommended to use this method in a
     * fast loop except when the button state is actually needed.
     *
     * @param buttonName The name of the button to query
     * @param callback Executed once the button has been queried
     *
     * @throws NullPointerException if <b>callback</b> is null
     */
    public void fetchButtonState(final String buttonName, final WvaCallback<Boolean> callback) {
        if (callback == null) throw new NullPointerException("Callback should not be null!");
        WvaCallback<Boolean> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);

        hardware.fetchButtonState(buttonName, wrapped);
    }

    /**
     * Asynchronously queries the WVA for the list of addressable ECUs in the vehicle. The callback
     * provided will be passed the full set of ECU names in the library's cache, after
     * successfully parsing the web services response and updating the cache.
     *
     * <p>
     *     Engine control units (ECUs) are expected to be consistent throughout an entire
     *     trip, if not for the entire lifetime of the vehicle. As such, this method should only
     *     need to be called once per trip.
     * </p>
     *
     * <p>
     *     Each ECU in the system is referenced by a CAN bus number, and the ECU address number.
     *     A standard ECU in vehicles, for instance, is at address 0, which is defined as the
     *     primary engine. The reference designation for the engine on CAN bus 1 would be "can1ecu0".
     *     Both the CAN bus number and the ECU address number in the reference designator are
     *     expressed in decimal. ECU address numbers are generally sparse.
     * </p>
     *
     * <p>
     *     If your code loses the string set passed into the callback, simply call
     *     {@link #getCachedEcus()} to retrieve the cached list.
     * </p>
     *
     * @param onInitialized callback to be executed once the request has completed
     */
    public void fetchEcus(final WvaCallback<Set<String>> onInitialized) {
        ecus.fetchEcus(WvaCallback.wrap(onInitialized, this.uiThreadHandler));
    }

    /**
     * Returns the library's cached list of addressable ECUs in the vehicle. This will be empty
     * unless you have called {@link #fetchEcus(WvaCallback)} first.
     *
     * @return the cached list of addressable ECUs
     */
    public Set<String> getCachedEcus() {
        return ecus.getCachedEcus();
    }

    /**
     * Asynchronously queries the WVA for the list of data elements describing a specific engine control unit
     * (ECU) in the vehicle. The callback provided will be passed the set of ECU descriptors
     * in the library's cache, after successfully parsing the web services response and updating
     * the cache.
     *
     * <p>
     *     Engine control units (ECUs) are expected to be consistent throughout an entire
     *     trip, if not for the entire lifetime of the vehicle. As such, this method should only
     *     need to be called once per trip.
     * </p>
     *
     * <p>
     *     If your code loses the string set passed into the callback, simply call
     *     {@link #getCachedEcuElements(String)} to retrieve the cached list.
     * </p>
     *
     * @param ecuName the name of the ECU whose elements are to be queried
     * @param callback callback to be executed once the request is completed
     */
    public void fetchEcuElements(final String ecuName, final WvaCallback<Set<String>> callback) {
        WvaCallback<Set<String>> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);

        ecus.fetchEcuElements(ecuName, wrapped);
    }

    /**
     * Returns the library's cached list of specific ECU elements on a given ECU. This will be empty
     * unless you have called {@link #fetchEcuElements(String, WvaCallback)} first.
     *
     * @param ecuName the name of the ECU whose elements are to be looked up
     * @return the cached list of specific ECU elements
     */
    public Set<String> getCachedEcuElements(String ecuName) {
        return ecus.getCachedEcuElements(ecuName);
    }

    /**
     * Asynchronously queries the WVA for the value of a specific element describing a specific ECU.
     *
     * <p>
     *     Engine control units (ECUs) are expected to be consistent throughout an entire
     *     trip, if not for the entire lifetime of the vehicle. As such, this method should only
     *     need to be called once per trip.
     * </p>
     *
     * <p>
     *     If your code loses the string set passed into the callback, simply call
     *     {@link #getCachedEcuElementValue(String, String)} to retrieve the cached list.
     * </p>
     *
     * @param ecuName the name of the ECU whose info is being queried
     * @param element the name of the description element being queried, e.g. "VIN"
     * @param callback callback to be executed once the request is completed
     */
    public void fetchEcuElementValue(final String ecuName, final String element, final WvaCallback<String> callback) {
        WvaCallback<String> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);

        ecus.fetchEcuElementValue(ecuName, element, wrapped);
    }

    /**
     * Synchronously returns the library's last cached value of a specific element of a specific ECU
     * of the WVA. No networking is involved in this request.
     *
     * <p>
     *     This value will be null unless you have previously called
     *     {@link #fetchEcuElementValue(String, String, WvaCallback)} or
     *     {@link #fetchAllEcuElementValues(String, WvaCallback)}.
     * </p>
     *
     * @param ecuName The name of the ECU
     * @param endpoint The endpoint on that ECU to look up
     * @return The string representation of the data for the given ECU and
     *         endpoint. Depending on the endpoint, the actual data is
     *         semantically decimal, or hexadecimal, but it is received as a
     *         String for type simplicity.
     */
    public String getCachedEcuElementValue(String ecuName, String endpoint) {
        return ecus.getCachedEcuElementValue(ecuName, endpoint);
    }

    /**
     * Fetches all endpoints from a single ECU, calling the provided callback's
     * onResponse method with the data from every endpoint.
     *
     * <p>
     *     This is a relatively resource-intensive call, but it should only have to
     *     be performed once per ECU; the data is completely static and can just be
     *     accessed from the local cache.
     * </p>
     *
     * @param ecuName the ECU to query
     * @param callback an action to be performed when each individual response is received. The first
     * value of the pair will always be the name of the element which was queried; the second value will
     * be null if there was an error, or the string value if the query succeeded
     *
     * @throws IllegalStateException if {@link #getCachedEcuElements(String)} returns null or an
     * empty set (meaning that there are no cached ECU element names for this ECU)
     */
    public void fetchAllEcuElementValues(String ecuName, WvaCallback<Pair<String, String>> callback) {
        WvaCallback<Pair<String, String>> wrapped = WvaCallback.wrap(callback, this.uiThreadHandler);

        ecus.fetchAllEcuElementValues(ecuName, wrapped);
    }
}

