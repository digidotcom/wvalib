/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import org.joda.time.DateTime;

import com.digi.wva.util.WvaUtil;

/**
 * Abstract class for "vehicle response" data. Encapsulates the
 * necessary values for such an object: value and timestamp. This
 * abstract class also provides the definition of
 * getValue and getTime methods.
 *
 * @param <V> the type of the value in the vehicle response. Examples
 *                 are Double (for standard vehicle data) and String
 *                 (used for fault codes)
 */
public abstract class AbstractVehicleResponse<V> {
    private V value;
    private DateTime time;

    public final V getValue() {
        return this.value;
    }

    public final DateTime getTime() {
        return this.time;
    }

    /**
     * Basic constructor for a vehicle response. Automatically attempts to
     * parse <b>timestamp</b> into a {@link DateTime} object.
     *
     * @param value the vehicle response value
     * @param timestamp the timestamp for the value
     */
    public AbstractVehicleResponse(V value, String timestamp) {
        this.value = value;
        this.time = WvaUtil.dateTimeFromString(timestamp);
    }
}