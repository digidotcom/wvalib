/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.test_auxiliary;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.digi.wva.internal.HttpClient;

/**
 * Spoofs all HttpClient interactions. Used by WVA library unit testing.
 */
public class HttpClientSpoofer extends HttpClient {
    /** JSON data to 'respond' with. Set to null to use an "empty" response. */
    public JSONObject returnObject = null;
    /** JSON data sent down. */
    public JSONObject requestBody = null;
    /** Whether to call the callback as a success or failure. */
    public boolean success = true;
    /** Last request spoofed. E.g. "GET vehicle/data/EngineSpeed" */
    public String requestSummary = null;
    /** The error to pass into onFailure. Requires success=false to have any effect */
    public Throwable failWith = null;

    public HttpClientSpoofer(String hostname) {
        super(hostname);
    }

    private void spoof(HttpCallback callback, String requestSummary) {
        Log.v("HttpClientSpoofer", "Spoofing response to " + requestSummary);
        this.requestSummary = requestSummary;

        if (success) {
            if (returnObject == null) {
                callback.onJsonParseError(new JSONException("Spoofer - empty response"), "");
            } else {
                callback.onSuccess(returnObject);
            }
        } else {
            Throwable failure = (failWith == null ? new Exception("HttpClientSpoofer - no success") : failWith);
            callback.onFailure(failure);
        }
    }

    @Override
    public void get(String url, HttpCallback responseHandler) {
        spoof(responseHandler, "GET " + url);
    }

    @Override
    public void put(String url, JSONObject jObj, HttpCallback responseHandler) {
        requestBody = jObj;
        spoof(responseHandler, "PUT " + url);
    }

    @Override
    public void delete(String url, HttpCallback responseHandler) {
        spoof(responseHandler, "DELETE " + url);
    }

    @Override
    public void post(String url, JSONObject jObj, HttpCallback responseHandler) {
        requestBody = jObj;
        spoof(responseHandler, "POST " + url);
    }
}
