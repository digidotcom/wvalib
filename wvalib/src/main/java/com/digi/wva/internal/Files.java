package com.digi.wva.internal;

import android.util.Log;

import com.digi.wva.async.WvaCallback;

/**
 * This object allows users to access data in the {@code files/} web services.
 *
 * <p>You should not need to create an instance of this class manually.
 * Use the {@link com.digi.wva.WVA} class to manage all interactions with the WVA.</p>
 */
public class Files {
    private static final String TAG = "wvalib Files";
    private static final String FILES_BASE = "files/";

    private final HttpClient httpClient;

    public Files(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * If the named file does not yet exist, adds a new file (with the requested URI) to the system.
     * If the requested file already exists in the system, changes the existing file.
     * The payload of the PUT command become the contents of the file.
     *
     * @param volume should be either userfs or a USB flash drive name.
     * @param path refers to a directory in this system, and is constructed from a list of directory
     *            names separated by a /, indicating a specific location within the directory tree.
     * @param content The payload of the PUT command becomes the contents of the file.
     * @param cb callback to give feedback on whether the subscription call succeeds or not
     */
    public void updateFile(final String volume, final String path, final String content,
                           final WvaCallback<Void> cb) {
        final String url = FILES_BASE + volume + path;
        httpClient.put(url, content, new HttpClient.ExpectEmptyCallback() {
            @Override
            public void onSuccess() {
                if (cb != null) {
                    cb.onResponse(null, null);
                }
            }

            @Override
            public void onBodyNotEmpty(String body) {
                Log.e(TAG, "updateFile got unexpected response body content:\n" + body);
                onFailure(new Exception("Unexpected response body: " + body));
            }

            @Override
            public void onFailure(Throwable error) {
                Log.e(TAG, "Failed to updateFile at path: " + url, error);
                if (cb != null) {
                    cb.onResponse(error, null);
                }
            }
        });
    }
}
