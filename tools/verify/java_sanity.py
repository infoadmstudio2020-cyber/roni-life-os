#!/usr/bin/env python3
"""Compile-free structural checks for edited Java files (no javac in this environment)."""
import re, sys, os

FILES = [
 "native-storage-engine/src/main/java/com/roni/library/storage/StorageScope.java",
 "native-runtime-ui/src/main/java/com/roni/library/ui/handler/NativeStorageCommandHandler.java",
 "native-runtime-ui/src/main/java/com/roni/library/ui/handler/NativeWebCommandHandler.java",
 "native-runtime-ui/src/main/java/com/roni/library/ui/host/NativeUIHost.java",
 "native-runtime-ui/src/main/java/com/roni/library/ui/NativeRuntimeUI.java",
 "native-theme-engine/src/main/java/com/roni/library/themeengine/handler/ThemeCommandHandler.java",
]
ROOT = "/workspace/app-fu3r65ukcnwh/tasks/extracted/RONiLiFEOS"

def strip(src):
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if c == '"' or c == "'":
            q = c; i += 1
            while i < n:
                if src[i] == '\\': i += 2; continue
                if src[i] == q: i += 1; break
                i += 1
            out.append('""')
        elif src.startswith("//", i):
            j = src.find("\n", i); i = n if j < 0 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i); i = n if j < 0 else j + 2
        else:
            out.append(c); i += 1
    return "".join(out)

fails = 0
for rel in FILES:
    p = os.path.join(ROOT, rel)
    src = open(p, encoding="utf-8").read()
    code = strip(src)
    name = os.path.basename(rel)
    problems = []
    for open_c, close_c in (("{", "}"), ("(", ")"), ("[", "]")):
        if code.count(open_c) != code.count(close_c):
            problems.append(f"{open_c}{close_c} imbalance {code.count(open_c)}/{code.count(close_c)}")
    # package + public type name must match file name
    pkg = re.search(r"^\s*package\s+([\w.]+);", code, re.M)
    if not pkg: problems.append("no package")
    if not re.search(r"public\s+(final\s+)?(class|interface|enum)\s+" + name[:-5] + r"\b", code):
        problems.append(f"public type {name[:-5]} not found")
    # duplicate method signatures at class level (rough)
    sigs = re.findall(r"\n\s{4}(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?[\w<>\[\],.?\s]+?\s+(\w+)\s*\(([^)]*)\)\s*\{", code)
    seen = {}
    for m, args in sigs:
        key = (m, re.sub(r"\s+", "", args))
        seen[key] = seen.get(key, 0) + 1
    for k, v in seen.items():
        if v > 1: problems.append(f"duplicate method {k[0]}({k[1]}) x{v}")
    status = "OK  " if not problems else "FAIL"
    if problems: fails += 1
    print(f"{status} {name:34} {len(src.splitlines()):>4} lines  {'; '.join(problems)}")

print(f"\n{fails} file(s) with structural problems")
sys.exit(1 if fails else 0)
