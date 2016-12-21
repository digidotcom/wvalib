/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

import org.joda.time.DateTime;

import com.digi.wva.WVA;
import com.digi.wva.util.WvaUtil;

/**
 * Abstract class for "vehicle response" data. Encapsulates the
 * necessary values for such an object: value and timestamp. This
 * abstract class also provides the definition of
 * getValue, getRawValue and getTime methods.
 *
 * <p><strong>NOTE:</strong> Because of an inconsistency in the data format among vehicle data
 * endpoints (in particular, IgnitionSwitchStatus is presented as a raw string instead of a
 * number), as of wvalib 2.1.0 the AbstractVehicleResponse class stores both the parsed
 * data value (if possible to parse) <i>and</i> the "raw" value taken from the JSON
 * response. User code which interacts with these endpoints will need to write some logic
 * to detect these cases, such as:
 * <pre>
 *     Object raw = response.getRawValue();
 *     if (raw instanceof String) {
 *         useStringDataResponse((String) raw);
 *     } else if (raw instanceof Double) {
 *         useDoubleDataResponse((Double) raw);
 *     } else {
 *         throw new Exception(
 *             String.format("Vehicle data response contained unrecognized data: %s",
 *                           raw));
 *     }
 * </pre>
 * </p>
 *
 * @param <V> the type of the value in the vehicle response. Examples
 *                 are Double (for standard vehicle data) and String
 *                 (used for fault codes)
 */
public abstract class AbstractVehicleResponse<V> {
    private Object rawValue;
    private V value;
    private DateTime time;

    /**
     * Internal method to set the raw value field.
     *
     * <p>This is necessary because the Java Language Specification requires calls to the super
     * constructor in a class to be the first statement - and as of wvalib version 2.1.0 we would
     * need to write custom logic <i>before</i> the super call in order to parse values correctly.
     * So instead, we will invoke the super constructor with null values (for at least the value and
     * raw value) and fill those in afterwards.</p>
     *
     * @since 2.1.0
     *
     * @param value the raw form of the response value
     */
    protected final void setRawValue(Object value) {
        this.rawValue = value;
    }

    /**
     * Internal method to set the parsed value field.
     *
     * <p>See {@link #setRawValue(Object)} for more information on why this method exists.</p>
     * @param value the parsed form of the response value
     */
    protected final void setValue(V value) {
        this.value = value;
    }

    /**
     * @since 2.1.0
     *
     * @return the response value in its "raw" form (i.e. the value as decoded from JSON with
     * {@link org.json.JSONObject#get}.
     */
    public final Object getRawValue() {
        return this.rawValue;
    }

    /**
     * @return the response value in its parsed form
     */
    public final V getValue() {
        return this.value;
    }

    /**
     * @return the timestamp associated with this response value
     */
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
        this(value, timestamp, value);
    }

    /**
     * Basic constructor for a vehicle response. Automatically attempts to
     * parse <b>timestamp</b> into a {@link DateTime} object.
     *
     * @since 2.1.0
     *
     * @param value the vehicle response value
     * @param timestamp the timestamp for the value
     * @param rawValue the vehicle response value, as decoded from JSON
     */
    public AbstractVehicleResponse(V value, String timestamp, Object rawValue) {
        this.value = value;
        this.rawValue = rawValue;
        this.time = WvaUtil.dateTimeFromString(timestamp);
    }
}