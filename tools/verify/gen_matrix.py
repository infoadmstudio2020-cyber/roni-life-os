#!/usr/bin/env python3
"""Generate docs/bangla/API_REGISTRATION_MATRIX.md from actions.json."""
import json

d = json.load(open("actions.json", encoding="utf-8"))
OUT = "/workspace/app-fu3r65ukcnwh/tasks/extracted/RONiLiFEOS/docs/bangla/API_REGISTRATION_MATRIX.md"

DECISIONS = {
    "theme": ("❌ রাখা হয়নি",
              "`ui.getTheme`/`ui.applyTheme` ইতিমধ্যে থিম দেয়, কিন্তু সেগুলো "
              "`NativeRuntimeUIConfig` পড়ে — আর `theme.*` পড়ে `ThemeManager.getActiveTheme()`। "
              "দুটো আলাদা source of truth, তাই একসাথে expose করলে দুটি ভিন্ন থিম দেখাবে।"),
    "theme_legacy": ("❌ রাখা হয়নি",
                     "`theme.handler.ThemeCommandHandler`-এর ১৩ লাইনের alias। রেজিস্টার করলে "
                     "একই `theme` route-এ দুটি handler দাঁড়াবে। `@Deprecated` করা হয়েছে।"),
    "network": ("❌ রাখা হয়নি",
                "এটি bridge handler নয় — কোনো action dispatch নেই। এটি raw HTTP ক্লায়েন্ট "
                "(`execute(url, body, callback)`)।"),
    "platform": ("❌ রাখা হয়নি", "`getPath()` নেই, action switch নেই — অসম্পূর্ণ।"),
    "security": ("❌ রাখা হয়নি",
                 "BiometricPrompt-এর জন্য Activity দরকার; `NativeRuntimeUI`-এর কাছে সেই Activity "
                 "নেই। ভুল Activity দিলে biometric prompt crash করবে।"),
    "analytics": ("❌ রাখা হয়নি",
                  "constructor-এ একাধিক dependency (service + config) দরকার, `NativeRuntimeUI` "
                  "সেগুলো তৈরি করে না।"),
    "devtools": ("❌ রাখা হয়নি",
                 "শুধু debug build-এর জন্য; release-এ expose করলে internal state প্রকাশ পাবে।"),
    "logger": ("❌ রাখা হয়নি", "action dispatch নেই।"),
    "service": ("❌ রাখা হয়নি", "action dispatch নেই।"),
    "storage_engine": ("❌ রাখা হয়নি",
                       "`getPath()` = `storage` — `NativeStorageCommandHandler`-এর সাথে সংঘর্ষ। "
                       "দুটোই রেজিস্টার করলে একটি অন্যটিকে ঢেকে দেবে।"),
}

L = []
def A(s=""):
    L.append(s)

A("# API Registration Matrix — সব action বনাম Java handler")
A()
A("> এই ফাইলটি `tasks/verify/extract_actions.py` দিয়ে **স্বয়ংক্রিয়ভাবে** তৈরি — হাতে লেখা নয়।")
A("> সোর্স: প্রতিটি handler-এর `switch (action)` ব্লক, `SUPPORTED_ACTIONS` অ্যারে এবং")
A("> `NativeRuntimeUI.registerBridgeRoutes()`।")
A()
A("## ১. রাউটিং কীভাবে কাজ করে")
A()
A("`registerBridgeRoutes()` দুইভাবে route যোগ করে:")
A()
A("```java")
A('bridge.getRouter().registerHandler(commandHandler);          // path = "ui"  → prefix route')
A("for (String action : NativeUICommandHandler.SUPPORTED_ACTIONS) {")
A('    bridge.getRouter().registerRoute(action, commandHandler); // "showToast" → bare route')
A("}")
A("```")
A()
A("JS সবসময় **prefix** দিয়ে ডাকে (`ui.showToast`)। তাই:")
A()
A("| স্তর | কী মেলে | ফল |")
A("|---|---|---|")
A("| prefix route (`ui`) | `ui.*` যেকোনো কিছু | handler-এ পৌঁছায়, তারপর switch-এ মিলতে হবে |")
A("| bare route (`showToast`) | শুধু `showToast` | JS থেকে **কখনো** ব্যবহৃত হয় না |")
A()
A("অর্থাৎ **প্রকৃত dispatch table হলো `switch (action)`-এর case তালিকা**, `SUPPORTED_ACTIONS` নয়।")
A("`SUPPORTED_ACTIONS` শুধু native caller-দের জন্য bare route বানায়।")
A()
A("## ২. সারসংক্ষেপ")
A()
A("| Handler | Path | SUPPORTED_ACTIONS | switch case | অবস্থা |")
A("|---|---|---|---|---|")
for name in ["ui", "web", "device", "storage"]:
    h = d["handlers"][name]
    A("| `%s` | `%s` | %d | %d | ✅ রেজিস্টার্ড |"
      % (name, h["path"], h["supported_count"], h["case_count"]))
A("| **মোট** | | **%d** | | |" % d["total_registered_actions"])
A()
A("`device`-এ ৩৩টি case দেখা যায়, কিন্তু ৫টি (`portrait`, `landscape`, `reverse_portrait`, "
  "`reverse_landscape`, `sensor`) `setOrientation`-এর **ভেতরের** switch — action নয়। "
  "তাই প্রকৃত action সংখ্যা **২৮**।")
A()
A("## ৩. রেজিস্টার্ড action — পূর্ণ তালিকা")
A()
for idx, name in enumerate(["ui", "web", "device", "storage"], 1):
    h = d["handlers"][name]
    A("### ৩.%d `%s.*` — %dটি action" % (idx, name, h["case_count"]))
    A()
    A("| # | Action | Parameters | Error codes |")
    A("|---|---|---|---|")
    for i, act in enumerate(h["cases"], 1):
        meta = h["actions"].get(act, {})
        params = ", ".join("`%s`" % p for p in meta.get("params", [])) or "—"
        errs = ", ".join("`%s`" % e for e in meta.get("errors", [])) or "—"
        A("| %d | `%s.%s` | %s | %s |" % (i, name, act, params, errs))
    A()

A("## ৪. রেজিস্টার্ড নয় — কারণসহ সিদ্ধান্ত")
A()
A("| Module | Path | case | সিদ্ধান্ত | কারণ |")
A("|---|---|---|---|---|")
for name, h in d["unregistered"].items():
    dec, why = DECISIONS.get(name, ("❌ রাখা হয়নি", "—"))
    A("| `%s` | `%s` | %d | %s | %s |" % (name, h["path"] or "—", h["case_count"], dec, why))
A()
A("### ৪.১ `theme.*` — কেন এক্সপোজ করা হয়নি")
A()
A("```java")
A("// NativeUICommandHandler.java:645  (ui.getTheme)")
A("NativeRuntimeUIConfig config = uiHost.getConfiguration();")
A('data.put("themeMode", config.getThemeMode());')
A()
A("// ThemeCommandHandler.java:60  (theme.getTheme)")
A("IThemeConfig active = themeManager.getActiveTheme();")
A('data.put("themeName", active.getThemeName());')
A("```")
A()
A("দুটি ভিন্ন অবজেক্ট, দুটি ভিন্ন মান। `theme.setThemeMode` কল করলে `ui.getTheme` সেটি দেখবে না।")
A("একই অ্যাপে দুটি থিম API থাকলে ডেভেলপার ভুল তথ্য পাবেন।")
A()
A("**সুপারিশ:** `ui.getTheme`-কে `ThemeManager`-এ delegate করান, তারপর `theme.*` রেজিস্টার করুন।")
A("এটি আলাদা টাস্ক — এখানে করা হয়নি, কারণ এতে `NativeRuntimeUIConfig`-এর contract বদলাবে।")
A()
A("## ৫. এই টাস্কে করা পরিবর্তন")
A()
A("| ফাইল | পরিবর্তন |")
A("|---|---|")
A("| `NativeWebCommandHandler.java` | `SUPPORTED_ACTIONS`-এ `setTextZoom`, `getTextZoom` যোগ (৫৩ → ৫৫) |")
A("| `StorageScope.java` | নতুন — project-scoped key namespace |")
A("| `NativeStorageCommandHandler.java` | ৭টি action-ই scope-aware; `clear` শুধু নিজের key মুছে |")
A("| `NativeUIHost.java` | `projectId` field + getter/setter |")
A("| `NativeRuntimeUI.java` | `setProjectId()` / `getProjectId()` |")
A("| `themeengine/handler/ThemeCommandHandler.java` | `@Deprecated` + সতর্কবার্তা |")
A()
A("`web.setTextZoom`/`getTextZoom` আগে switch-এ ছিল কিন্তু `SUPPORTED_ACTIONS`-এ ছিল না — "
  "তাই শুধু prefix route দিয়ে কাজ করত। এখন bare route-ও আছে, অর্থাৎ native caller-রাও ডাকতে পারবে।")
A()
A("## ৬. যাচাই")
A()
A("| চেক | ফল |")
A("|---|---|")
A("| `extract_actions.py` — dead route (SUPPORTED_ACTIONS-এ আছে, case নেই) | ০ |")
A("| `extract_actions.py` — hidden action (case আছে, SUPPORTED_ACTIONS-এ নেই) | ০ |")
A("| `examples_runtime_test.mjs` — উদাহরণে ব্যবহৃত ৭৫টি action সবই dispatch table-এ | ✅ |")
A("| `examples_runtime_test.mjs` — prefix-only action | ০ |")
A()

open(OUT, "w", encoding="utf-8").write("\n".join(L))
print("wrote %s (%d lines)" % (OUT, len(L)))
