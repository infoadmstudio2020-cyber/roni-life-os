#!/usr/bin/env python3
"""RONi LiFE OS — স্ট্যাটিক ভ্যালিডেশন।

Android SDK / Gradle ছাড়াই যাচাই করে:
  1. Java ফাইলের গঠন (ব্রেস/বন্ধনী ভারসাম্য, package ↔ ডিরেক্টরি, ক্লাস নাম ↔ ফাইল নাম)
  2. Java-তে ব্যবহৃত প্রতিটি R.* রেফারেন্স res/ এ বিদ্যমান কি না
  3. XML-এ ব্যবহৃত প্রতিটি @type/name রেফারেন্স বিদ্যমান কি না
  4. AndroidManifest-এর প্রতিটি Activity ক্লাস বিদ্যমান কি না
  5. settings.gradle-এ ঘোষিত মডিউল ডিরেক্টরি বিদ্যমান কি না
"""

import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app")
RES = os.path.join(APP, "src", "main", "res")
JAVA = os.path.join(APP, "src", "main", "java")

errors = []
warnings = []


def fail(message):
    errors.append(message)


def warn(message):
    warnings.append(message)


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def strip_java(source):
    """স্ট্রিং ও কমেন্ট বাদ দিয়ে কোড বের করে।"""
    out = []
    i = 0
    n = len(source)
    while i < n:
        ch = source[i]
        if ch == '"':
            i += 1
            while i < n:
                if source[i] == "\\":
                    i += 2
                    continue
                if source[i] == '"':
                    i += 1
                    break
                i += 1
            continue
        if ch == "'":
            i += 1
            while i < n:
                if source[i] == "\\":
                    i += 2
                    continue
                if source[i] == "'":
                    i += 1
                    break
                i += 1
            continue
        if ch == "/" and i + 1 < n and source[i + 1] == "/":
            while i < n and source[i] != "\n":
                i += 1
            continue
        if ch == "/" and i + 1 < n and source[i + 1] == "*":
            i += 2
            while i + 1 < n and not (source[i] == "*" and source[i + 1] == "/"):
                i += 1
            i += 2
            continue
        out.append(ch)
        i += 1
    return "".join(out)


# ---------- ১. Java গঠন ----------
java_files = []
for base, _, files in os.walk(JAVA):
    for name in files:
        if name.endswith(".java"):
            java_files.append(os.path.join(base, name))

for path in java_files:
    rel = os.path.relpath(path, JAVA)
    source = read(path)
    code = strip_java(source)

    for opener, closer, label in (("{", "}", "brace"), ("(", ")", "paren"), ("[", "]", "bracket")):
        if code.count(opener) != code.count(closer):
            fail(f"{rel}: unbalanced {label} {code.count(opener)} vs {code.count(closer)}")

    package_match = re.search(r"^\s*package\s+([\w.]+)\s*;", source, re.MULTILINE)
    if not package_match:
        fail(f"{rel}: missing package declaration")
    else:
        expected_dir = package_match.group(1).replace(".", os.sep)
        if not os.path.dirname(rel).endswith(expected_dir):
            fail(f"{rel}: package {package_match.group(1)} does not match directory")

    stem = os.path.splitext(os.path.basename(path))[0]
    if not re.search(r"\b(class|interface|enum)\s+" + re.escape(stem) + r"\b", code):
        fail(f"{rel}: no type named {stem}")

# ---------- ২. res ইনডেক্স ----------
def xml_names(path, tag, attr="name"):
    names = set()
    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        fail(f"{os.path.relpath(path, ROOT)}: XML parse error {exc}")
        return names
    for node in tree.getroot().iter(tag):
        value = node.get(attr)
        if value:
            names.add(value)
    return names


def file_stems(folder):
    result = set()
    full = os.path.join(RES, folder)
    if not os.path.isdir(full):
        return result
    for name in os.listdir(full):
        if name.endswith(".xml"):
            result.add(os.path.splitext(name)[0])
    return result


index = {
    "id": set(),
    "layout": file_stems("layout"),
    "menu": file_stems("menu"),
    "drawable": file_stems("drawable") | file_stems("mipmap-anydpi-v26"),
    "string": xml_names(os.path.join(RES, "values", "strings.xml"), "string"),
    "color": set(),
    "dimen": xml_names(os.path.join(RES, "values", "dimens.xml"), "dimen"),
    "style": xml_names(os.path.join(RES, "values", "themes.xml"), "style"),
}

values_dir = os.path.join(RES, "values")
if os.path.isdir(values_dir):
    for name in os.listdir(values_dir):
        if name.endswith(".xml"):
            index["color"] |= xml_names(os.path.join(values_dir, name), "color")

for folder in ("layout", "menu"):
    full = os.path.join(RES, folder)
    if not os.path.isdir(full):
        continue
    for name in os.listdir(full):
        if not name.endswith(".xml"):
            continue
        try:
            tree = ET.parse(os.path.join(full, name))
        except ET.ParseError as exc:
            fail(f"res/{folder}/{name}: XML parse error {exc}")
            continue
        for node in tree.getroot().iter():
            node_id = node.get("{http://schemas.android.com/apk/res/android}id")
            if node_id and node_id.startswith("@+id/"):
                index["id"].add(node_id[len("@+id/"):])

# ---------- ৩. Java-র R.* রেফারেন্স ----------
for path in java_files:
    rel = os.path.relpath(path, JAVA)
    source = read(path)
    for kind, name in re.findall(r"(?<!android\.)\bR\.(\w+)\.(\w+)\b", source):
        if kind not in index:
            warn(f"{rel}: unknown R namespace R.{kind}")
            continue
        if name not in index[kind]:
            fail(f"{rel}: R.{kind}.{name} not found in res/")

# ---------- ৪. XML-এর @type/name রেফারেন্স ----------
ref_pattern = re.compile(r"@(string|drawable|color|style|dimen|layout|menu|mipmap)/([\w.]+)")

# লাইব্রেরি (Material / AndroidX) থেকে আসা রিসোর্স — লোকাল res/ এ থাকবে না।
EXTERNAL_STRINGS = {"appbar_scrolling_view_behavior"}
for base, _, files in os.walk(RES):
    for name in files:
        if not name.endswith(".xml"):
            continue
        path = os.path.join(base, name)
        rel = os.path.relpath(path, ROOT)
        try:
            ET.parse(path)
        except ET.ParseError as exc:
            fail(f"{rel}: XML parse error {exc}")
            continue
        for kind, ref in ref_pattern.findall(read(path)):
            lookup = "drawable" if kind == "mipmap" else kind
            if lookup not in index or ref in index[lookup]:
                continue
            if kind == "string" and ref in EXTERNAL_STRINGS:
                continue
            if kind == "style" and "." in ref:
                # Widget.MaterialComponents.* / ThemeOverlay.* ইত্যাদি লাইব্রেরি স্টাইল
                continue
            fail(f"{rel}: @{kind}/{ref} not found")

# ---------- ৫. Manifest ----------
manifest = os.path.join(APP, "src", "main", "AndroidManifest.xml")
try:
    tree = ET.parse(manifest)
except ET.ParseError as exc:
    fail(f"AndroidManifest.xml: XML parse error {exc}")
    tree = None

if tree is not None:
    android_ns = "{http://schemas.android.com/apk/res/android}"
    application = tree.getroot().find("application")
    if application is None:
        fail("AndroidManifest.xml: missing <application>")
    else:
        app_name = application.get(android_ns + "name")
        if app_name and app_name.startswith("."):
            app_name = "com.roni.life.os.app" + app_name
        if app_name:
            expected = os.path.join(JAVA, app_name.replace(".", os.sep) + ".java")
            if not os.path.isfile(expected):
                fail(f"AndroidManifest.xml: application class {app_name} not found")

        launchers = 0
        for activity in application.findall("activity"):
            activity_name = activity.get(android_ns + "name")
            if activity_name and activity_name.startswith("."):
                activity_name = "com.roni.life.os.app" + activity_name
            expected = os.path.join(JAVA, activity_name.replace(".", os.sep) + ".java")
            if not os.path.isfile(expected):
                fail(f"AndroidManifest.xml: activity {activity_name} not found")
            for intent_filter in activity.findall("intent-filter"):
                actions = [a.get(android_ns + "name") for a in intent_filter.findall("action")]
                if "android.intent.action.MAIN" in actions:
                    launchers += 1
        if launchers != 1:
            fail(f"AndroidManifest.xml: expected exactly 1 launcher activity, found {launchers}")

# ---------- ৬. Gradle মডিউল ----------
settings = os.path.join(ROOT, "settings.gradle")
if os.path.isfile(settings):
    for module in re.findall(r"include\s+':([\w-]+)'", read(settings)):
        if not os.path.isdir(os.path.join(ROOT, module)):
            fail(f"settings.gradle: module :{module} has no directory")

app_gradle = os.path.join(APP, "build.gradle")
if os.path.isfile(app_gradle):
    gradle_text = read(app_gradle)
    namespace = re.search(r"namespace\s+'([\w.]+)'", gradle_text)
    if not namespace:
        fail("app/build.gradle: missing namespace")
    elif namespace.group(1) != "com.roni.life.os.app":
        fail(f"app/build.gradle: namespace {namespace.group(1)} != com.roni.life.os.app")
    for module in re.findall(r"project\(':([\w-]+)'\)", gradle_text):
        if not os.path.isdir(os.path.join(ROOT, module)):
            fail(f"app/build.gradle: dependency :{module} has no directory")

# ---------- ফলাফল ----------
print(f"Java files      : {len(java_files)}")
print(f"Resource ids    : {len(index['id'])}")
print(f"Layouts         : {len(index['layout'])}")
print(f"Strings         : {len(index['string'])}")
print(f"Errors          : {len(errors)}")
print(f"Warnings        : {len(warnings)}")
for message in errors:
    print("  ERROR  " + message)
for message in warnings:
    print("  WARN   " + message)
sys.exit(1 if errors else 0)
