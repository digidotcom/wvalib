/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.exc;

import java.io.IOException;

/**
 * Indicates that we were unable to connect to the TCP event
 * channel port on the WVA.
 */
public class FailedConnectionException extends IOException {
    /**
     * Indicates that we were unable to connect to the TCP event
     * channel port on the WVA.
     * @param message A detail message for the failed connection
     * @param cause The original exception which led to this exception being raised
     */
    public FailedConnectionException(String message, IOException cause) {
        super(message, cause);
    }
}
