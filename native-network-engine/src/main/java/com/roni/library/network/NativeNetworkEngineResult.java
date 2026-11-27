package com.roni.library.network;

import java.io.Serializable;

public class NativeNetworkEngineResult<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final T data;
    private final String errorMessage;
    private final int errorCode;

    public NativeNetworkEngineResult(boolean success, T data, String errorMessage, int errorCode) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
    }

    public static <T> NativeNetworkEngineResult<T> success(T data) {
        return new NativeNetworkEngineResult<>(true, data, null, 0);
    }

    public static <T> NativeNetworkEngineResult<T> failure(int errorCode, String errorMessage) {
        return new NativeNetworkEngineResult<>(false, null, errorMessage, errorCode);
    }

    public static <T> NativeNetworkEngineResult<T> failure(String errorMessage) {
        return new NativeNetworkEngineResult<>(false, null, errorMessage, -1);
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "NativeNetworkEngineResult{success=" + success + ", data=" + data +
                ", errorCode=" + errorCode + ", errorMessage='" + errorMessage + "'}";
    }
}
