/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import android.util.Log;

import com.digi.wva.async.AlarmType;
import com.digi.wva.async.FaultCodeCommon;
import com.digi.wva.async.FaultCodeEvent;
import com.digi.wva.async.FaultCodeListener;
import com.digi.wva.async.FaultCodeResponse;
import com.digi.wva.async.WvaCallback;
import com.digi.wva.exc.NotListeningToECUException;
import com.digi.wva.exc.WvaHttpException;
import com.digi.wva.internal.HttpClient.ExpectEmptyCallback;
import com.digi.wva.internal.HttpClient.HttpCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This object allows users to access information about vehicle diagnostic trouble
 * codes (also known as fault codes). The WVA allows for both direct querying of
 * the most recent fault code from a particular ECU, and subscribing and/or setting
 * up alarms to receive new fault code information through the TCP event channel. This
 * communication occurs through the {@code vehicle/dtc} services of the WVA.
 *
 */
public class FaultCodes {

    private static final String TAG = "wvalib FaultCodes";
    private static final String SUBSCRIPTION_BASE = "subscriptions/";
    private static final String ALARM_BASE = "alarms/";
    private static final boolean BUFFER_SUBSCRIPTIONS = true;
    private static final boolean BUFFER_ALARMS = true;
    private static final String SUB_SUFFIX = "~dtcsub",
                               ALARM_SUFFIX = "~change";

    /**
     * Maps web service URIs to their most recent received value.
     */
    private final ConcurrentHashMap<String, FaultCodeResponse> faultCodeCache;
    private final HttpClient httpClient;

    /**
     * Maps fault code ECU paths (e.g. can0_active/ecu0) to their listeners.
     */
    private ConcurrentHashMap<String, FaultCodeListener> listenerMap = new ConcurrentHashMap<String, FaultCodeListener>();
    private FaultCodeListener allListener;

    /**
     * Constructor.
     * @param client the HTTP client used to handle HTTP calls made
     */
    public FaultCodes(HttpClient client) {
        this.faultCodeCache = new ConcurrentHashMap<String, FaultCodeResponse>();
        this.httpClient = client;
    }

    /**
     * Triggers the {@link #setFaultCodeListener(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String, FaultCodeListener) listener associated}
     * with <b>e</b>'s {@link FaultCodeEvent#getShortName() shortname} (if there are any), and then
     * the {@link #setFaultCodeListener(FaultCodeListener) "catch-all" listener} (if any).
     *
     * @param e the event to be used to trigger the aforementioned listeners
     */
    public void notifyListeners(FaultCodeEvent e) {
        String path = e.getUri().substring(FaultCodeCommon.FAULT_CODE_BASE.length());

        // Call the ECU-specific listener, if any
        if (listenerMap.containsKey(path)) {
            listenerMap.get(path).onEvent(e);
        }

        if (allListener != null) {
            allListener.onEvent(e);
        }
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#removeAllFaultCodeListeners()}
     */
    public void removeAllListeners() {
        listenerMap.clear();
        allListener = null;
    }

    /**
     * Updates the cached fault code value of a given ECU, and also calls
     * {@link #notifyListeners(FaultCodeEvent)} with <b>e</b>.
     *
     * It is convenient for testing when no WVA and/or TCP connection is
     * available.
     *
     * @param e a FaultCodeEvent object
     */
    public void updateCachedFaultCode(FaultCodeEvent e) {
        if (e == null) {
            Log.w(TAG, "updateCachedFaultCode received null event");
            return;
        }

        faultCodeCache.put(e.getUri(), e.getResponse());
        notifyListeners(e);
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#setFaultCodeListener(FaultCodeListener)}
     */
    public void setFaultCodeListener(FaultCodeListener listener) {
        this.allListener = listener;
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#removeFaultCodeListener()}
     */
    public void removeFaultCodeListener() {
        this.allListener = null;
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#setFaultCodeListener(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String, FaultCodeListener)}
     */
    public void setFaultCodeListener(FaultCodeCommon.Bus bus, FaultCodeCommon.FaultCodeType type, String ecu, FaultCodeListener listener) {
        String ecuPath = FaultCodeCommon.createEcuPath(bus, type, ecu);

        listenerMap.put(ecuPath, listener);
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#removeFaultCodeListener(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String)}
     */
    public void removeFaultCodeListener(FaultCodeCommon.Bus bus, FaultCodeCommon.FaultCodeType type, String ecu) {
        String ecuPath = FaultCodeCommon.createEcuPath(bus, type, ecu);

        listenerMap.remove(ecuPath);
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#getCachedFaultCode(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String)}
     */
    public FaultCodeResponse getCachedFaultCode(FaultCodeCommon.Bus bus, FaultCodeCommon.FaultCodeType type, String ecu) {
        return faultCodeCache.get(FaultCodeCommon.createUri(bus, type, ecu));
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#fetchFaultCode(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String, WvaCallback)}
     */
    public void fetchFaultCode(final FaultCodeCommon.Bus bus, final FaultCodeCommon.FaultCodeType type, final String ecu, final WvaCallback<FaultCodeResponse> cb) {
        final String ecuPath = FaultCodeCommon.createEcuPath(bus, type, ecu);
        String uri = FaultCodeCommon.createUri(ecuPath);

        httpClient.get(uri, new HttpCallback() {
            @Override
            public void onSuccess(JSONObject jObj) {
                JSONObject valTimeObj;
                try {
                    valTimeObj = jObj.getJSONObject(ecu);
                    faultCodeCache.replace(ecuPath, new FaultCodeResponse(valTimeObj));

                    if (cb != null) {
                        cb.onResponse(null, new FaultCodeResponse(valTimeObj));
                    }

                } catch (JSONException e) {
                    Log.e(TAG, "Data fetched from " + ecuPath + " unreadable", e);
                    if (cb != null) {
                        cb.onResponse(e, null);
                    }
                }
            }

            @Override
            public void onFailure(Throwable error) {
                if (error instanceof WvaHttpException.WvaHttpServiceUnavailable) {
                    // The referenced ECU is expected to be valid, but no PGN 65227 message
                    // has yet been received.
                    if (cb != null) {
                        cb.onResponse(null, null);
                    }
                } else if (error instanceof WvaHttpException.WvaHttpNotFound) {
                    // Generally indicates that the system is not listening for trouble codes
                    // on the referenced ECU.
                    if (cb != null) {
                        cb.onResponse(new NotListeningToECUException(ecuPath), null);
                    }
                } else {
                    Log.e(TAG, String.format("Error fetching fault code from %s: %s", ecuPath, error.getMessage()));
                    error.printStackTrace();
                    if (cb != null) {
                        cb.onResponse(error, null);
                    }
                }
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#fetchFaultCodeEcuNames(com.digi.wva.async.FaultCodeCommon.Bus, WvaCallback)}
     */
    public void fetchEcuNames(final FaultCodeCommon.Bus bus, final WvaCallback<Set<String>> callback) {
        final String resource = String.format("%s_active", bus);
        final String subPath = String.format("%s%s", FaultCodeCommon.FAULT_CODE_BASE, resource);
        final int subPathLength = subPath.length();

        httpClient.get(subPath, new HttpCallback() {
            @Override
            public void onSuccess(JSONObject obj) {
                JSONArray array;
                try {
                    array = obj.getJSONArray(resource);
                } catch (JSONException e) {
                    Log.e(TAG, String.format("fetchEcuNames: Web services response has no '%s' key", resource));
                    callback.onResponse(e, null);
                    return;
                }

                Set<String> returnSet = new HashSet<String>();

                for (int i = 0; i < array.length(); i++) {
                    String value;
                    try {
                        value = array.getString(i);
                    } catch (JSONException e) {
                        // Value at index i is not a string. Log the error.
                        String error;
                        if (array.isNull(i)) {
                            error = "Null ECU URL in web service response";
                        } else {
                            // Should never reach this point. getString will coerce values, so
                            // it should not be possible to have a non-null, non-String value here.
                            try {
                                value = array.get(i).toString();
                            } catch (JSONException e2) {
                                e.printStackTrace();
                                continue;
                            }
                            error = "Non-string ECU URL in web service response: " + value;
                        }

                        Log.e(TAG, error);
                        continue;
                    }

                    if (!value.startsWith(subPath + '/')) {
                        Log.e(TAG, String.format("ECU URL '%s' doesn't start with '%s/'", value, subPath));
                        continue;
                    }

                    // Trim off the leading vehicle/dtc/...._active/
                    returnSet.add(value.substring(subPathLength + 1));
                }

                callback.onResponse(null, returnSet);
            }

            @Override
            public void onFailure(Throwable error) {
                Log.e(TAG, "Error fetching ECU names on " + bus + ": " + error.getMessage());
                callback.onResponse(error, null);
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#subscribeToFaultCodes(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String, int, WvaCallback)}
     * @throws JSONException If an error occurs while creating the request
     */
    public void subscribe(final FaultCodeCommon.Bus bus, final FaultCodeCommon.FaultCodeType type, final String ecu,
            int seconds, final WvaCallback<Void> cb) throws JSONException {
        final String ecuPath = FaultCodeCommon.createEcuPath(bus, type, ecu);
        String uri = FaultCodeCommon.createUri(ecuPath);

        JSONObject parameters = new JSONObject();
        JSONObject subscription = new JSONObject();
        parameters.put("interval", seconds);
        parameters.put("uri", uri);
        parameters.put("buffer",  BUFFER_SUBSCRIPTIONS ? "queue" : "discard");
        subscription.put("subscription",  parameters);

        // The url at which the subscription will be available
        final String shortName = ecuPath.replace('/', '~') + SUB_SUFFIX;

        httpClient.put(SUBSCRIPTION_BASE + shortName, subscription, new ExpectEmptyCallback() {
            @Override
            public void onFailure(Throwable error) {
                Log.e(TAG, "Failed to subscribe to fault code " + ecuPath, error);
                if (cb != null) {
                    cb.onResponse(error, null);
                }
            }

            @Override
            public void onBodyNotEmpty(String body) {
                Log.e(TAG, "subscribe got unexpected response body content:\n" + body);
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
     * Underlying implementation of {@link com.digi.wva.WVA#unsubscribeFromFaultCodes(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String, WvaCallback)}
     */
    public void unsubscribe(final FaultCodeCommon.Bus bus, final FaultCodeCommon.FaultCodeType type, final String ecu, final WvaCallback<Void> cb) {
        final String ecuPath = FaultCodeCommon.createEcuPath(bus, type, ecu);

        final String shortName = ecuPath.replace('/', '~') + SUB_SUFFIX;

        httpClient.delete(SUBSCRIPTION_BASE + shortName,  new ExpectEmptyCallback() {
            @Override
            public void onFailure(Throwable error) {
                if (error instanceof WvaHttpException.WvaHttpNotFound) {
                    // The error was 404 not found. This is fine, since it means
                    // there were no subscriptions for this ECU to begin with.
                    // We don't need to fill the logs with the stack trace from this error.
                    Log.e(TAG, "Unable to unsubscribe from " + ecuPath + ": no subscription exists.");
                } else {
                    Log.e(TAG, "Unable to unsubscribe from " + ecuPath, error);
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
     * Underlying implementation of {@link com.digi.wva.WVA#createFaultCodeAlarm(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String, int, WvaCallback)}
     *
     * @throws JSONException if an error occurs in generating the JSON data
     * being sent to the WVA to create the alarm
     */
    public void createAlarm(final FaultCodeCommon.Bus bus, final FaultCodeCommon.FaultCodeType type, final String ecu, int seconds, final WvaCallback<Void> cb) throws JSONException {
        final String ecuPath = FaultCodeCommon.createEcuPath(bus, type, ecu);
        String uri = FaultCodeCommon.createUri(ecuPath);

        JSONObject parameters = new JSONObject();
        JSONObject alarm = new JSONObject();
        parameters.put("interval", seconds);
        parameters.put("uri", uri);
        parameters.put("type", AlarmType.makeString(AlarmType.CHANGE));
        parameters.put("threshold", 0);
        parameters.put("buffer", BUFFER_ALARMS ? "queue" : "discard");
        alarm.put("alarm",  parameters);

        // The resource at which the alarm will be available
        final String shortname = ecuPath.replace('/', '~') + ALARM_SUFFIX;

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
                Log.e(TAG, "Failed to create alarm for " + ecuPath, error);
                if (cb != null) {
                    cb.onResponse(error, null);
                }
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#deleteFaultCodeAlarm(com.digi.wva.async.FaultCodeCommon.Bus, com.digi.wva.async.FaultCodeCommon.FaultCodeType, String, WvaCallback)}
     */
    public void deleteAlarm(final FaultCodeCommon.Bus bus, final FaultCodeCommon.FaultCodeType type, final String ecu, final WvaCallback<Void> cb) {
        final String ecuPath = FaultCodeCommon.createEcuPath(bus, type, ecu);

        final String shortname = ecuPath.replace('/', '~') + ALARM_SUFFIX;

        httpClient.delete(ALARM_BASE + shortname, new ExpectEmptyCallback() {
            @Override
            public void onFailure(Throwable error) {
                if (error instanceof WvaHttpException.WvaHttpNotFound) {
                    // The error was 404 not found. This is fine, since it means
                    // there were no alarms for this ECU and type to begin with.
                    // We don't need to fill the logs with the stack trace from this error.
                    Log.e(TAG, "Unable to remove alarm from " + ecuPath + ": no alarm exists.");
                } else {
                    Log.e(TAG, "Unable to remove alarm from " + ecuPath, error);
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

}
