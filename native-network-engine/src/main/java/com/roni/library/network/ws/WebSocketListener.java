package com.roni.library.network.ws;

/**
 * Callback surface for {@link WebSocketClient}.
 *
 * <p>{@link #onPing(WebSocketClient, byte[])} and {@link #onPong(WebSocketClient, byte[])} are
 * default no-ops so existing implementations keep compiling; the client always answers a ping
 * with a pong regardless of whether the listener observes it.</p>
 */
public interface WebSocketListener {
    void onOpen(WebSocketClient ws);
    void onMessage(WebSocketClient ws, String text);
    void onMessage(WebSocketClient ws, byte[] bytes);
    void onClosing(WebSocketClient ws, int code, String reason);
    void onClosed(WebSocketClient ws, int code, String reason);
    void onFailure(WebSocketClient ws, Throwable t);

    default void onPing(WebSocketClient ws, byte[] payload) {
    }

    default void onPong(WebSocketClient ws, byte[] payload) {
    }
}
