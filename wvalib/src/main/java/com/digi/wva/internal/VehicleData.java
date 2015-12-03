/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import android.util.Log;

import com.digi.wva.async.AlarmType;
import com.digi.wva.async.EventFactory;
import com.digi.wva.async.VehicleDataEvent;
import com.digi.wva.async.VehicleDataListener;
import com.digi.wva.async.VehicleDataResponse;
import com.digi.wva.async.WvaCallback;
import com.digi.wva.exc.EndpointUnknownException;
import com.digi.wva.exc.WvaHttpException;
import com.digi.wva.internal.HttpClient.ExpectEmptyCallback;
import com.digi.wva.internal.HttpClient.HttpCallback;
import com.digi.wva.util.WvaUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This object allows users to access data in the {@code vehicle/data} web services about the
 * current operation of a vehicle. The WVA allows for both direct querying of
 * the most recent vehicle data, and subscribing and/or setting
 * up alarms to receive new vehicle data through the TCP event channel.
 *
 * <p>You should not need to create an instance of this class manually.
 * Use the {@link com.digi.wva.WVA} class to manage all interactions with the WVA.</p>
 */
public class VehicleData {
    private static final String TAG = "wvalib VehicleData";
    private static final String VEHICLE_BASE = "vehicle/data/";
    private static final String SUBSCRIPTION_BASE = "subscriptions/";
    private static final String ALARM_BASE = "alarms/";
    private static final boolean BUFFER_SUBSCRIPTIONS = false;
    private static final boolean BUFFER_ALARMS = false;
    /**
     * Suffix used to create the default short names for subscriptions. Appended onto the
     * endpoint name, e.g. {@code "EngineSpeed"} becomes {@code "EngineSpeed~sub"}.
     */
    public static final String SUB_SUFFIX = "~sub";

    /**
     * Maps web service URIs to their most recent received value.
     * {@link #fetchVehicleDataEndpoints} will initialize the URI for each vehicle data
     * endpoint to an empty {@link VehicleDataResponse} object.
     */
    private final ConcurrentHashMap<String, VehicleDataResponse> dataCache;
    private Set<String> endpointNames;
    private final HttpClient httpClient;

    /**
     * Maps URIs to their listeners.
     */
    private ConcurrentHashMap<String, VehicleDataListener> listenerMap = new ConcurrentHashMap<String, VehicleDataListener>();
    private VehicleDataListener allListener;

    /**
     * Constructor.
     * @param client the HTTP client used to handle HTTP calls made
     */
    public VehicleData(HttpClient client) {
        this.dataCache = new ConcurrentHashMap<String, VehicleDataResponse>();
        this.httpClient = client;
        this.endpointNames = new HashSet<String>();
    }

    /**
     * Given an endpoint name, get the 'full' web services URI for that endpoint (e.g.
     * EngineSpeed -> vehicle/data/EngineSpeed).
     *
     * @param endpoint the endpoint name whose URI is needed
     * @return the URI corresponding to the given endpoint
     */
    private String uriFromEndpoint(String endpoint) {
        return VEHICLE_BASE + endpoint;
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#fetchVehicleDataEndpoints(WvaCallback)}.
     */
    public void fetchVehicleDataEndpoints(final WvaCallback<Set<String>> onInitialized) {
        httpClient.get(VEHICLE_BASE, new HttpCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONArray uris;
                if (!response.isNull("data")) {
                    try {
                        uris = response.getJSONArray("data");
                    } catch (JSONException e) {
                        Log.e(TAG, "fetchVehicleDataEndpoints - `data` key is not an array: " + e.getMessage());
                        onFailure(e);
                        return;
                    }
                } else {
                    Log.e(TAG, "fetchVehicleDataEndpoints - `data` key is missing or null");
                    onFailure(new IllegalArgumentException("No `data` key in fetchVehicleDataEndpoints HTTP response."));
                    return;
                }

                int l  = uris.length();
                String uri;

                // Initialize endpoint names cache
                for (int i = 0; i < l; i++) {
                    try {
                        uri = uris.getString(i);
                    } catch (JSONException e) {
                        Log.w(TAG, "couldn't initialize vehicle data correctly on URI index " + i);
                        continue;
                    }

                    String endpoint = WvaUtil.getEndpointFromUri(uri);
                    if (endpoint == null) {
                        Log.w(TAG, "Got vehicle data URI but no endpoint: " + uri);
                    } else {
                        endpointNames.add(endpoint);
                    }
                }

                // Trigger callback
                if (onInitialized != null) {
                    onInitialized.onResponse(null, new HashSet<String>(endpointNames));
                }
            }

            @Override
            public void onFailure(Throwable error) {
                Log.w(TAG, "fetchVehicleDataEndpoints() failed", error);
                if (onInitialized != null) {
                    onInitialized.onResponse(error, new HashSet<String>());
                }
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#getCachedVehicleDataEndpoints()}
     */
    public Set<String> getCachedVehicleDataEndpoints() {
        return new HashSet<String>(this.endpointNames);
    }

    /**
     * Triggers the {@link #setVehicleDataListener(String, VehicleDataListener) listener associated}
     * with <b>e</b>'s {@link VehicleDataEvent#getUri()} URI} (if there are any), and then
     * the {@link #setVehicleDataListener(VehicleDataListener) "catch-all" listener} (if any).
     *
     * @param e the event to be used to trigger the aforementioned listeners
     */
    public void notifyListeners(VehicleDataEvent e) {
        String uri = e.getUri();

        // Call the URI-specific listener, if any
        if (listenerMap.containsKey(uri)) {
            listenerMap.get(uri).onEvent(e);
        }

        if (allListener != null) {
            allListener.onEvent(e);
        }
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#removeAllVehicleDataListeners()}
     */
    public void removeAllListeners() {
        listenerMap.clear();
        allListener = null;
    }

    /**
     * Updates the cached value of an endpoint, and also calls
     * {@link #notifyListeners(VehicleDataEvent)} with <b>e</b>.
     *
     * It is convenient for testing when no WVA and/or TCP connection is
     * available.
     *
     * @param e a VehicleDataEvent object
     */
    public void updateCachedVehicleData(VehicleDataEvent e) {
        if (e == null) {
            Log.w(TAG, "updateCachedVehicleData received null event");
            return;
        }

        dataCache.put(e.getUri(), e.getResponse());
        notifyListeners(e);
    }

    /**
     * Updates the cached value of an endpoint, and also calls
     * {@link #notifyListeners(VehicleDataEvent)} with a new Event constructed
     * from <b>uri</b> and <b>response</b>.
     *
     * It is convenient for testing when no WVA and/or TCP connection is
     * available.
     *
     * @param uri
     *          the web service URI whose data is being updated
     * @param response
     *          the data from the WVA
     */
    public void updateCachedVehicleData(String uri, VehicleDataResponse response) {
        String endpoint = WvaUtil.getEndpointFromUri(uri);
        updateCachedVehicleData(new VehicleDataEvent(EventFactory.Type.SUBSCRIPTION, uri, endpoint, null, endpoint + SUB_SUFFIX, response));
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#setVehicleDataListener(VehicleDataListener)}
     */
    public void setVehicleDataListener(VehicleDataListener listener) {
        this.allListener = listener;
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#removeVehicleDataListener()}
     */
    public void removeVehicleDataListener() {
        this.allListener = null;
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#setVehicleDataListener(String, VehicleDataListener)}
     */
    public void setVehicleDataListener(String endpoint, VehicleDataListener listener) {
        listenerMap.put(uriFromEndpoint(endpoint), listener);
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#removeVehicleDataListener(String)}
     */
    public void removeVehicleDataListener(String endpoint) {
        listenerMap.remove(uriFromEndpoint(endpoint));
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#setUriListener(String, VehicleDataListener)}
     */
    public void setUriListener(String uri, VehicleDataListener listener) {
        if (uri.startsWith(VEHICLE_BASE)) {
            // Log a warning about using this method to manipulate vehicle/data/ listeners.
            String message = String.format(
                    "setUriListener was called with uri \"%s\". This function is intended to be used " +
                            "with non-vehicle-data URIs like vehicle/ignition. Use setVehicleDataListener instead.",
                    uri);
            Log.w(TAG, message);
        }
        listenerMap.put(uri, listener);
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#removeUriListener(String)}
     */
    public void removeUriListener(String uri) {
        if (uri.startsWith(VEHICLE_BASE)) {
            // Log a warning about using this method to manipulate vehicle/data/ listeners.
            String message = String.format(
                    "removeUriListener was called with uri \"%s\". This function is intended to be " +
                            "used with non-vehicle-data URIs like vehicle/ignition. Use removeVehicleDataListener instead.",
                    uri);
            Log.w(TAG, message);
        }
        listenerMap.remove(uri);
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#getCachedVehicleData(String)}
     */
    public VehicleDataResponse getCachedVehicleData(String endpoint) {
        return dataCache.get(uriFromEndpoint(endpoint));
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#getCachedDataAtUri(String)}
     */
    public VehicleDataResponse getCachedDataAtUri(String uri) {
        return dataCache.get(uri);
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#fetchVehicleData(String, WvaCallback)}
     */
    public void fetchVehicleData(final String endpoint, final WvaCallback<VehicleDataResponse> cb) {
        final String uri = uriFromEndpoint(endpoint);
        httpClient.get(uri, new HttpCallback() {
            @Override
            public void onSuccess(JSONObject jObj) {
                JSONObject valTimeObj;
                try {
                    valTimeObj = jObj.getJSONObject(endpoint);
                    VehicleDataResponse response = new VehicleDataResponse(valTimeObj);

                    // Update the cache
                    dataCache.put(uri, response);

                    if (cb != null) {
                        cb.onResponse(null, response);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Data fetched from " + endpoint + " unreadable");
                    cb.onResponse(e, null);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                if (error instanceof WvaHttpException.WvaHttpNotFound) {
                    Log.e(TAG, "Unable to fetch vehicle data - endpoint " + endpoint + " does not exist.");
                    cb.onResponse(new EndpointUnknownException("No vehicle data endpoint " + endpoint), null);
                    return;
                }

                Log.e(TAG, "Unable to fetch vehicle data", error);
                cb.onResponse(error, null);
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#subscribeToVehicleData(String, int, WvaCallback)}
     */
    public void subscribe(final String endpoint, int seconds, final WvaCallback<Void> cb) throws JSONException {
        JSONObject parameters = new JSONObject();
        JSONObject subscription = new JSONObject();
        parameters.put("interval", seconds);
        parameters.put("uri", uriFromEndpoint(endpoint));
        parameters.put("buffer",  BUFFER_SUBSCRIPTIONS ? "queue" : "discard");
        subscription.put("subscription",  parameters);

        // The url at which the subscription will be available
        final String shortName = endpoint + SUB_SUFFIX;

        httpClient.put(SUBSCRIPTION_BASE + shortName, subscription, new ExpectEmptyCallback() {
            @Override
            public void onSuccess() {
                if (cb != null) {
                    cb.onResponse(null, null);
                }
            }

            @Override
            public void onBodyNotEmpty(String body) {
                Log.e(TAG, "subscribe got unexpected response body content:\n" + body);
                onFailure(new Exception("Unexpected response body: " + body));
            }

            @Override
            public void onFailure(Throwable error) {
                Log.e(TAG, "Failed to subscribe to " + endpoint);
                if (cb != null) {
                    if (error instanceof WvaHttpException.WvaHttpNotFound) {
                        error = new EndpointUnknownException("Vehicle data endpoint " + endpoint + " does not exist.");
                    }
                    cb.onResponse(error, null);
                }
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#subscribeToUri(String, int, WvaCallback)}
     * @since 2.1.0
     */
    public void subscribeToUri(final String uri, int seconds, final WvaCallback<Void> callback) throws JSONException {
        if (uri.startsWith(VEHICLE_BASE)) {
            // Log a warning about using this method to manipulate data subscriptions.
            String message = String.format(
                    "subscribeToUri was called with uri \"%s\". This function is intended to be " +
                            "used with non-vehicle-data URIs like vehicle/ignition. Use WVA#subscribeToVehicleData instead.",
                    uri);
            Log.w(TAG, message);
        }

        JSONObject parameters = new JSONObject();
        JSONObject subscription = new JSONObject();
        parameters.put("interval", seconds);
        parameters.put("uri", uri);
        parameters.put("buffer",  BUFFER_SUBSCRIPTIONS ? "queue" : "discard");
        subscription.put("subscription",  parameters);

        // The url at which the subscription will be available
        final String shortName = WvaUtil.getEscapedStringFromUri(uri) + SUB_SUFFIX;

        httpClient.put(SUBSCRIPTION_BASE + shortName, subscription, new ExpectEmptyCallback() {
            @Override
            public void onSuccess() {
                if (callback != null) {
                    callback.onResponse(null, null);
                }
            }

            @Override
            public void onBodyNotEmpty(String body) {
                Log.e(TAG, "subscribe got unexpected response body content:\n" + body);
                onFailure(new Exception("Unexpected response body: " + body));
            }

            @Override
            public void onFailure(Throwable error) {
                Log.e(TAG, "Failed to subscribe to " + uri);
                if (callback != null) {
                    if (error instanceof WvaHttpException.WvaHttpNotFound) {
                        error = new EndpointUnknownException("URI " + uri + " does not exist.");
                    }
                    callback.onResponse(error, null);
                }
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#unsubscribeFromVehicleData(String, WvaCallback)}
     */
    public void unsubscribe(final String endpoint, final WvaCallback<Void> cb) {
        final String shortName = endpoint + SUB_SUFFIX;

        httpClient.delete(SUBSCRIPTION_BASE + shortName, new ExpectEmptyCallback() {
            @Override
            public void onFailure(Throwable error) {
                if (error instanceof WvaHttpException.WvaHttpNotFound) {
                    // The error was 404 not found. This is fine, since it means
                    // there were no subscriptions for this endpoint to begin with.
                    // We don't need to fill the logs with the stack trace from this error.
                    Log.e(TAG, "Unable to unsubscribe from " + endpoint + ": no subscription exists.");
                } else {
                    Log.e(TAG, "Unable to unsubscribe from " + endpoint, error);
                }
                if (cb != null) {
                    cb.onResponse(error, null);
                }
            }

            @Override
            public void onBodyNotEmpty(String body) {
                Log.e(TAG, "unsubscribe got unexpected response body content:\n" + body);
                onFailure(new Exception("Unexpected response body: " + body));
            }

            @Override
            public void onSuccess() {
                if (cb != null) {
                    cb.onResponse(null, null);
                }
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#unsubscribeFromUri(String, WvaCallback)}
     */
    public void unsubscribeFromUri(final String uri, final WvaCallback<Void> cb) {
        if (uri.startsWith(VEHICLE_BASE)) {
            // Log a warning about using this method to manipulate data subscriptions.
            String message = String.format(
                    "unsubscribeFromUri was called with uri \"%s\". This function is intended to be " +
                            "used with non-vehicle-data URIs like vehicle/ignition. Use WVA#unsubscribeFromVehicleData instead.",
                    uri);
            Log.w(TAG, message);
        }

        final String shortName = WvaUtil.getEscapedStringFromUri(uri) + SUB_SUFFIX;

        httpClient.delete(SUBSCRIPTION_BASE + shortName, new ExpectEmptyCallback() {
            @Override
            public void onFailure(Throwable error) {
                if (error instanceof WvaHttpException.WvaHttpNotFound) {
                    // The error was 404 not found. This is fine, since it means
                    // there were no subscriptions for this endpoint to begin with.
                    // We don't need to fill the logs with the stack trace from this error.
                    Log.e(TAG, "Unable to unsubscribe from " + uri + ": no subscription exists.");
                } else {
                    Log.e(TAG, "Unable to unsubscribe from " + uri, error);
                }
                if (cb != null) {
                    cb.onResponse(error, null);
                }
            }

            @Override
            public void onBodyNotEmpty(String body) {
                Log.e(TAG, "unsubscribeFromUri got unexpected response body content:\n" + body);
                onFailure(new Exception("Unexpected response body: " + body));
            }

            @Override
            public void onSuccess() {
                if (cb != null) {
                    cb.onResponse(null, null);
                }
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#createVehicleDataAlarm(String, AlarmType, float, int, WvaCallback)}
     */
    public void createAlarm(final String endpoint, AlarmType type, double threshold, int seconds, final WvaCallback<Void> cb) throws JSONException {
        JSONObject parameters = new JSONObject();
        JSONObject alarm = new JSONObject();
        parameters.put("interval", seconds);
        parameters.put("uri", uriFromEndpoint(endpoint));
        parameters.put("type", AlarmType.makeString(type));
        parameters.put("threshold", threshold);
        parameters.put("buffer", BUFFER_ALARMS ? "queue" : "discard");
        alarm.put("alarm",  parameters);

        // The resource at which the alarm will be available
        final String shortname = endpoint + "~" + AlarmType.makeString(type);

        httpClient.put(ALARM_BASE + shortname, alarm, new ExpectEmptyCallback() {
            @Override
            public void onSuccess() {
                if (cb != null) {
                    cb.onResponse(null, null);
                }
            }

            @Override
            public void onBodyNotEmpty(String body) {
                Log.e(TAG, "createAlarm got unexpected response body content:\n" + body);
                onFailure(new Exception("Unexpected response body: " + body));
            }

            @Override
            public void onFailure(Throwable error) {
                Log.e(TAG, "Failed to create alarm " + shortname, error);
                if (cb != null) {
                    if (error instanceof WvaHttpException.WvaHttpNotFound) {
                        error = new EndpointUnknownException("Vehicle data endpoint " + endpoint + " does not exist.");
                    }
                    cb.onResponse(error, null);
                }
            }
        });
    }

    /**
     * Underlying implementation of
     * {@link com.digi.wva.WVA#createUriAlarm(String, AlarmType, float, int, WvaCallback)}
     */
    public void createUriAlarm(final String uri, final AlarmType type, final float threshold,
                               final int seconds, final WvaCallback<Void> callback) throws JSONException {
        if (uri.startsWith(VEHICLE_BASE)) {
            // Log a warning about using this method to manipulate data alarms.
            String message = String.format(
                    "createUriAlarm was called with uri \"%s\". This function is intended to be " +
                            "used with non-vehicle-data URIs like vehicle/ignition. Use WVA#createVehicleDataAlarm instead.",
                    uri);
            Log.w(TAG, message);
        }

        JSONObject parameters = new JSONObject();
        JSONObject alarm = new JSONObject();
        parameters.put("interval", seconds);
        parameters.put("uri", uri);
        parameters.put("type", AlarmType.makeString(type));
        parameters.put("threshold", threshold);
        parameters.put("buffer", BUFFER_ALARMS ? "queue" : "discard");
        alarm.put("alarm",  parameters);

        // The resource at which the alarm will be available
        final String shortname = WvaUtil.getEscapedStringFromUri(uri) + "~" + AlarmType.makeString(type);

        httpClient.put(ALARM_BASE + shortname, alarm, new ExpectEmptyCallback() {
            @Override
            public void onSuccess() {
                if (callback != null) {
                    callback.onResponse(null, null);
                }
            }

            @Override
            public void onBodyNotEmpty(String body) {
                Log.e(TAG, "createAlarm got unexpected response body content:\n" + body);
                onFailure(new Exception("Unexpected response body: " + body));
            }

            @Override
            public void onFailure(Throwable error) {
                Log.e(TAG, "Failed to create alarm " + shortname, error);
                if (callback != null) {
                    if (error instanceof WvaHttpException.WvaHttpNotFound) {
                        error = new EndpointUnknownException("URI " + uri + " does not exist.");
                    }
                    callback.onResponse(error, null);
                }
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#deleteVehicleDataAlarm(String, AlarmType, WvaCallback)}
     */
    public void deleteAlarm(final String endpoint, final AlarmType type, final WvaCallback<Void> cb) {

        final String shortname = endpoint + "~" + AlarmType.makeString(type);

        httpClient.delete(ALARM_BASE + shortname, new ExpectEmptyCallback() {
            @Override
            public void onFailure(Throwable error) {
                if (error instanceof WvaHttpException.WvaHttpNotFound) {
                    // The error was 404 not found. This is fine, since it means
                    // there were no alarms for this endpoint and type to begin with.
                    // We don't need to fill the logs with the stack trace from this error.
                    Log.e(TAG, "Unable to remove alarm from " + endpoint + ": no alarm exists.");
                } else {
                    Log.e(TAG, "Unable to remove alarm from " + endpoint, error);
                }
                if (cb != null) {
                    cb.onResponse(error, null);
                }
            }

            @Override
            public void onBodyNotEmpty(String body) {
                Log.e(TAG, "deleteAlarm got unexpected response body content:\n" + body);
                onFailure(new Exception("Unexpected response body: " + body));
            }

            @Override
            public void onSuccess() {
                if (cb != null) {
                    cb.onResponse(null, null);
                }
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#deleteUriAlarm(String, AlarmType, WvaCallback)}
     */
    public void deleteUriAlarm(final String uri, final AlarmType type, final WvaCallback<Void> callback) {
        if (uri.startsWith(VEHICLE_BASE)) {
            // Log a warning about using this method to manipulate data alarms.
            String message = String.format(
                    "deleteUriAlarm was called with uri \"%s\". This function is intended to be " +
                            "used with non-vehicle-data URIs like vehicle/ignition. Use WVA#deleteVehicleDataAlarm instead.",
                    uri);
            Log.w(TAG, message);
        }

        final String shortname = WvaUtil.getEscapedStringFromUri(uri) + "~" + AlarmType.makeString(type);

        httpClient.delete(ALARM_BASE + shortname, new ExpectEmptyCallback() {
            @Override
            public void onFailure(Throwable error) {
                if (error instanceof WvaHttpException.WvaHttpNotFound) {
                    // The error was 404 not found. This is fine, since it means
                    // there were no alarms for this endpoint and type to begin with.
                    // We don't need to fill the logs with the stack trace from this error.
                    Log.e(TAG, "Unable to remove alarm for " + uri + ": no alarm exists.");
                } else {
                    Log.e(TAG, "Unable to remove alarm for " + uri, error);
                }
                if (callback != null) {
                    callback.onResponse(error, null);
                }
            }

            @Override
            public void onBodyNotEmpty(String body) {
                Log.e(TAG, "deleteUriAlarm got unexpected response body content:\n" + body);
                onFailure(new Exception("Unexpected response body: " + body));
            }

            @Override
            public void onSuccess() {
                if (callback != null) {
                    callback.onResponse(null, null);
                }
            }
        });
    }
}
