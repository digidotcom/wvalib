/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2014 Digi International Inc., All Rights Reserved.
 */

package com.digi.wva.util;

import android.text.TextUtils;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 * Utility methods for the WVA Android library.
 */
public final class WvaUtil {
    private static final DateTimeFormatter formatMillis = ISODateTimeFormat.dateTime();
    private static final DateTimeFormatter format = ISODateTimeFormat.dateTimeNoMillis();

    /**
     * Default constructor. WvaUtil is never intended to be instantiated.
     * Unfortunately, JaCoCo doesn't exclude auto-generated default constructors, nor does it provide
     * any way to ignore lines/methods - so this constructor will show up as unused in code coverage
     * results.
     */
    private WvaUtil() {}

    /**
     * Parse the given time string into a corresponding {@link DateTime} object.
     * @param timestamp the time string to parse
     * @return the parsed {@link DateTime} object
     */
    public static DateTime dateTimeFromString(String timestamp) {
        try {
            return format.parseDateTime(timestamp);
        } catch (IllegalArgumentException e) {
            // Real WVA sends timestamps without milliseconds. If not connected
            // to "real" WVA (i.e. spoofer), we might be sending out timestamps
            // with milliseconds. In that case, parse it out here.
           return formatMillis.parseDateTime(timestamp);
        }
    }

    /**
     * Extract the last path piece from the given URI.
     *
     * <pre>
     *     assert getEndpointFromUri("foo/bar/baz").equals("baz");
     * </pre>
     * @param uri the path string to parse
     * @return the section of the given path following the last slash character (<code>/</code>),
     * or null if <b>uri</b> is null or empty
     */
    public static String getEndpointFromUri(String uri) {
        if (TextUtils.isEmpty(uri)) {
            return null;
        } else {
            // lastIndexOf returns -1 if the value isn't found. substring from 0 is the
            // whole string, so this is okay.
            return uri.substring(uri.lastIndexOf('/') + 1, uri.length());
        }
    }

    /**
     * Given a web services URI or sub-path, intelligently parse out the correct top-level key
     * to use when applying configuration to the WVA.
     *
     * <p>
     *     For example, when interacting with {@code ws/config/canbus/1}, the JSON we send down
     *     must have the top-level key {@code canbus}.
     * </p>
     *
     * @param uri the full request path, or a section of that, to be used when applying configuration
     * to the WVA
     * @return
     * <ul>
     *     <li>
     *         The first piece of the given path following {@code config/}, if {@code config/} is
     *         present in the path. This represents the settings group being configured, which is
     *         the correct string to use as the top-level key.
     *     </li>
     *     <li>
     *         <b>null</b> if the last piece of the path is {@code config}, implying that
     *          we will be unable to decide which key to use (because it is not clear what is
     *          being configured).
     *     </li>
     *     <li>
     *         The first piece of the path, if {@code config} is not found as a section of the path.
     *         This implies that <b>uri</b> is the path under {@code ws/config/} being used, and as
     *         such, the first section of the path represents the settings group being configured.
     *     </li>
     * </ul>
     */
    public static String getConfigKeyFromUri(String uri) {
        if (uri.endsWith("/")) {
            // If there's a trailing slash, remove it.
            uri = uri.substring(0, uri.length() - 1);
        }
        String[] pieces = uri.split("/");
        // Take the first piece after 'config'. If 'config/' is not present, then take
        // the first piece of the URI.
        int configIndex = -1;
        for (int i = 0; i < pieces.length; i++) {
            if ("config".equals(pieces[i])) {
                configIndex = i;
                break;
            }
        }

        if (configIndex != -1) {
            if (configIndex == pieces.length - 1) {
                // config was the last piece of the path.
                return null;
            }

            return pieces[configIndex + 1];
        } else {
            // config not present in path. Return the first piece
            return pieces[0];
        }
    }
}
