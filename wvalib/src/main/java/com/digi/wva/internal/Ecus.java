/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import android.util.Log;
import android.util.Pair;

import com.digi.wva.async.WvaCallback;
import com.digi.wva.util.WvaUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * This object defines the interactions with the vehicle's Engine Control Units
 * (ECUs). These units provide useful information about a vehicle's physical
 * parts and attributes, such as VINs, serial numbers, make, and model.
 */
public class Ecus {
    private static final String TAG = "com.digi.wva.internal.Ecus";
    private static final String ECU_BASE = "vehicle/ecus/";

    private final HttpClient httpClient;
    private final ConcurrentMap<String, ConcurrentMap<String, String>> ecuDataCache;
    private final ConcurrentMap<String, Set<String>> ecuElements;

    /**
     * Constructor.
     * @param httpClient the HTTP client used to handle HTTP calls made
     */
    public Ecus(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.ecuDataCache = new ConcurrentHashMap<String, ConcurrentMap<String, String>>();
        this.ecuElements = new ConcurrentHashMap<String, Set<String>>();
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#fetchEcus(WvaCallback)}
     */
    public void fetchEcus(final WvaCallback<Set<String>> onInitialized) {
        httpClient.get(ECU_BASE, new HttpClient.HttpCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!response.has("ecus")) {
                    onInitialized.onResponse(new JSONException("No 'ecus' key found"), null);
                    return;
                }
                try {
                    JSONArray ecus = response.getJSONArray("ecus");
                    for (int i = 0; i < ecus.length(); i++) {
                        String uri = ecus.getString(i);
                        String ecuName = WvaUtil.getEndpointFromUri(uri);

                        Log.v(TAG, "Found ECU: " + ecuName);
                        ecuDataCache.putIfAbsent(ecuName, new ConcurrentHashMap<String, String>());
                    }

                    if (onInitialized != null) {
                        onInitialized.onResponse(null, new HashSet<String>(ecuDataCache.keySet()));
                    }
                } catch (JSONException e) {
                    Log.w(TAG, "Bad ECUs response");
                    if (onInitialized != null) {
                        onInitialized.onResponse(e, null);
                    }
                }
            }

            @Override
            public void onFailure(Throwable error) {
                if (onInitialized != null) {
                    onInitialized.onResponse(error, null);
                }
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#getCachedEcus()}
     */
    public Set<String> getCachedEcus() {
        return new HashSet<String>(ecuDataCache.keySet());
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#fetchEcuElements(String, WvaCallback)}
     */
    public void fetchEcuElements(final String ecuName, final WvaCallback<Set<String>> callback) {
        httpClient.get(ECU_BASE + ecuName, new HttpClient.HttpCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!response.has(ecuName)) {
                    callback.onResponse(new JSONException("No '" + ecuName + "' key found"), null);
                    return;
                }

                try {
                    JSONArray elements = response.getJSONArray(ecuName);

                    // Begin populating the elements map
                    if (!ecuElements.containsKey(ecuName)) {
                        // Create what is essentially a ConcurrentHashSet
                        // http://stackoverflow.com/a/6992643
                        ecuElements.put(ecuName, Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>()));
                    }
                    Set<String> cache = ecuElements.get(ecuName);

                    for (int i = 0; i < elements.length(); i++) {
                        String uri = elements.getString(i);
                        String elementName = WvaUtil.getEndpointFromUri(uri);

                        Log.v(TAG, "Found ECU element on " + ecuName + ": " + elementName);

                        // Add this ECU to the element name cache
                        cache.add(elementName);
                    }

                    if (callback != null) {
                        callback.onResponse(null, new HashSet<String>(cache));
                    }
                } catch (JSONException e) {
                    Log.w(TAG, "Bad ECU elements response");
                    if (callback != null) {
                        callback.onResponse(e, null);
                    }
                }
            }

            @Override
            public void onFailure(Throwable error) {
                if (callback != null) {
                    callback.onResponse(error, null);
                }
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#getCachedEcuElements(String)}
     */
    public Set<String> getCachedEcuElements(String ecuName) {
        Set<String> inCache = ecuElements.get(ecuName);
        return (inCache == null) ? null : new HashSet<String>(inCache);
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#fetchEcuElementValue(String, String, WvaCallback)}
     */
    public void fetchEcuElementValue(final String ecuName, final String element, final WvaCallback<String> callback) {
        httpClient.get(ECU_BASE + ecuName + "/" + element, new HttpClient.HttpCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!response.has(element)) {
                    callback.onResponse(new JSONException("No '" + element + "' key found"), null);
                    return;
                }
                try {
                    String value = response.getString(element);

                    ConcurrentMap<String, String> valueCache = ecuDataCache.get(ecuName);
                    Set<String> elementCache = ecuElements.get(ecuName);

                    if (valueCache == null) {
                        // We haven't cached any values from this ECU yet.
                        valueCache = new ConcurrentHashMap<String, String>();
                        ecuDataCache.put(ecuName, valueCache);
                    }
                    if (elementCache == null) {
                        // We haven't queried this ECU for its elements before this.
                        elementCache = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
                        ecuElements.put(ecuName, elementCache);
                    }

                    // Update caches
                    valueCache.put(element, value);
                    elementCache.add(element);

                    if (callback != null) {
                        callback.onResponse(null, value);
                    }
                } catch (JSONException e) {
                    Log.w(TAG, "Bad ECU element value response");
                    if (callback != null) {
                        callback.onResponse(e, null);
                    }
                }
            }

            @Override
            public void onFailure(Throwable error) {
                if (callback != null) {
                    callback.onResponse(error, null);
                }
            }
        });
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#getCachedEcuElementValue(String, String)}
     */
    public String getCachedEcuElementValue(String ecuName, String element) {
        if (!ecuDataCache.containsKey(ecuName)) {
            return null;
        }
        return ecuDataCache.get(ecuName).get(element);
    }

    /**
     * Underlying implementation of {@link com.digi.wva.WVA#fetchAllEcuElementValues(String, WvaCallback)}
     */
    public void fetchAllEcuElementValues(final String ecuName, final WvaCallback<Pair<String, String>> callback) {
        final Set<String> knownElements = getCachedEcuElements(ecuName);

        if (knownElements == null || knownElements.isEmpty()) {
            throw new IllegalStateException("No ECU element names in cache.");
        }

        for (final String element : knownElements) {
            fetchEcuElementValue(ecuName, element, new WvaCallback<String>() {
                @Override
                public void onResponse(Throwable error, String response) {
                    callback.onResponse(error, new Pair<String, String>(element, response));
                }
            });
        }
    }
}
