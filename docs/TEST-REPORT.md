# Test Execution Report

Generated 2026-11-16 18:10:49 UTC.

## Environment

```
openjdk version "17.0.21" 2026-10-20
OpenJDK Runtime Environment Temurin-17.0.21+9 (build 17.0.21+9)
OpenJDK 64-Bit Server VM Temurin-17.0.21+9 (build 17.0.21+9, mixed mode, sharing)
javac 17.0.21
ANDROID_HOME=<unset>
```

## Compile

```
$ javac -Xlint:all -d /tmp/out <13 files>   # exit 0, no warnings
$ javac -Xlint:all -d /tmp/out2 <5 files>   # exit 0, no warnings
```

## Suites

```
$ java -cp /tmp/out com.roni.library.security.crypto.VaultFormatCheck
VaultFormatCheck: 36 checks passed
$ java -cp /tmp/out com.roni.library.security.crypto.VaultCipherCheck
VaultCipherCheck: 33 checks passed
$ java -cp /tmp/out com.roni.library.security.crypto.DecryptedResourceCacheCheck
DecryptedResourceCacheCheck: 11 checks passed
$ java -cp /tmp/out com.roni.life.os.app.project.SealDecisionCheck
SealDecisionCheck: 16 checks passed
$ java -cp /tmp/out com.roni.life.os.app.project.ProjectManifestCheck
ProjectManifestCheck: 29 checks passed
$ java -cp /tmp/out2 com.roni.library.ui.web.WebPathsCheck
WebPathsCheck: 193 checks passed
$ java -cp /tmp/out2 com.roni.library.ui.web.WebSourceCheck
WebSourceCheck: 15 checks passed
```

Total: 333 checks, 0 failures.

## Not executed

| Task | Status | Reason |
|---|---|---|
| `./gradlew :app:assembleDebug` | attempted, failed | `SDK location not found` — no Android SDK |
| `./gradlew test` | not run | Android modules need the SDK to configure |
| Instrumentation tests | not run | no device, no emulator, no adb |
| WebView integration | not run | no device |
| Keystore AES/GCM | not run | no Android runtime |
| `my-app` in a real WebView | not run | no device |
