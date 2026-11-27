package com.roni.library.bridge;

/**
 * Observes bridge traffic for the Runtime Inspector.
 *
 * <p>Deliberately payload-free: the callbacks carry the request id, action, route and outcome
 * but never the message body, so a token or password sent through the bridge cannot reach the
 * log buffer through this path.</p>
 *
 * <p>Called on the WebView thread. Implementations must return immediately.</p>
 */
public interface BridgeTraceListener {

    /** A request was accepted and is about to be dispatched. */
    void onRequest(String requestId, String action, String route);

    /**
     * A request completed.
     *
     * @param durationMs elapsed time, or -1 when unknown
     * @param success    true when the handler returned a response
     * @param error      failure reason when {@code success} is false, otherwise null
     */
    void onResponse(String requestId, String action, long durationMs, boolean success,
                    String error);

    /** A message was rejected before dispatch (origin, size or validation). */
    void onRejected(String requestId, String action, String reason);

    /** A native-to-web event was emitted. */
    void onEvent(String topic);
}
