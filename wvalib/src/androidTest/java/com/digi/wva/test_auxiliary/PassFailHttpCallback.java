/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.test_auxiliary;

import com.digi.wva.internal.HttpClient.HttpCallback;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An HttpCallback implementation which records its success/failure state and response.
 * Also provides an await method, so that tests can wait for the callback to be executed.
 *
 * <p>Implementation based on okhttp-tests RecordingCallback class.</p>
 */
public class PassFailHttpCallback extends HttpCallback {
    public static class RecordedResponse {
        public final boolean success;
        public final JSONObject response;
        public final Throwable error;

        public RecordedResponse(boolean success, JSONObject response, Throwable error) {
            this.success = success;
            this.response = response;
            this.error = error;
        }
    }

    // Wait up to 15 seconds for the callback to be executed.
    public static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private final List<RecordedResponse> responses = new ArrayList<RecordedResponse>();

    @Override
    public synchronized void onSuccess(JSONObject response) {
        responses.add(new RecordedResponse(true, response, null));
        notifyAll();
    }

    @Override
    public synchronized void onFailure(Throwable error) {
        responses.add(new RecordedResponse(false, null, error));
        notifyAll();
    }

    @SuppressWarnings("LoopStatementThatDoesntLoop")
    public synchronized RecordedResponse await() {
        long timeoutMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) + TIMEOUT_MILLIS;

        while (true) {
            for (Iterator<RecordedResponse> i = responses.iterator(); i.hasNext(); ) {
                RecordedResponse r = i.next();
                i.remove();
                return r;
            }

            long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
            if (now >= timeoutMillis) break;

            try {
                wait(timeoutMillis - now);
            } catch (InterruptedException e) {
                // Wrap exception so that we don't need to declare it in the signature.
                throw new AssertionError("await interrupted");
            }
        }

        // Broke out of the loop because we timed out.
        throw new AssertionError("Timed out waiting for response.");
    }
}
