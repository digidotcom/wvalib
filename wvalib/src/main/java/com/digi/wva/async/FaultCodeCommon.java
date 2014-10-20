/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.async;

import android.text.TextUtils;

import java.util.Locale;

/**
 * Provides a collection of static content and methods pertaining to fault codes.
 * */
public class FaultCodeCommon {
    /** The base URL path for all fault code data in the WVA device web services. */
    public static final String FAULT_CODE_BASE = "vehicle/dtc/";

    /**
     * Given the bus, message type, and ECU name, return the {@code vehicle/dtc/} sub-path to the
     * specified ECU data. An example return value is
     * {@code can0_active/ecu0}.
     *
     * @param bus the CAN bus used
     * @param type the message type (active or inactive)
     * @param ecu the ECU name
     * @return the result of {@code String.format("%s_%s/%s", bus, type, ecu)}
     *
     * @throws NullPointerException if <b>ecu</b> is null or 0-length. The path generated using such values
     *                                 would be meaningless
     */
    public static String createEcuPath(Bus bus, FaultCodeType type, String ecu) {
        if (TextUtils.isEmpty(ecu)) {
            throw new NullPointerException("createEcuPath: ecu argument must not be null or 0-length");
        }
        return String.format("%s_%s/%s", bus, type, ecu);
    }

    /**
     * Given the ECU URI path, return the full web services path to the specified ECU data.
     * For example, if given {@code can0_active/ecu0}, this method will return
     * {@code vehicle/dtc/can0_active/ecu0}.
     *
     * @param ecuPath the path to the desired ECU URI
     * @return the equivalent of {@code String.format("vehicle/dtc/%s", ecuPath)}
     *
     * @throws NullPointerException if <b>ecuPath</b> is null or 0-length. The path generated using such values
     *                                 would be meaningless
     */
    public static String createUri(String ecuPath) {
        if (TextUtils.isEmpty(ecuPath)) {
            throw new NullPointerException("createUri: ecuPath argument must not be null or 0-length");
        }
        return String.format("%s%s", FAULT_CODE_BASE, ecuPath);
    }

    /**
     * Given the bus, message type, and ECU name, return the full web services path to the specified ECU
     * data. An example return value is {@code vehicle/dtc/can0_active/ecu0}.
     *
     * @param bus the CAN bus used
     * @param type the message type (active or inactive)
     * @param ecu the ECU name
     * @return the equivalent of {@code String.format("vehicle/dtc/%s", createEcuPath(bus, type, ecu))}
     *
     * @throws NullPointerException if <b>ecu</b> is null or 0-length. The path generated using such values
     *                                 would be meaningless
     */
    public static String createUri(Bus bus, FaultCodeType type, String ecu) {
        final String ecuPath = createEcuPath(bus, type, ecu);

        return String.format("%s%s", FAULT_CODE_BASE, ecuPath);
    }

    /**
     * Enumeration of the different CAN buses exposed by the WVA.
     */
    public static enum Bus {
        /** The primary CAN bus of the WVA. */
        CAN0,
        /** The secondary CAN bus of the WVA. */
        CAN1;

        /**
         * Return the enum value's name, but lowercased.
         * Used when generating web service URIs, since the WVA's web services
         * use all lowercase names.
         */
        public String toString() {
            // Specifying a locale here keeps lint happy
            return super.toString().toLowerCase(Locale.US);
        }
    }

    /**
     * Enumeration of the different fault code message types.
     */
    public static enum FaultCodeType {
        /** Active DTC messages (J1939 PGN 65226). */
        ACTIVE,
        /** Inactive DTC messages (J1939 PGN 65227). */
        INACTIVE;

        /**
         * Return the enum value's name, but lowercased.
         * Used when generating web service URIs, since the WVA's web services
         * use all lowercase names.
         */
        public String toString() {
            // Specifying a locale here keeps lint happy
            return super.toString().toLowerCase(Locale.US);
        }
    }
}
