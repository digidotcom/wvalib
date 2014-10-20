/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.test_auxiliary;

import com.digi.wva.async.WvaCallback;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A WvaCallback implementation which records its onResponse arguments,
 * and provides an expectResponse method so that tests can wait for the callback to
 * be executed.
 *
 * <p>The {@link #onResponse} arguments can be accessed via the public
 * {@link #error} and {@link #response} fields.</p>
 *
 * <p>Instances of this class are designed for one-off usage. If for some reason you reuse
 * an instance within a test, then the {@link #expectResponse} method will return immediately and the
 * error and response variables will not have been updated yet. Instead, create new MockWvaCallback
 * instances.</p>
 */
public final class MockWvaCallback<T> extends WvaCallback<T> {
    private final CountDownLatch latch = new CountDownLatch(1);
    private final String name;

    /** The {@code error} argument to onResponse */
    public Throwable error;
    /** The {@code response} argument to onResponse */
    public T response;

    /**
     * Constructor.
     * @param name a name to report in any errors, e.g. "isWVA call"
     */
    public MockWvaCallback(String name) {
        this.name = name;
    }

    @Override
    public boolean runsOnUiThread() {
        return false;
    }

    @Override
    public void onResponse(Throwable error, T response) {
        this.error = error;
        this.response = response;

        this.latch.countDown();
    }

    /**
     * Waits until {@link #onResponse} is called, or the wait timeout period elapses.
     *
     * <p>If the thread is interrupted, or the waiting period completes,
     * this method throws an AssertionError.</p>
     */
    public synchronized void expectResponse() {
        try {
            boolean called = latch.await(5, TimeUnit.SECONDS);

            if (!called) {
                throw new AssertionError("Timed out waiting for onResponse call - " + name);
            }
        } catch (InterruptedException e) {
            throw new AssertionError("expectResponse interrupted - " + name);
        }
    }
}
