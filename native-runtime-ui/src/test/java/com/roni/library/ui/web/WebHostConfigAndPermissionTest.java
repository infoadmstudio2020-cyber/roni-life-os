package com.roni.library.ui.web;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * U6/U7/U8 — permission policy, cache mode mapping and bridge origin derivation.
 */
public class WebHostConfigAndPermissionTest {

    @Test
    public void originOfStripsPathAndQuery() {
        Assert.assertEquals("https://appassets.androidplatform.net",
                WebViewHost.originOf("https://appassets.androidplatform.net/index.html?a=1"));
        Assert.assertEquals("https://example.com:8443",
                WebViewHost.originOf("https://example.com:8443/app/page"));
        Assert.assertEquals("http://127.0.0.1:8080",
                WebViewHost.originOf("http://127.0.0.1:8080/"));
    }

    @Test
    public void originOfRejectsNonHttpSchemes() {
        Assert.assertNull(WebViewHost.originOf("about:blank"));
        Assert.assertNull(WebViewHost.originOf("data:text/html,<h1>x</h1>"));
        Assert.assertNull(WebViewHost.originOf("file:///android_asset/index.html"));
        Assert.assertNull(WebViewHost.originOf(null));
        Assert.assertNull(WebViewHost.originOf(""));
    }

    @Test
    public void cacheModeIsPreservedByTheBuilder() {
        WebHostConfig config = WebHostConfig.builder()
                .cacheMode(WebHostConfig.CACHE_ONLY)
                .build();
        Assert.assertEquals(WebHostConfig.CACHE_ONLY, config.getCacheMode());
    }

    @Test
    public void permissionHandlerDefaultsToNull() {
        Assert.assertNull(WebHostConfig.builder().build().getPermissionHandler());
    }

    @Test
    public void permissionHandlerIsRetainedByTheBuilder() {
        WebPermissionHandler handler = new WebPermissionHandler() {
            @Override
            public void onPermissionRequest(PermissionRequest request, PermissionResponder responder) {
                responder.deny();
            }

            @Override
            public void onGeolocationRequest(String origin, GeolocationResponder responder) {
                responder.invoke(false, false);
            }
        };
        Assert.assertSame(handler,
                WebHostConfig.builder().permissionHandler(handler).build().getPermissionHandler());
    }

    @Test
    public void securityHandlerDeniesGeolocationWhenEngineIsNotInitialized() {
        AtomicBoolean allow = new AtomicBoolean(true);
        AtomicBoolean retain = new AtomicBoolean(true);

        new SecurityWebPermissionHandler().onGeolocationRequest("https://example.com",
                (a, r) -> {
                    allow.set(a);
                    retain.set(r);
                });

        Assert.assertFalse(allow.get());
        Assert.assertFalse(retain.get());
    }
}
