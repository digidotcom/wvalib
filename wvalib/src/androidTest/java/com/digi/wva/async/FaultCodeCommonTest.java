/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.async;

import junit.framework.TestCase;

/**
 * Unit tests for the methods and data provided by FaultCodeCommon
 */
public class FaultCodeCommonTest extends TestCase {
    public void testConstants() {
        assertEquals("vehicle/dtc/", FaultCodeCommon.FAULT_CODE_BASE);
    }

    public void testCreateEcuPath() {
        assertEquals("can0_active/ecu0", FaultCodeCommon.createEcuPath(
                FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0"));

        assertEquals("can1_inactive/ecu1", FaultCodeCommon.createEcuPath(
                FaultCodeCommon.Bus.CAN1, FaultCodeCommon.FaultCodeType.INACTIVE, "ecu1"));

        try {
            FaultCodeCommon.createEcuPath(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, null);

            // If that didn't throw an NPE, fail this test case.
            fail("Expected a NullPointerException on null ECU in createEcuPath");
        } catch (NullPointerException e) {
            // Expected.
        }

        try {
            FaultCodeCommon.createEcuPath(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "");

            // If that didn't throw an NPE, fail this test case.
            fail("Expected a NullPointerException on zero-length ECU in createEcuPath");
        } catch (NullPointerException e) {
            // Expected.
        }
    }

    public void testCreateUriWithPath() {
        assertEquals("vehicle/dtc/foo", FaultCodeCommon.createUri("foo"));

        try {
            FaultCodeCommon.createUri(null);

            // If that didn't throw an NPE, fail this test case.
            fail("Expected a NullPointerException on null path in createUri");
        } catch (NullPointerException e) {
            // Expected.
        }

        try {
            FaultCodeCommon.createUri("");

            // If that didn't throw an NPE, fail this test case.
            fail("Expected a NullPointerException on zero-length path in createUri");
        } catch (NullPointerException e) {
            // Expected.
        }
    }

    public void testCreateUriWithValues() {
        assertEquals("vehicle/dtc/can0_active/ecu0",
                FaultCodeCommon.createUri(FaultCodeCommon.Bus.CAN0, FaultCodeCommon.FaultCodeType.ACTIVE, "ecu0"));

        assertEquals("vehicle/dtc/can1_inactive/ecu1",
                FaultCodeCommon.createUri(FaultCodeCommon.Bus.CAN1, FaultCodeCommon.FaultCodeType.INACTIVE, "ecu1"));
    }
}
