package com.roni.library.bridge;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * B4 — outbound payloads must be JSON-quoted before interpolation into the page.
 */
public class BridgeInjectionTest {

    private WebBridge bridge;
    private final AtomicReference<String> script = new AtomicReference<>();

    @Before
    public void setUp() {
        WebBridge.getInstance().release();
        bridge = WebBridge.init(BridgeConfig.builder().build());
        bridge.setWebEvaluator(script::set);
    }

    @After
    public void tearDown() {
        bridge.release();
    }

    private void emit(String value) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("value", value);
        bridge.emitToWeb("test", payload);
    }

    @Test
    public void quoteBreakoutIsEscaped() {
        emit("\");alert(1);//");
        assertNotNull(script.get());
        assertTrue("payload should still be present", script.get().contains("alert(1)"));
        assertEquals("only the two argument delimiters may stay unescaped",
                2, countUnescapedQuotes(script.get()));
    }

    @Test
    public void scriptTagIsEscaped() {
        emit("</script><script>alert(1)</script>");
        assertNotNull(script.get());
        assertFalse(script.get().contains("</script>"));
        assertTrue(script.get().contains("<\\/script>"));
    }

    @Test
    public void lineSeparatorIsEscaped() {
        emit("a\u2028b");
        assertNotNull(script.get());
        assertFalse(script.get().contains("\u2028"));
        assertTrue(script.get().contains("\\u2028"));
    }

    @Test
    public void maliciousInterfaceNameFallsBack() {
        BridgeConfig config = BridgeConfig.builder()
                .interfaceName("bad-name');alert(1);//")
                .build();
        assertEquals(BridgeConfig.DEFAULT_INTERFACE_NAME, config.getInterfaceName());

        String script = JavaScriptBridgeScriptHolder.injection("bad-name');alert(1);//");
        assertTrue(script.contains(BridgeConfig.DEFAULT_INTERFACE_NAME + "Client"));
        assertFalse(script.contains("alert(1)"));
    }

    private static int countUnescapedQuotes(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '"') {
                continue;
            }
            int backslashes = 0;
            for (int j = i - 1; j >= 0 && s.charAt(j) == '\\'; j--) {
                backslashes++;
            }
            if (backslashes % 2 == 0) {
                count++;
            }
        }
        return count;
    }

    /** Keeps the import of the js package local to this test. */
    private static final class JavaScriptBridgeScriptHolder {
        static String injection(String name) {
            return com.roni.library.bridge.js.JavaScriptBridgeScript.getInjectionScript(name);
        }
    }
}
