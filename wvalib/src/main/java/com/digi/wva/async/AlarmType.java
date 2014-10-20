/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.async;

/**
 * Enumeration of the different alarm types supported by the WVA.
 */
public enum AlarmType {
    /** Alarm triggered if the tracked value goes above the given threshold.<br />
     * <b>Example:</b> EngineRPM goes above 4500.*/
    ABOVE,

    /** Alarm triggered if the tracked value falls below the given threshold.<br />
     * <b>Example:</b> EngineSpeed drops below 65. */
    BELOW,

    /** Alarm triggered if the tracked value changes at all.<br />
     * <b>Example:</b> Gear changes. */
    CHANGE,

    /** Alarm triggered if the tracked value differs from the previous value by more than
     * the configured threshold.<br />
     * <b>Example:</b> Gear alarm threshold is 2, and the value changes from 3 to 6.
     */
    DELTA;

    /**
     * Takes an AlarmType enum value and produces the string used by the
     * WVA web services to represent that type. Use this as opposed to {@link #toString()}
     *
     * @param t An AlarmType instance
     * @return The string corresponding to the type, or the empty string.
     */
    public static String makeString(AlarmType t) {
        if (t == null) {
            return "";
        }
        switch(t) {
        case ABOVE:
            return "above";
        case BELOW:
            return "below";
        case CHANGE:
            return "change";
        default:
            return "delta";
        }
    }

    /**
     * Takes a string and creates the corresponding AlarmType, or null if the
     * string does not correspond to an AlarmType.
     * @param s The input string ("above", "below", "change", "delta")
     * @return an AlarmType value, or null if the given string is not recognized
     */
    public static AlarmType fromString(String s) {
        if ("above".equalsIgnoreCase(s)) {
            return ABOVE;
        }
        else if ("below".equalsIgnoreCase(s)) {
            return BELOW;
        }
        else if ("change".equalsIgnoreCase(s)) {
            return CHANGE;
        }
        else if ("delta".equalsIgnoreCase(s)) {
            return DELTA;
        }
        else {
            return null;
        }
    }
}
