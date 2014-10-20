/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.async;

import com.digi.wva.internal.AbstractVehicleResponse;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * FaultCodeResponse objects encapsulate fault code data.
 */
public class FaultCodeResponse extends AbstractVehicleResponse<String> {
    /**
     * Creates a new FaultCodeResponse object from a JSONObject.
     *
     * @throws JSONException When the JSONObject received is not of the form
     *         {"value":&lt;string&gt;, "timestamp":&lt;ISO8601 timestamp&gt;}
     */
    public FaultCodeResponse(JSONObject from) throws JSONException {
        super(from.getString("value"), from.getString("timestamp"));
    }
}

