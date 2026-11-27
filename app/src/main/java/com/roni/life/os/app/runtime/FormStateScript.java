package com.roni.life.os.app.runtime;

import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * JavaScript used to snapshot and repopulate uncommitted form input inside the project WebView.
 *
 * <p>The snapshot is a JSON object of the shape
 * {@code {"url": "...", "ts": 1700000000000, "fields": {"name": "value"}}}. Only user-editable
 * controls are captured: passwords, hidden inputs, file pickers and buttons are skipped so no
 * credential or binary reference is ever written to disk.</p>
 *
 * <p>Restore is deliberately conservative — it only fills controls that are currently
 * <em>empty</em>. A page that has already rendered its own values is never overwritten.</p>
 */
public final class FormStateScript {

    /** Snapshots older than this are ignored on restore. */
    public static final long MAX_AGE_MS = 24L * 60L * 60L * 1000L;

    private FormStateScript() {
    }

    /**
     * Walks every form control and returns the snapshot as a JSON string.
     *
     * <p>Written with single-quoted JS strings so the Java literal needs no escaping.</p>
     */
    public static final String CAPTURE =
            "(function(){try{"
                    + "var out={};"
                    + "var els=document.querySelectorAll('input,textarea,select');"
                    + "for(var i=0;i<els.length;i++){"
                    + "var el=els[i];"
                    + "var key=el.name||el.id;"
                    + "if(!key)continue;"
                    + "var t=(el.type||'').toLowerCase();"
                    + "if(t==='password'||t==='hidden'||t==='file'||t==='submit'"
                    + "||t==='button'||t==='reset'||t==='image')continue;"
                    + "if(t==='checkbox'||t==='radio'){if(el.checked)out[key]=el.value;}"
                    + "else{if(el.value!==''&&el.value!=null)out[key]=el.value;}"
                    + "}"
                    + "var ce=document.querySelectorAll('[contenteditable]');"
                    + "for(var j=0;j<ce.length;j++){"
                    + "var node=ce[j];"
                    + "if(!node.isContentEditable)continue;"
                    + "var ck=node.id||node.getAttribute('data-name');"
                    + "if(ck&&node.innerHTML)out['ce:'+ck]=node.innerHTML;"
                    + "}"
                    + "return JSON.stringify({url:location.href,ts:Date.now(),fields:out});"
                    + "}catch(e){return '';}})()";

    /**
     * Builds the restore script for a stored snapshot.
     *
     * @param snapshot the JSON produced by {@link #CAPTURE}
     * @return a self-invoking script, or {@code null} when the snapshot is unusable
     */
    public static String restore(String snapshot) {
        if (snapshot == null || snapshot.trim().isEmpty()) {
            return null;
        }
        String literal = JSONObject.quote(snapshot);
        return "(function(p){try{"
                + "var d=JSON.parse(p);"
                + "if(!d||!d.fields)return 0;"
                + "if(d.ts&&(Date.now()-d.ts)>" + MAX_AGE_MS + ")return -2;"
                + "var n=0;"
                + "for(var key in d.fields){"
                + "if(!d.fields.hasOwnProperty(key))continue;"
                + "var value=d.fields[key];"
                + "if(key.indexOf('ce:')===0){"
                + "var node=document.getElementById(key.substring(3));"
                + "if(node&&!node.innerHTML){node.innerHTML=value;n++;}"
                + "continue;"
                + "}"
                + "var els=document.getElementsByName(key);"
                + "if(!els||!els.length){var byId=document.getElementById(key);"
                + "if(byId)els=[byId];}"
                + "if(!els||!els.length)continue;"
                + "for(var i=0;i<els.length;i++){"
                + "var el=els[i];"
                + "var t=(el.type||'').toLowerCase();"
                + "if(t==='checkbox'||t==='radio'){"
                + "if(el.value===value&&!el.checked){el.checked=true;"
                + "el.dispatchEvent(new Event('change',{bubbles:true}));n++;}"
                + "}else if(!el.value){"
                + "el.value=value;"
                + "el.dispatchEvent(new Event('input',{bubbles:true}));"
                + "el.dispatchEvent(new Event('change',{bubbles:true}));"
                + "n++;"
                + "}"
                + "}"
                + "}"
                + "return n;"
                + "}catch(e){return -1;}})(" + literal + ")";
    }

    /**
     * Decodes the value delivered to {@code ValueCallback<String>}.
     *
     * <p>{@code evaluateJavascript} hands back a JSON-encoded result, so a script that returns a
     * string arrives wrapped in quotes with its inner quotes escaped. This unwraps it.</p>
     *
     * @return the inner JSON string, or {@code null} when the script failed or returned nothing
     */
    public static String unwrap(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "null".equals(trimmed)) {
            return null;
        }
        try {
            Object parsed = new JSONTokener(trimmed).nextValue();
            if (parsed instanceof String) {
                String json = (String) parsed;
                return json.isEmpty() ? null : json;
            }
        } catch (Throwable ignored) {
            // Not a JSON string — treat as unusable.
        }
        return null;
    }

    /** @return the {@code url} field of a snapshot, or an empty string */
    public static String urlOf(String snapshot) {
        if (snapshot == null) {
            return "";
        }
        try {
            return new JSONObject(snapshot).optString("url", "");
        } catch (Throwable t) {
            return "";
        }
    }
}
