package com.roni.library.ui.web;

/**
 * Receives WebView diagnostics for the Runtime Inspector.
 *
 * <p>Declared in the UI library rather than in the app so the library keeps no dependency on
 * the app's inspector package: the app registers an implementation, the library only reports.
 * Every callback is invoked on the WebView thread and must return quickly — implementations
 * are expected to hand the event to a buffer and return.</p>
 */
public interface WebDiagnosticsListener {

    /**
     * A console message from page JavaScript.
     *
     * @param priority   {@code android.util.Log} priority (2=verbose … 7=assert)
     * @param message    the console text
     * @param sourceId   script URL, may be empty
     * @param lineNumber line number, or -1 when unknown
     */
    void onConsoleMessage(int priority, String message, String sourceId, int lineNumber);

    /**
     * A page lifecycle event.
     *
     * @param event one of {@code PAGE_STARTED}, {@code PAGE_FINISHED}, {@code RENDERER_GONE}
     * @param url   the page URL, may be empty
     */
    void onPageEvent(String event, String url);

    /**
     * A resource or navigation failure.
     *
     * @param url         the failing URL
     * @param description human-readable reason
     * @param mainFrame   true when the failure is for the main document
     */
    void onResourceError(String url, String description, boolean mainFrame);
}
