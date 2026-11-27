# Security API Guide — `security.*`

## ১. গুরুত্বপূর্ণ

**কোনো credential, key বা secret কখনো JavaScript-এ যায় না।** শুধু status ও boolean।

## ২. Action (১১)

| Action | Payload | Response |
|---|---|---|
| `security.biometric.capability` | — | `{available, enrolled, ...}` |
| `security.biometric.authenticate` | `{title}` | `{success:true, initiated:true}` + ইভেন্ট |
| `security.permission.check` | `{permission}` | `{permission, status, granted}` |
| `security.hash.sha256` | `{text}` | `{hash}` |
| `security.keystore.generate` | `{alias, algorithm}` | `{alias, created}` |
| `security.keystore.hasKey` | `{alias}` | `{exists}` |
| `security.keystore.delete` | `{alias}` | `{deleted}` |
| `security.keystore.list` | — | `{aliases:[...]}` |
| `security.keystore.encrypt` | `{alias, plaintext}` | `{ciphertext}` |
| `security.keystore.decrypt` | `{alias, ciphertext}` | `{plaintext}` |
| `security.diagnostic` | — | `{...}` |

## ৩. Biometric — দুই ধাপ

`IApiHandler` synchronous, কিন্তু biometric asynchronous। তাই:

```
১. call('security.biometric.authenticate') → {initiated:true}   (প্রম্পট দেখানো হয়েছে)
২. ইভেন্ট 'security.biometric.result' → {status, method, errorCode, message}
```

```js
import { security } from './native-runtime.js';

const cap = await security.capability();
if (!cap.available) return;

const off = security.onResult((result) => {
  if (result.status === 'SUCCEEDED') unlockVault();
  else showError(result.message);
  off();
});

await security.authenticate('Vault খুলুন');
```

`status`: `SUCCEEDED` | `FAILED` | `ERROR`

## ৪. Activity lifecycle

`AndroidBiometricPromptDelegate` একটি `FragmentActivity` ছাড়া প্রম্পট দেখাতে পারে না।

| অবস্থা | ফল |
|---|---|
| Activity সংযুক্ত | প্রম্পট দেখায় |
| Activity নেই | `onAuthenticationError(ERROR_NO_HOST = -1000)` → ইভেন্ট `ERROR` |
| Activity recreate | `onResume`-এ `attachActivity()` আবার সংযুক্ত করে |

## ৫. যা নেই

| সক্ষমতা | কারণ |
|---|---|
| `biometric.cancel` | `BiometricPrompt` instance লোকাল, handle রাখা হয় না। সিস্টেম ডায়ালগের Cancel → `ERROR_NEGATIVE_BUTTON` |
| Face-specific API | Android আলাদা করে দেয় না; `BIOMETRIC_WEAK`-এ face পড়ে |
| Vault unlock | `ProjectVault` আছে, কিন্তু JS action নেই |

## ৬. নিরাপত্তা নিয়ম

- `keystore.encrypt/decrypt` শুধু alias নেয়, key কখনো ফেরত দেয় না
- `hash.sha256` একমুখী
- Permission check শুধু পড়ে, চায় না
- Biometric bypass করার কোনো পথ নেই — `setHeadlessSimulationAllowed` ডিফল্টে false
