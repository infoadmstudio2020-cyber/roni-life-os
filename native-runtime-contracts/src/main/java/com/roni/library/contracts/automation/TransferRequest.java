package com.roni.library.contracts.automation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A download or upload request handled by the transfer engine. */
public final class TransferRequest {

    public static final String DIRECTION_DOWNLOAD = "DOWNLOAD";
    public static final String DIRECTION_UPLOAD = "UPLOAD";

    private final String id;
    private final String direction;
    private final String url;
    private final String localPath;
    private final Map<String, String> headers;
    private final long expectedBytes;

    private TransferRequest(Builder builder) {
        this.id = builder.id;
        this.direction = builder.direction;
        this.url = builder.url;
        this.localPath = builder.localPath;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
        this.expectedBytes = builder.expectedBytes;
    }

    public String getId() {
        return id;
    }

    public String getDirection() {
        return direction;
    }

    public String getUrl() {
        return url;
    }

    public String getLocalPath() {
        return localPath;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public long getExpectedBytes() {
        return expectedBytes;
    }

    public boolean isDownload() {
        return DIRECTION_DOWNLOAD.equals(direction);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("direction", direction);
        map.put("url", url);
        map.put("localPath", localPath);
        map.put("headers", headers);
        map.put("expectedBytes", expectedBytes);
        return map;
    }

    public static final class Builder {
        private String id = "";
        private String direction = DIRECTION_DOWNLOAD;
        private String url = "";
        private String localPath = "";
        private Map<String, String> headers = new LinkedHashMap<>();
        private long expectedBytes;

        public Builder id(String value) {
            this.id = value;
            return this;
        }

        public Builder direction(String value) {
            this.direction = value;
            return this;
        }

        public Builder url(String value) {
            this.url = value;
            return this;
        }

        public Builder localPath(String value) {
            this.localPath = value;
            return this;
        }

        public Builder headers(Map<String, String> value) {
            this.headers = value == null ? new LinkedHashMap<>() : value;
            return this;
        }

        public Builder expectedBytes(long value) {
            this.expectedBytes = value;
            return this;
        }

        public TransferRequest build() {
            return new TransferRequest(this);
        }
    }
}
