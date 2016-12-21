/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import android.util.Log;

import com.digi.wva.async.WvaCallback;
import com.digi.wva.util.WvaUtil;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** This class allows interaction with hardware components of the WVA gateway itself rather than
 * the vehicle to which it is attached. These are located under the {@code hw} web services tree.
 */
public class Hardware {
    private static final DateTimeFormatter format = ISODateTimeFormat.dateTimeNoMillis();
    private static final String TAG = "wvalib Hardware";
    private static final String LED_BASE = "hw/leds/";
    private static final String BUTTON_BASE = "hw/buttons/";
    private static final String TIME_BASE = "hw/time/";
    private static final String LED_KEY = "leds";
    private static final String BUTTON_KEY = "buttons";

    private final HttpClient httpClient;
    private final Set<String> leds, buttons;

    /**
     * Constructor.
     * @param httpClient the HTTP client used to handle HTTP calls made
     */
    public Hardware(HttpClient httpClient) {
        this.httpClient = httpClient;
        // Make the LED and button name caches the equivalent to a ConcurrentHashSet
        this.leds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        this.buttons = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    }

    /**
     * The necessary callbacks for fetchLedNames and fetchButtonNames are
     * practically identical, so we create a new subclass of HttpCallback
     * to handle both in a more generic manner.
     */
    private class NameCacheInitializingCallback extends HttpClient.HttpCallback {
        private final String key;
        private final Set<String> initSet;
        private final WvaCallback<Set<String>> callback;

        public NameCacheInitializingCallback(String key, Set<String> initSet, WvaCallback<Set<String>> callback) {
            this.key = key;
            this.initSet = initSet;
            this.callback = callback;
        }

        @Override
        public void onSuccess(JSONObject response) {
            if (!response.has(this.key)) {
                String message = String.format("Couldn't initialize hardware. JSON response has no %s key", this.key);
                Log.w(TAG, message);
                callback.onResponse(new JSONException(message), null);
                return;
            }

            try {
                JSONArray uris = response.getJSONArray(key);
                int l  = uris.length();
                String uri;
                for (int i = 0; i < l; i++) {
                    uri = uris.getString(i);
                    String name = WvaUtil.getEndpointFromUri(uri);
                    initSet.add(name);
                    Log.v(TAG, String.format("adding hardware %s", name));
                }

                callback.onResponse(null, new HashSet<String>(initSet));
            } catch (JSONException e) {
                Log.w(TAG, String.format("Couldn't initialize %s correctly", this.key), e);
                callback.onResponse(e, null);
            }
        }

        @Override
        public void onFailure(Throwable error) {
            Log.w(TAG, String.format("Received error while initializing hardware %s", this.key), error);
            callback.onResponse(error, null);
        }
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#fetchLedNames(WvaCallback)}
     */
    public void fetchLedNames(WvaCallback<Set<String>> callback) {
        httpClient.get(LED_BASE, new NameCacheInitializingCallback(LED_KEY, leds, callback));
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#fetchButtonNames(WvaCallback)}
     */
    public void fetchButtonNames(WvaCallback<Set<String>> callback) {
        httpClient.get(BUTTON_BASE, new NameCacheInitializingCallback(BUTTON_KEY, buttons, callback));
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#getCachedButtonNames()}
     */
    public Set<String> getCachedButtonNames() {
        return new HashSet<String>(buttons);
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#getCachedLedNames()}
     */
    public Set<String> getCachedLedNames() {
        return new HashSet<String>(leds);
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#fetchButtonState(String, WvaCallback)}
     */
    public void fetchButtonState(final String buttonName, final WvaCallback<Boolean> cb) {
        httpClient.get(BUTTON_BASE + buttonName, new HttpClient.HttpCallback() {
            @Override
            public void onSuccess(JSONObject btn) {
                String pressed;
                try {
                    pressed = btn.getString("button");
                    boolean upDown = pressed.equals("up");
                    cb.onResponse(null, upDown);

                } catch (JSONException e) {
                    Log.w(TAG, "unable to fetch " + buttonName);
                    cb.onResponse(e, null);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                cb.onResponse(error, null);
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#fetchLedState(String, WvaCallback)}
     */
    public void fetchLedState(final String ledName, final WvaCallback<Boolean> cb) {
        httpClient.get(LED_BASE + ledName, new HttpClient.HttpCallback() {
            @Override
            public void onSuccess(JSONObject led) {
                String state;
                try {
                    state = led.getString("led");
                    boolean onOff = state.equals("on");
                    cb.onResponse(null, onOff);
                } catch (JSONException e) {
                    Log.w(TAG, "unable to fetch " + ledName);
                    cb.onResponse(e, null);
                }

            }

            @Override
            public void onFailure(Throwable error) {
                cb.onResponse(error, null);
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#setLedState(String, boolean, WvaCallback)}
     * @throws JSONException if an error occurs while creating the request
     */
    public void setLedState(final String ledName, final boolean state, final WvaCallback<Boolean> cb)
            throws JSONException {
        JSONObject led = new JSONObject();
        led.put("led", state ? "on" : "off");

        httpClient.put(LED_BASE + ledName, led, new HttpClient.ExpectEmptyCallback() {
            @Override
            public void onSuccess() {
                if (cb != null)
                    cb.onResponse(null, state);
            }

            @Override
            public void onBodyNotEmpty(String body) {
                Log.e(TAG, "setLedState got unexpected response body content:\n" + body);
                onFailure(new Exception("Unexpected response body: " + body));
            }

            @Override
            public void onFailure(Throwable error) {
                Log.e(TAG, "Failed to set state of LED " + ledName, error);
                if (cb != null) {
                    cb.onResponse(error, null);
                }
            }
        });

    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#fetchTime(WvaCallback)}
     */
    public void fetchTime(final WvaCallback<DateTime> cb) {
        httpClient.get(TIME_BASE, new HttpClient.HttpCallback() {
            @Override
            public void onSuccess(JSONObject timeObj) {
                String timeStr;
                try {
                    timeStr = timeObj.getString("time");
                    cb.onResponse(null, format.parseDateTime(timeStr));
                } catch (JSONException e) {
                    Log.w(TAG, "unable to get time.");
                    cb.onResponse(e, null);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                cb.onResponse(error, null);
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#setTime(DateTime, WvaCallback)}
     *
     * @throws JSONException if an error occurs while creating the request
     */
    public void setTime(final DateTime newTime, final WvaCallback<DateTime> cb) throws JSONException {
        JSONObject time = new JSONObject();

        final DateTime newTimeUTC = newTime.toDateTime(DateTimeZone.UTC);

        final String timestamp = format.print(newTimeUTC);
        Log.i(TAG, "Sending timestamp down: " + timestamp);
        time.put("time", timestamp);

        httpClient.put(TIME_BASE, time, new HttpClient.ExpectEmptyCallback() {
            @Override
            public void onSuccess() {
                if (cb != null) {
                    cb.onResponse(null, newTimeUTC);
                }
            }

            @Override
            public void onBodyNotEmpty(String body) {
                Log.e(TAG, "setTime got unexpected response body content:\n" + body);
                onFailure(new Exception("Unexpected response body: " + body));
            }

            @Override
            public void onFailure(Throwable error) {
                Log.e(TAG, "Failed to set WVA device time to " + timestamp, error);
                if (cb != null) {
                    cb.onResponse(error, newTimeUTC);
                }
            }
        });
    }
}