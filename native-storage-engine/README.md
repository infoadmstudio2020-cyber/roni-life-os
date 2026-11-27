# NativeStorageEngine

Layer 2 storage engine for the NativeRuntime ecosystem: encrypted preferences, a document
database, a plaintext-or-encrypted key/value store, an LRU disk cache and a SQLite helper.

## Overview

`NativeStorageEngine` is a process-wide singleton. It must be initialised before any accessor
is used:

```java
NativeStorageEngineResult<Boolean> result = StorageEngine.init(context,
        new NativeStorageEngineConfig.Builder()
                .storageDirectory(new File(context.getFilesDir(), "native_storage"))
                .encryptionKeyAlias("native_storage_master_key")
                .build());
```

Calling `getPreferences`, `getDatabase`, `getEncryptedStore`, `getKeyValueStore`,
`getDiskCache`, `getDatabaseHelper`, `getService` or `getCommandHandler` before `init` throws
`IllegalStateException`. There is no silent auto-initialisation.

## Module layout

```
storage/
  NativeStorageEngine          singleton, lifecycle, sub-object wiring
  StorageEngine                static facade
  NativeStorageEngineConfig    builder (storageDirectory, encryptionKeyAlias, encryptionPassword)
  KeyValueStore                Properties-backed KV, optional AES-GCM
  EncryptedPreferences         AES-GCM preferences
  EncryptedStore               thin wrapper over EncryptedPreferences
  LruDiskCache                 size-bounded disk cache
  DatabaseHelper               SQLite helper
  crypto/CryptoUtils           PBKDF2 + AES-GCM primitives
  crypto/KeystoreKeyProvider   AndroidKeyStore master key
  database/                    StorageDatabase, StorageCollection, Document, QueryFilter
  handler/StorageCommandHandler  storage.* RPC handler
  service/NativeStorageService   service wrapper
```

## Two storage tiers

| Tier | Class | On-disk format | Encryption |
| --- | --- | --- | --- |
| Preferences | `EncryptedPreferences` | `<name>.encpref` (Properties) | Always AES-GCM |
| Key/value | `KeyValueStore` | `<name>.kv` (Properties) | Optional AES-GCM |

`KeyValueStore` has two constructors:

```java
new KeyValueStore("kv_store", dir);            // plaintext, logs a one-time WARN
new KeyValueStore("kv_store", dir, secretKey); // AES-GCM
```

`isEncrypted()` reports the active mode. `flush()` forces a write and returns whether it
succeeded.

## Key management

The master key is an AES-256 key in `AndroidKeyStore`:

```java
KeystoreKeyProvider provider = new KeystoreKeyProvider("native_storage_master_key");
SecretKey key = provider.getOrCreateKey(); // null when the keystore is unavailable
```

- Generated on first use with `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`, `BLOCK_MODE_GCM`,
  `ENCRYPTION_PADDING_NONE`, `setRandomizedEncryptionRequired(true)`.
- Never leaves the keystore; only ciphertext is written to disk.
- On a plain JVM (unit tests) `isAvailable()` is `false` and `getOrCreateKey()` returns `null`.
  In that case the engine falls back to `NativeStorageEngineConfig.encryptionPassword`, which
  must be supplied explicitly. There is **no** built-in default passphrase.

`EncryptedPreferences` throws `StorageException("MISSING_KEY", …)` when constructed without
key material.

## Migration from 1.0.0

`KeyValueStore` files written before 1.0.2 have no `__format__` marker. Opening such a file
with a `SecretKey` re-encrypts every value and rewrites the file with `__format__=2`. The
migration runs once; re-opening the file does not double-encrypt.

`EncryptedPreferences` files written with the old shared passphrase cannot be decrypted with a
keystore key. They surface as `[DECRYPTION_ERROR]` in `getAll()` and as the default value in
`getString()`. Re-write those values after upgrading.

## Public API

```
StorageEngine (static facade)
  getInstance() · init(Context, NativeStorageEngineConfig) · isInitialized()
  getPreferences(String) · getDatabase() · getEncryptedStore() · getKeyValueStore()
  getDiskCache() · getDatabaseHelper() · getService() · getCommandHandler() · release()

NativeStorageEngine
  getInstance() · init(Context, NativeStorageEngineConfig) · isInitialized()
  getRuntimeState() · getConfig() · getService() · getCommandHandler()
  getPreferences(String) · getDatabase() · getEncryptedStore() · getKeyValueStore()
  getDiskCache() · getDatabaseHelper() · release()

KeyValueStore(String, File) · KeyValueStore(String, File, SecretKey)
  isEncrypted() · flush() · putString · getString · putInt · getInt · putBoolean · getBoolean
  contains · remove · clear · getAll

EncryptedPreferences(String, File, String) · EncryptedPreferences(String, File, SecretKey)
  isKeyBacked() · flush() · putString · getString · putInt · getInt · putLong · getLong
  putBoolean · getBoolean · contains · remove · clear · getAll

StorageCommandHandler
  SUPPORTED_ACTIONS · StorageCommandHandler() · StorageCommandHandler(NativeStorageService)
  handleRequest(NativeApiRequest)
```

## `storage.*` actions

| Action | Response |
| --- | --- |
| `get` | `{key, value, exists}` |
| `set` | `{saved: true}` |
| `remove` | `{removed: true}` |
| `clear` | `{cleared: true}` |
| `keys` | `{keys: [String]}` |
| `has` | `{exists: boolean}` |
| `getAll` | flat map of key → value |

All three action forms are accepted: `storage.get`, `/storage/get`, `get`. The legacy
`pref.*` and `db.*` actions remain available.

## Testing

```bash
./gradlew :native-storage-engine:testDebugUnitTest
```

| Test class | Covers |
| --- | --- |
| `StorageEngineTest` | pre-existing engine, crypto, database, cache, handler |
| `KeyValueStoreEncryptionTest` | S3 — encryption, legacy read, one-time migration |
| `EncryptedPreferencesKeyTest` | S2/S6 — missing key, key round-trip, corrupted entry |
| `StorageEngineLifecycleTest` | S1/S4 — fail-fast accessors, re-init, idempotent release |
| `StorageCommandHandlerContractTest` | S5 — seven actions, three forms, response shapes |
| `KeystoreKeyProviderTest` | S2 — JVM fallback behaviour |
