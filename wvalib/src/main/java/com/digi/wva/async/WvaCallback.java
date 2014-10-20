/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.async;

import android.os.Handler;

import com.digi.wva.internal.MainThreadOptional;

/**
 * Callback class used to receive response information from asynchronous calls into
 * the library.
 */
public abstract class WvaCallback<T> implements MainThreadOptional {
    /**
     * Method to be called upon completion of the call that this callback is associated with.
     *
     * @param error a notable exception encountered during the call, or null if there is none
     * @param response the "return value" for this call
     */
    public abstract void onResponse(Throwable error, T response);

    /**
     * Specifies whether this callback should be executed on the
     * application's main thread (also called the UI thread), or
     * if it should be executed on a background thread. By default,
     * this returns <b>true</b>.
     *
     * <p>If you know a callback does not need to be run on the UI
     * thread, in your WvaCallback, override this method. Here's an example:
     * </p>
     *
     * <pre>
     *     makeSomeCall(new WvaCallback<Void>() {
     *         {@literal @}Override
     *         public void runsOnUiThread() {
     *             return false;
     *         }
     *
     *         {@literal @}Override
     *         public void onResponse(Throwable error, Void response) {
     *             // ...
     *         }
     *     });
     * </pre>
     *
     * @return true if this callback should be executed on the UI thread,
     *         false otherwise
     */
    public boolean runsOnUiThread() {
        return true;
    }

    /**
     * <b>Normal library users should not need to call this method.</b>
     * Used internally by the library to convert a normal callback into one where
     * {@link #onResponse(Throwable, Object) onResponse} is executed on the main thread.
     *
     * <p>
     * If the callback's {@link #runsOnUiThread()} returns true, returns a new WvaCallback instance
     * to run <b>callback</b>'s onResponse method on the UI thread. Otherwise, if
     * {@link #runsOnUiThread()} returns false, this method returns the callback.
     * </p>
     *
     * @param uiThread the main thread handler, to be used when wrapping the callback
     *                  to execute on the main thread
     * @param callback the callback to wrap
     * @return a wrapped callback, or <b>callback</b> if it does not need to run on the UI thread.
     *          If <b>callback</b> is null, this returns null as well
     */
    public static <T> WvaCallback<T> wrap(final WvaCallback<T> callback, final Handler uiThread) {
        if (callback == null) {
            return null;
        }

        if (callback.runsOnUiThread()) {
            // Wrap the WvaCallback in a new one which, when called, posts a call
            // of its onResponse method to the UI thread.
            return new UiThreadWvaCallback<T>(uiThread, callback);
        } else {
            return callback;
        }
    }

    private static class UiThreadWvaCallback<T> extends WvaCallback<T> {
        private final Handler uiThread;
        private final WvaCallback<T> callback;

        public UiThreadWvaCallback(Handler uiThread, WvaCallback<T> callback) {
            this.uiThread = uiThread;
            this.callback = callback;
        }

        /**
         * Method to be called upon completion of the call that this callback is associated with.
         * This will post a {@link Runnable} to the application's main thread, wherein the callback
         * (wrapped inside this {@link UiThreadWvaCallback}'s
         * {@link WvaCallback#onResponse(Throwable, Object) onResponse} method will be called with
         * the same arguments.
         *
         * @param error a exception encountered during the call, or null if there is none
         * @param response the return value for this call
         */
        @Override
        public void onResponse(final Throwable error, final T response) {
            uiThread.post(new Runnable() {
                @Override
                public void run() {
                    callback.onResponse(error, response);
                }
            });
        }
    }
}
