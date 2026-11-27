package com.roni.library.ui.web;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * A single resolved web resource (HTML, JS, CSS, image, font, ...).
 */
public final class WebResource {

    private final InputStream stream;
    private final String mimeType;
    private final String encoding;
    private final long contentLength;
    private final int statusCode;
    private final String reasonPhrase;
    private final Map<String, String> headers;

    private WebResource(InputStream stream, String mimeType, String encoding, long contentLength,
                        int statusCode, String reasonPhrase, Map<String, String> headers) {
        this.stream = stream;
        this.mimeType = mimeType != null ? mimeType : "application/octet-stream";
        this.encoding = encoding;
        this.contentLength = contentLength;
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase != null ? reasonPhrase : "";
        this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
    }

    public static WebResource of(InputStream stream, String mimeType, long contentLength) {
        return new WebResource(stream, mimeType, isText(mimeType) ? "UTF-8" : null,
                contentLength, 200, "OK", new HashMap<>());
    }

    public static WebResource text(String content, String mimeType) {
        byte[] bytes = (content != null ? content : "").getBytes(StandardCharsets.UTF_8);
        Map<String, String> h = new HashMap<>();
        h.put("Content-Type", mimeType != null ? mimeType : "text/plain");
        return new WebResource(new ByteArrayInputStream(bytes), mimeType, "UTF-8",
                bytes.length, 200, "OK", h);
    }

    public static WebResource notFound() {
        byte[] bytes = "404 Not Found".getBytes(StandardCharsets.UTF_8);
        return new WebResource(new ByteArrayInputStream(bytes), "text/plain", "UTF-8",
                bytes.length, 404, "Not Found", new HashMap<>());
    }

    private static boolean isText(String mimeType) {
        if (mimeType == null) return false;
        String m = mimeType.toLowerCase();
        return m.startsWith("text/")
                || m.contains("javascript")
                || m.contains("json")
                || m.contains("xml")
                || m.contains("svg");
    }

    public InputStream getStream() { return stream; }
    public String getMimeType() { return mimeType; }
    public String getEncoding() { return encoding; }
    public long getContentLength() { return contentLength; }
    public int getStatusCode() { return statusCode; }
    public String getReasonPhrase() { return reasonPhrase; }
    public Map<String, String> getHeaders() { return new HashMap<>(headers); }
    public boolean isFound() { return statusCode >= 200 && statusCode < 400; }
}
