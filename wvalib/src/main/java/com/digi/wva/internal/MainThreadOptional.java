/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

/**
 * Specification of the method(s) provided by callbacks and listeners which will default to
 * running on the UI thread. Overriding the {@link #runsOnUiThread()} method allows users of
 * the library to specify explicitly whether a given callback/listener should be executed on
 * the main thread.
 */
public interface MainThreadOptional {
    /**
     * Specifies whether this callback or listener's methods should be executed on the
     * application's main thread (also called the UI thread), or if it should be allowed to
     * execute on a background thread.
     *
     * @return true if the callback/listener's methods should be executed on the main thread,
     *              false otherwise
     */
    public boolean runsOnUiThread();
}
