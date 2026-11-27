#!/usr/bin/env python3
"""Extract every bridge action from Java handlers, with params/errors, and cross-check registration."""
import json, re, os

ROOT = "/workspace/app-fu3r65ukcnwh/tasks/extracted/RONiLiFEOS"

HANDLERS = {
    "ui":      "native-runtime-ui/src/main/java/com/roni/library/ui/handler/NativeUICommandHandler.java",
    "web":     "native-runtime-ui/src/main/java/com/roni/library/ui/handler/NativeWebCommandHandler.java",
    "device":  "native-runtime-ui/src/main/java/com/roni/library/ui/handler/NativeDeviceCommandHandler.java",
    "storage": "native-runtime-ui/src/main/java/com/roni/library/ui/handler/NativeStorageCommandHandler.java",
    "runtime": "native-runtime-engine/src/main/java/com/roni/library/runtime/handler/RuntimeCommandHandler.java",
    "bridge":  "native-web-bridge-sdk/src/main/java/com/roni/library/bridge/handler/BridgeCommandHandler.java",
    "echo":    "native-router-sdk/src/main/java/com/roni/library/router/handler/EchoCommandHandler.java",
    "system":  "native-router-sdk/src/main/java/com/roni/library/router/handler/SystemDiagnosticHandler.java",
}

UNREGISTERED = {
    "theme":          "native-theme-engine/src/main/java/com/roni/library/theme/handler/ThemeCommandHandler.java",
    "theme_legacy":   "native-theme-engine/src/main/java/com/roni/library/themeengine/handler/ThemeCommandHandler.java",
    "network":        "native-network-engine/src/main/java/com/roni/library/network/handler/NetworkCommandHandler.java",
    "platform":       "native-platform-engine/src/main/java/com/roni/library/platform/handler/PlatformCommandHandler.java",
    "security":       "native-security-engine/src/main/java/com/roni/library/security/handler/SecurityCommandHandler.java",
    "analytics":      "native-analytics-engine/src/main/java/com/roni/library/analytics/handler/AnalyticsCommandHandler.java",
    "devtools":       "native-dev-tools-sdk/src/main/java/com/roni/library/devtools/handler/DevToolsCommandHandler.java",
    "logger":         "native-logger-sdk/src/main/java/com/roni/library/logger/handler/LoggerCommandHandler.java",
    "service":        "native-service-sdk/src/main/java/com/roni/library/service/handler/ServiceCommandHandler.java",
    "storage_engine": "native-storage-engine/src/main/java/com/roni/library/storage/handler/StorageCommandHandler.java",
}

def read(rel):
    p = os.path.join(ROOT, rel)
    return open(p, encoding="utf-8").read() if os.path.exists(p) else None

def brace_block(src, start_idx):
    """Return substring of the {...} block starting at first '{' after start_idx."""
    i = src.find("{", start_idx)
    if i < 0:
        return ""
    depth, j = 0, i
    while j < len(src):
        if src[j] == "{": depth += 1
        elif src[j] == "}":
            depth -= 1
            if depth == 0: return src[i:j+1]
        j += 1
    return src[i:]

def action_switch(src):
    """Body of the switch that dispatches on the request action."""
    if not src: return ""
    m = re.search(r"switch\s*\(\s*(?:normalizeAction\s*\(\s*)?(?:request\.getAction\(\)|action)\s*\)?\s*\)", src)
    if not m:
        m = re.search(r"switch\s*\(\s*action\s*\)", src)
    if not m: return ""
    return brace_block(src, m.start())

def cases_in(block):
    return sorted(set(re.findall(r'case\s+"([a-zA-Z0-9_.]+)"\s*:', block)))

def supported_actions(src):
    if not src: return []
    m = re.search(r"SUPPORTED_ACTIONS\s*=\s*(.*?);\s*\n", src, re.S)
    if not m: return []
    return sorted(set(re.findall(r'"([a-zA-Z0-9_.]+)"', m.group(1))))

def get_path(src):
    if not src: return None
    m = re.search(r'getPath\s*\(\s*\)\s*\{[^}]*?return\s+"([^"]+)"', src, re.S)
    return m.group(1) if m else None

def params_of(block):
    names = set()
    for pat in (r'getStringParam\s*\(\s*"([^"]+)"', r'getIntParam\s*\(\s*"([^"]+)"',
                r'getBooleanParam\s*\(\s*"([^"]+)"', r'getLongParam\s*\(\s*"([^"]+)"',
                r'getDoubleParam\s*\(\s*"([^"]+)"', r'getObjectParam\s*\(\s*"([^"]+)"',
                r'getArrayParam\s*\(\s*"([^"]+)"', r'optString\s*\(\s*"([^"]+)"',
                r'optInt\s*\(\s*"([^"]+)"', r'optBoolean\s*\(\s*"([^"]+)"',
                r'hasParam\s*\(\s*"([^"]+)"', r'getParam\s*\(\s*"([^"]+)"'):
        names.update(re.findall(pat, block))
    return sorted(names)

def errors_of(block):
    return sorted(set(re.findall(r'NativeApiError\s*\(\s*"([A-Z0-9_]+)"', block)))

def per_action(block):
    """Split the action switch into per-case chunks."""
    out = {}
    marks = [(m.start(), m.group(1)) for m in re.finditer(r'case\s+"([a-zA-Z0-9_.]+)"\s*:', block)]
    for idx, (pos, name) in enumerate(marks):
        end = marks[idx+1][0] if idx+1 < len(marks) else len(block)
        chunk = block[pos:end]
        out[name] = {"params": params_of(chunk), "errors": errors_of(chunk)}
    return out

def analyse(rel):
    src = read(rel)
    if src is None:
        return {"file": rel, "exists": False, "supported": [], "cases": [], "path": None, "actions": {}}
    block = action_switch(src)
    sup, cases = supported_actions(src), cases_in(block)
    return {
        "file": rel, "exists": True, "path": get_path(src),
        "supported": sup, "cases": cases,
        "supported_count": len(sup), "case_count": len(cases),
        "in_supported_not_in_switch": sorted(set(sup) - set(cases)),
        "in_switch_not_in_supported": sorted(set(cases) - set(sup)),
        "actions": per_action(block),
    }

def main():
    out = {"handlers": {}, "unregistered": {}}
    for name, rel in HANDLERS.items():
        out["handlers"][name] = analyse(rel)
    for name, rel in UNREGISTERED.items():
        out["unregistered"][name] = analyse(rel)

    ui = read("native-runtime-ui/src/main/java/com/roni/library/ui/NativeRuntimeUI.java") or ""
    out["registered_handlers"] = re.findall(r"registerHandler\((\w+)\)", ui)
    out["total_registered_actions"] = sum(h["supported_count"] for h in out["handlers"].values())

    json.dump(out, open("actions.json", "w", encoding="utf-8"), indent=2, ensure_ascii=False)

    print(f"{'handler':10} {'path':10} {'supported':>9} {'cases':>6} {'dead':>5} {'hidden':>7}")
    for name, h in out["handlers"].items():
        print(f"{name:10} {str(h['path']):10} {h['supported_count']:>9} {h['case_count']:>6} "
              f"{len(h['in_supported_not_in_switch']):>5} {len(h['in_switch_not_in_supported']):>7}")
    print(f"{'TOTAL':10} {'':10} {out['total_registered_actions']:>9}")
    print("\nregistered handlers:", out["registered_handlers"])
    print("\n--- unregistered modules ---")
    for name, h in out["unregistered"].items():
        print(f"{name:15} path={str(h['path']):10} supported={h['supported_count']:>3} cases={h['case_count']:>3}")
    print("\n--- dead routes ---")
    for name, h in out["handlers"].items():
        if h["in_supported_not_in_switch"]:
            print(f"  {name}: {h['in_supported_not_in_switch']}")
    print("\n--- hidden actions (case, not in SUPPORTED_ACTIONS) ---")
    for name, h in out["handlers"].items():
        if h["in_switch_not_in_supported"]:
            print(f"  {name}: {h['in_switch_not_in_supported']}")

if __name__ == "__main__":
    main()
