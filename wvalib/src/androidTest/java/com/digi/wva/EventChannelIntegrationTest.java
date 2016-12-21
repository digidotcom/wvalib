/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva;

import com.digi.wva.async.EventChannelStateListener;
import com.digi.wva.test_auxiliary.IntegrationTestCase;
import com.digi.wva.test_auxiliary.MockStateListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Integration testing of the event channel state.
 *
 * <p>These tests do not include any onError testing, because that callback is only invoked on
 * truly unexpected errors, which are difficult to inject into the running system (such as errors
 * opening up a reader).</p>
 */
public class EventChannelIntegrationTest extends IntegrationTestCase {

    /**
     * Test the reporting of a successful connection.
     */
    public void testConnect() {
        // Ensure that we are not connected yet.
        assertTrue(wva.isEventChannelDisconnected());

        // Set up the state listener
        MockStateListener stateListener = new MockStateListener();
        wva.setEventChannelStateListener(stateListener);
        stateListener.verifyNoInvocations();

        // Connect to the event channel
        wva.connectEventChannel(events.getPort());

        // Wait for a successful connection
        MockStateListener.OnConnectedCall connected = stateListener.expectOnConnected();
        assertEquals(wva, connected.device);

        // Since we're connected, isEventChannelDisconnected should return false
        assertFalse(wva.isEventChannelDisconnected());

        // No more state changes should have occurred.
        stateListener.verifyNoInvocations();
    }

    /**
     * Test the reporting of the socket closing on the remote end.
     */
    public void testRemoteClose() {
        // Ensure that we are not connected yet.
        assertTrue(wva.isEventChannelDisconnected());

        // Set up the state listener
        MockStateListener stateListener = new MockStateListener();
        wva.setEventChannelStateListener(stateListener);

        // Connect to the event channel
        wva.connectEventChannel(events.getPort());

        // Wait for a successful connection
        MockStateListener.OnConnectedCall connected = stateListener.expectOnConnected();
        assertEquals(wva, connected.device);

        // Since we're connected, isEventChannelDisconnected should return false
        assertFalse(wva.isEventChannelDisconnected());

        // Now, trigger a remote close of the socket
        events.triggerRemoteClose();

        // The listener's onRemoteClose method should be invoked
        MockStateListener.OnRemoteCloseCall remoteClose = stateListener.expectOnRemoteClose();
        assertEquals(wva, remoteClose.device);
        assertEquals(events.getPort(), remoteClose.port);

        // The listener's onDone method should also be invoked
        MockStateListener.OnDoneCall done = stateListener.expectOnDone();
        assertEquals(wva, done.device);

        // We should report as disconnected from the event channel.
        assertTrue(wva.isEventChannelDisconnected());

        // No more state changes should have occurred.
        stateListener.verifyNoInvocations();
    }

    /**
     * Test the reporting of a failed connection.
     */
    public void testFailedConnection() {
        // Ensure that we are not connected yet.
        assertTrue(wva.isEventChannelDisconnected());

        // Set up the state listener
        MockStateListener stateListener = new MockStateListener();
        wva.setEventChannelStateListener(stateListener);

        // Connect to a port not served up by the event channel
        // (Note: This assumes the device running the tests has nothing listening on port 80)
        wva.connectEventChannel(80);

        // Expect that we are unable to establish a connection
        MockStateListener.OnFailedConnectionCall failed = stateListener.expectOnFailedConnection();
        assertEquals(wva, failed.device);
        assertEquals(80, failed.port);

        // Since we didn't connect, isEventChannelDisconnect should still return true
        assertTrue(wva.isEventChannelDisconnected());

        // onDone should also have been called
        MockStateListener.OnDoneCall done = stateListener.expectOnDone();
        assertEquals(wva, done.device);

        // No more state changes should have occurred.
        stateListener.verifyNoInvocations();
    }

    /**
     * Test the handling of the reconnectAfter method.
     */
    public void testReconnectAfter() {
        final CountDownLatch failedConnection = new CountDownLatch(1),
                             connected = new CountDownLatch(1);
        // Store two values: the time of onFailedConnection, and the time on onConnected
        final long[] times = new long[2];

        final int reconnectDelay = 1000;

        // Set up the state listener
        wva.setEventChannelStateListener(new EventChannelStateListener() {
            @Override
            public void onConnected(WVA device) {
                times[1] = System.currentTimeMillis();

                connected.countDown();
            }

            @Override
            public void onFailedConnection(WVA device, int port) {
                failedConnection.countDown();

                times[0] = System.currentTimeMillis();
                this.reconnectAfter(device, reconnectDelay, events.getPort());
            }
        });

        // Connect to a port not served up by the event channel
        // (Note: This assumes the device running the tests has nothing listening on port 80)
        wva.connectEventChannel(80);

        try {
            if (!failedConnection.await(3, TimeUnit.SECONDS)) {
                fail("Time out waiting for onFailedConnection");
            }
        } catch (InterruptedException e) {
            fail("Interrupted waiting for onFailedConnection");
        }

        try {
            if (!connected.await(3, TimeUnit.SECONDS)) {
                fail("Time out waiting for onConnected");
            }
        } catch (InterruptedException e) {
            fail("Interrupted waiting for onConnected");
        }

        long timeToReconnect = times[1] - times[0];
        // Allow 250 milliseconds of leeway.
        assertTrue("Reconnect took " + timeToReconnect + " milliseconds", Math.abs(timeToReconnect - reconnectDelay) < 250);
    }
}
