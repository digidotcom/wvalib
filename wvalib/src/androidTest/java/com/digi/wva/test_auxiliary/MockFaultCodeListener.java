/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.test_auxiliary;

import com.digi.wva.async.FaultCodeEvent;
import com.digi.wva.async.FaultCodeListener;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Custom {@link FaultCodeListener} subclass, specialized for use in integration testing of the
 * WVA library.
 */
public class MockFaultCodeListener extends FaultCodeListener {
    private final BlockingQueue<FaultCodeEvent> events = new LinkedBlockingQueue<FaultCodeEvent>();

    @Override
    public final void onEvent(FaultCodeEvent event) {
        events.add(event);
    }

    /**
     * Waits until {@link #onEvent} has been called, or a 5-second wait timeout period elapses.
     *
     * <p>This listener class keeps an internal queue of captured events, and this method
     * returns the head of that queue. This means that you can test a listener by first sending more than
     * one event through the event channel, and then calling expectEvent for each event sent,
     * performing assertions on each, in the order the events were sent.</p>
     *
     * <p>If the thread is interrupted, or the waiting period completes,
     * this method throws an AssertionError.</p>
     *
     * @return the event
     */
    public synchronized FaultCodeEvent expectEvent() {
        FaultCodeEvent event;
        try {
            event = events.poll(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new AssertionError("Thread interrupted waiting for event");
        }

        if (event == null) {
            throw new AssertionError("Timeout waiting for fault code event");
        }

        return event;
    }

    /**
     * Verify that this mock listener has no more recorded events.
     *
     * <p>Use this method in test cases to verify that a listener has never been invoked or
     * that all invocations has been extracted (via {@link #expectEvent()}).</p>
     */
    public synchronized void verifyNoEvents() {
        int count = events.size();
        if (count > 0) {
            throw new AssertionError("Expected no more events, but there were " + count);
        }
    }
}
