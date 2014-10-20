/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.test_auxiliary;

import com.digi.wva.WVA;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import junit.framework.TestCase;

/**
 * Base TestCase class for all WVA library integration testing.
 *
 * <p>
 *     Provides the following protected fields:
 *     <ul>
 *         <li>{@code wva}, a {@link WVA} object to act as the device-under-test</li>
 *         <li>{@code events}, a
 *             {@link com.digi.wva.test_auxiliary.SimulatedEventChannel simulated event channel}</li>
 *         <li>{@code ws}, a {@link com.squareup.okhttp.mockwebserver.MockWebServer mock web server}</li>
 *     </ul>
 *
 *     And also finalizes the setUp and tearDown methods. Just extend IntegrationTestCase and go!
 * </p>
 */
public class IntegrationTestCase extends TestCase {
    /** The WVA instance under test */
    protected WVA wva;
    /** The event channel */
    protected SimulatedEventChannel events;
    /** The web services */
    protected MockWebServer ws;

    @Override
    public final void setUp() throws Exception {
        super.setUp();

        // Start the mock event channel
        events = new SimulatedEventChannel();
        events.start();

        // Start the mock web server
        ws = new MockWebServer();
        ws.play();

        // Point the WVA to the mock web server
        wva = new WVA("127.0.0.1");
        wva.useSecureHttp(false);
        wva.setHttpPort(ws.getPort());
    }

    @Override
    protected final void tearDown() throws Exception {
        super.tearDown();

        // Disconnect the WVA
        wva.disconnectEventChannel(true);
        // Shut down the event channel
        events.shutdown();
        // Shut down the web server
        ws.shutdown();
    }
}
