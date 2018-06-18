package com.digi.wva.internal;

import com.digi.wva.test_auxiliary.HttpClientSpoofer;
import com.digi.wva.test_auxiliary.PassFailCallback;

import junit.framework.TestCase;

import org.junit.Test;

public class FilesTest extends TestCase {
    HttpClientSpoofer httpClient;
    Files dut;


    protected void setUp() throws Exception {
        httpClient = new HttpClientSpoofer("hostname");
        dut = new Files(httpClient);

        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testInitFiles() {
        Files newFiles = new Files(new HttpClient("hostname2"));
        assertNotNull(newFiles);
    }

    @Test
    public void testUpdateFile() {
        PassFailCallback<Void> cb1 = new PassFailCallback<>();
        PassFailCallback<Void> cb2 = new PassFailCallback<>();

        httpClient.returnObject = null;
        httpClient.success = true;
        dut.updateFile("volume", "/path", "content", cb1);
        assertTrue(cb1.success);
        assertEquals("PUT files/volume/path", httpClient.requestSummary);

        httpClient.success = false;
        dut.updateFile("volume", "path", "content", cb2);
        assertFalse(cb2.success);
    }
}