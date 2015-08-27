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
 * VehicleDataResponse objects encapsulate vehicle data.
 */
public class VehicleDataResponse extends AbstractVehicleResponse<Double> {
    /**
     * Creates a new VehicleDataResponse object from a JSONObject.
     * @throws JSONException When the JSONObject received is not of the form
     *         {"value":&lt;double&gt;, "timestamp":&lt;ISO8601 timestamp&gt;}
     */
    public VehicleDataResponse(JSONObject from) throws JSONException {
        super(null, from.getString("timestamp"), null);

        // Grab the value in both "raw" and parsed form.
        this.setRawValue(from.get("value"));

        double doubleValue = from.optDouble("value");
        this.setValue(Double.isNaN(doubleValue) ? null : doubleValue);
    }
}

