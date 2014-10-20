/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.internal;

/**
 * Base class for event listeners registered with the {@link VehicleData} instance in use.
 */
public abstract class AbstractEventListener<E extends AbstractEvent> implements MainThreadOptional {
    /**
     * Callback for a new vehicle response. Will be called when a new event
     * comes in through the WVA's TCP event channel and is parsed out.
     *
     * @param event the event being processed
     */
    public abstract void onEvent(E event);

    /**
     * Specifies whether this listener's {@link #onEvent onEvent} method
     * should be executed on the application's main thread (also called the UI thread), or
     * if it should be allowed to execute on a background thread. By default, this returns <b>true</b>.
     *
     * <p>
     *     If you know this listener do not need to be run on the
     *     UI thread, override this method to return false. Here's an example:
     * </p>
     *
     * @return true if this listener should be executed on the UI thread,
     *         false otherwise
     */
    public boolean runsOnUiThread() {
        return true;
    }
}
