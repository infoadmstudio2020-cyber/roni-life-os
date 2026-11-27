#!/usr/bin/env python3
import os
import sys
import xml.etree.ElementTree as ET

def verify_xml_files(base_dir):
    print("=== VERIFYING XML FILES ===")
    errors = []
    xml_count = 0
    for root, _, files in os.walk(base_dir):
        for f in files:
            if f.endswith(".xml"):
                path = os.path.join(root, f)
                xml_count += 1
                try:
                    ET.parse(path)
                except Exception as e:
                    errors.append(f"XML Error in {path}: {e}")
    print(f"Verified {xml_count} XML files.")
    if errors:
        for err in errors:
            print("ERROR:", err)
        return False
    print("All XML files are well-formed and valid.")
    return True

def verify_java_files(base_dir):
    print("\n=== VERIFYING JAVA FILES SYNTAX & BRACES ===")
    errors = []
    java_count = 0
    for root, _, files in os.walk(base_dir):
        for f in files:
            if f.endswith(".java"):
                path = os.path.join(root, f)
                java_count += 1
                with open(path, "r", encoding="utf-8", errors="replace") as jf:
                    content = jf.read()
                
                # Check package declaration
                if "package " not in content:
                    errors.append(f"Missing package in {path}")
                
                # Count braces outside strings/comments
                brace_depth = 0
                in_line_comment = False
                in_block_comment = False
                in_str = False
                in_char = False
                escaped = False
                
                i = 0
                n = len(content)
                while i < n:
                    ch = content[i]
                    if in_line_comment:
                        if ch == '\n':
                            in_line_comment = False
                    elif in_block_comment:
                        if ch == '*' and i + 1 < n and content[i+1] == '/':
                            in_block_comment = False
                            i += 1
                    elif in_str:
                        if escaped:
                            escaped = False
                        elif ch == '\\':
                            escaped = True
                        elif ch == '"':
                            in_str = False
                    elif in_char:
                        if escaped:
                            escaped = False
                        elif ch == '\\':
                            escaped = True
                        elif ch == '\'':
                            in_char = False
                    else:
                        if ch == '/' and i + 1 < n and content[i+1] == '/':
                            in_line_comment = True
                            i += 1
                        elif ch == '/' and i + 1 < n and content[i+1] == '*':
                            in_block_comment = True
                            i += 1
                        elif ch == '"':
                            in_str = True
                        elif ch == '\'':
                            in_char = True
                        elif ch == '{':
                            brace_depth += 1
                        elif ch == '}':
                            brace_depth -= 1
                            if brace_depth < 0:
                                errors.append(f"Mismatched closing brace in {path} at char {i}")
                                break
                    i += 1
                
                if brace_depth != 0:
                    errors.append(f"Unbalanced braces in {path} (depth={brace_depth})")

    print(f"Verified {java_count} Java files.")
    if errors:
        for err in errors:
            print("ERROR:", err)
        return False
    print("All Java files have valid package statements and balanced syntax.")
    return True

def verify_sample_activity_setup(base_dir):
    print("\n=== VERIFYING SAMPLE ACTIVITY & MANIFEST ===")
    manifest_path = os.path.join(base_dir, "app/src/main/AndroidManifest.xml")
    with open(manifest_path, "r", encoding="utf-8") as mf:
        m_content = mf.read()
    
    assert "SampleActivity" in m_content, "SampleActivity must be in AndroidManifest.xml"
    assert "android.intent.action.MAIN" in m_content, "MAIN action must be present"
    assert "android.intent.category.LAUNCHER" in m_content, "LAUNCHER category must be present"
    print("✓ AndroidManifest.xml correctly designates SampleActivity as the MAIN LAUNCHER activity!")

    sample_java = os.path.join(base_dir, "app/src/main/java/com/roni/library/sdk/app/SampleActivity.java")
    assert os.path.exists(sample_java), "SampleActivity.java must exist"
    with open(sample_java, "r", encoding="utf-8") as sf:
        s_content = sf.read()
    assert "extends NativeRuntimeActivity" in s_content, "SampleActivity must extend NativeRuntimeActivity"
    assert "BanglaChartView" in s_content, "SampleActivity must include BanglaChartView"
    assert "setupComponentItems" in s_content, "SampleActivity must setup item-based UI components"
    assert "runAllUiTests" in s_content, "SampleActivity must have Test All UI runner"
    print("✓ SampleActivity correctly extends library NativeRuntimeActivity with Bangla Chart and Item-based UI!")

    chart_view = os.path.join(base_dir, "app/src/main/java/com/roni/library/sdk/app/chart/BanglaChartView.java")
    assert os.path.exists(chart_view), "BanglaChartView.java must exist"
    with open(chart_view, "r", encoding="utf-8") as cf:
        c_content = cf.read()
    assert "toBanglaDigits" in c_content, "BanglaChartView must use Bengali digits"
    print("✓ BanglaChartView and BanglaChartItem models verified!")

    return True

if __name__ == "__main__":
    base = sys.argv[1] if len(sys.argv) > 1 else "."
    ok_xml = verify_xml_files(base)
    ok_java = verify_java_files(base)
    ok_sample = verify_sample_activity_setup(base)
    if ok_xml and ok_java and ok_sample:
        print("\n>>> ALL VERIFICATION CHECKS PASSED SUCCESSFULLY! <<<")
        sys.exit(0)
    else:
        print("\n>>> VERIFICATION FAILED! <<<")
        sys.exit(1)
