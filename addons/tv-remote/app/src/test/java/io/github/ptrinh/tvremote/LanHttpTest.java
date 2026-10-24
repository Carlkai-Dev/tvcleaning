package io.github.ptrinh.tvremote;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class LanHttpTest {
    private static final String ID = "abcdefghijkl";
    private static final String KEY = "0123456789abcdefghijkl";

    private static InputStream in(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    public void pairedNeedsThisTvAndTheCurrentKey() {
        String hash = LanHttp.sha256(KEY);
        assertTrue(LanHttp.paired(ID, KEY, ID, hash));
        assertFalse(LanHttp.paired("abcdefghijkX", KEY, ID, hash));
        assertFalse(LanHttp.paired(ID, KEY + "x", ID, hash));
        assertFalse(LanHttp.paired(ID, "short", ID, LanHttp.sha256("short")));
        assertFalse(LanHttp.paired(ID, null, ID, hash));
        assertFalse(LanHttp.paired(ID, KEY, ID, null));
    }

    @Test
    public void sha256IsHexLikeTheRelays() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", LanHttp.sha256(""));
    }

    @Test
    public void pairingInTheAddress() {
        assertTrue(LanHttp.validPairing(ID + "." + KEY));
        assertFalse(LanHttp.validPairing(ID + KEY));
        assertFalse(LanHttp.validPairing(ID + "." + KEY + "\"><script>"));
        assertFalse(LanHttp.validPairing(null));
        String page = "<link rel=\"manifest\" href=\"/manifest.webmanifest\">";
        assertEquals("<link rel=\"manifest\" href=\"/manifest.webmanifest?p=" + ID + "." + KEY + "\">", LanHttp.page(page, ID + "." + KEY));
        assertEquals(page, LanHttp.page(page, "bad\""));
        assertTrue(LanHttp.manifest(ID + "." + KEY).contains("\"start_url\":\"/?p=" + ID + "." + KEY + "\""));
        assertTrue(LanHttp.manifest("x").contains("\"start_url\":\"/\""));
    }

    @Test
    public void onlyPrivateAddresses() {
        assertTrue(LanHttp.local(new byte[]{(byte) 192, (byte) 168, 1, 5}));
        assertTrue(LanHttp.local(new byte[]{10, 0, 0, 1}));
        assertTrue(LanHttp.local(new byte[]{(byte) 172, 20, 0, 1}));
        assertFalse(LanHttp.local(new byte[]{(byte) 172, 32, 0, 1}));
        assertFalse(LanHttp.local(new byte[]{8, 8, 8, 8}));
        byte[] mapped = new byte[16];
        mapped[10] = (byte) 0xff;
        mapped[11] = (byte) 0xff;
        mapped[12] = 8;
        mapped[13] = 8;
        mapped[14] = 8;
        mapped[15] = 8;
        assertFalse(LanHttp.local(mapped));
        assertFalse(LanHttp.local(null));
    }

    @Test
    public void parsesARequest() throws Exception {
        LanHttp.Request r = LanHttp.parseHead(LanHttp.readHead(in(
                "POST /api/cmd?p=a%20b HTTP/1.1\r\nHost: 192.168.1.5:8781\r\nContent-Length: 2\r\n\r\n{}")));
        assertEquals("POST", r.method);
        assertEquals("/api/cmd", r.path);
        assertEquals("a b", r.query.get("p"));
        assertEquals(2, r.contentLength());
        InputStream body = in("{}");
        assertEquals("{}", LanHttp.readBody(r, body));
    }

    private static int status(String head) {
        try {
            LanHttp.Request r = LanHttp.parseHead(LanHttp.readHead(in(head)));
            r.contentLength();
            return 0;
        } catch (LanHttp.BadRequest e) {
            return e.status;
        } catch (IOException e) {
            return -1;
        }
    }

    @Test
    public void refusesWhatIsPastTheLimits() throws Exception {
        char[] big = new char[LanHttp.MAX_HEAD + 10];
        Arrays.fill(big, 'a');
        assertEquals(431, status("GET / HTTP/1.1\r\nX: " + new String(big) + "\r\n\r\n"));
        StringBuilder many = new StringBuilder("GET / HTTP/1.1\r\n");
        for (int i = 0; i <= LanHttp.MAX_HEADERS; i++) many.append("H").append(i).append(": x\r\n");
        assertEquals(431, status(many.append("\r\n").toString()));
        assertEquals(405, status("PUT / HTTP/1.1\r\n\r\n"));
        assertEquals(505, status("GET / HTTP/2\r\n\r\n"));
        assertEquals(400, status("GET http://x/ HTTP/1.1\r\n\r\n"));
        assertEquals(400, status("GET / HTTP/1.1\r\n"));   // closed before the blank line
        assertEquals(400, status("POST / HTTP/1.1\r\nContent-Length: -1\r\n\r\n"));
        assertEquals(501, status("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"));
        StringBuilder params = new StringBuilder("GET /?");
        for (int i = 0; i <= LanHttp.MAX_PARAMS; i++) params.append("a").append(i).append("=1&");
        assertEquals(400, status(params.append(" HTTP/1.1\r\n\r\n").toString()));

        LanHttp.Request tooBig = LanHttp.parseHead(LanHttp.readHead(in("POST /api/cmd HTTP/1.1\r\nContent-Length: 9000\r\n\r\n")));
        try {
            LanHttp.readBody(tooBig, in(""));
            fail();
        } catch (LanHttp.BadRequest e) {
            assertEquals(413, e.status);
        }
        LanHttp.Request noLength = LanHttp.parseHead(LanHttp.readHead(in("POST /api/cmd HTTP/1.1\r\n\r\n")));
        try {
            LanHttp.readBody(noLength, in("{}"));
            fail();
        } catch (LanHttp.BadRequest e) {
            assertEquals(411, e.status);
        }
    }

    @Test
    public void commandChecksMatchTheRelay() {
        assertNull(LanHttp.checkCommand("ping", "", null, true));
        assertEquals(400, LanHttp.checkCommand("Bad!", "", null, true)[0]);
        assertEquals(400, LanHttp.checkCommand("banner", "", null, true)[0]);
        char[] t = new char[501];
        Arrays.fill(t, 'x');
        assertEquals(400, LanHttp.checkCommand("banner", new String(t), null, true)[0]);
        assertEquals(400, LanHttp.checkCommand("sony", "", null, false)[0]);
        char[] a = new char[3001];
        Arrays.fill(a, 'x');
        assertEquals(413, LanHttp.checkCommand("sony", "", new String(a), true)[0]);
        assertNull(LanHttp.checkCommand("set_pin", "1234", null, true));
        assertEquals("The PIN is 4 digits.", LanHttp.checkCommand("set_pin", "12a4", null, true)[1]);
        assertNull(LanHttp.checkCommand("lock_timer", "720", null, true));
        assertEquals(400, LanHttp.checkCommand("lock_timer", "721", null, true)[0]);
        assertEquals(400, LanHttp.checkCommand("lock_timer", "0", null, true)[0]);
        assertEquals(400, LanHttp.checkCommand("set_psk", new String(new char[65]).replace('\0', 'k'), null, true)[0]);
    }

    @Test
    public void webSocketFrames() throws Exception {
        // RFC 6455's example: dGhlIHNhbXBsZSBub25jZQ== is answered with s3pPLMBiTxaQ9kYGzzhZRbK+xOo=.
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", LanHttp.wsAccept("dGhlIHNhbXBsZSBub25jZQ=="));
        // A masked "Hello" from the RFC.
        byte[] masked = {(byte) 0x81, (byte) 0x85, 0x37, (byte) 0xfa, 0x21, 0x3d, 0x7f, (byte) 0x9f, 0x4d, 0x51, 0x58};
        LanHttp.Frame f = LanHttp.readFrame(new ByteArrayInputStream(masked), 4096);
        assertTrue(f.fin);
        assertEquals(1, f.op);
        assertEquals("Hello", new String(f.data, StandardCharsets.UTF_8));
        // The server's own frames are unmasked.
        assertArrayEquals(new byte[]{(byte) 0x81, 0x05, 'H', 'e', 'l', 'l', 'o'}, LanHttp.frame(1, "Hello".getBytes(StandardCharsets.UTF_8)));
        byte[] big = LanHttp.frame(2, new byte[70000]);
        assertEquals(127, big[1]);
        assertEquals(70000 + 10, big.length);
        try {
            LanHttp.readFrame(new ByteArrayInputStream(new byte[]{(byte) 0x81, 0x05, 'H', 'e', 'l', 'l', 'o'}), 4096);
            fail("unmasked client frame");
        } catch (IOException expected) {
            // Refused.
        }
        try {
            LanHttp.readFrame(new ByteArrayInputStream(new byte[]{(byte) 0x81, (byte) 0xFE, 0x20, 0x00, 0, 0, 0, 0}), 4096);
            fail("too large");
        } catch (IOException expected) {
            // Refused.
        }
    }
}
