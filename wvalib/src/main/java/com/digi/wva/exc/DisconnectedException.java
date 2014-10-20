/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.exc;

import java.io.IOException;

/**
 * Indicates that the TCP event channel socket closed on the WVA's
 * end of the connection.
 */
public class DisconnectedException extends IOException {

    public DisconnectedException() {
        super();
    }

    public DisconnectedException(String detailMessage) {
        super(detailMessage);
    }
}
