# Airgap

An Android card vault that keeps your credit and debit cards on your phone and nowhere else.

- **No internet access.** The app declares no network permission. The build fails if any dependency tries to add one.
- **Hardware-backed encryption.** Cards are encrypted with AES-256-GCM. The key is protected by a master key in Android Keystore (StrongBox when the phone has it). That master key can't be used without your fingerprint, face or device PIN.
- **No backups.** The vault lives in `noBackupFilesDir`, and backup and device-to-device transfer are turned off.
- **Locks itself.** The vault locks whenever the app leaves the screen. Screenshots, screen recording and the recent-apps preview are blocked.
- **Copy for checkout.** Copied details are flagged sensitive, and the clipboard is cleared 30 to 50 seconds later, even if Android has frozen or closed the app in the meantime.

## What it doesn't do

**Contactless (tap-to-pay) payments.** A terminal needs a one-time cryptogram for every tap. Only the card's chip or a bank-issued token can produce it. Tokens come from Visa and Mastercard tokenization services (VTS, MDES), which are open only to certified wallet providers. A saved card number, expiry and CVV can't produce that cryptogram. For tap-to-pay, use Google Wallet or your bank's app.

## Security model

```
fingerprint / face / PIN
        │  BiometricPrompt approves one Cipher operation
        ▼
Keystore master key (AES-256, never leaves secure hardware)
        │  unwraps
        ▼
data key (random 256-bit, kept in memory only while unlocked)
        │  encrypts
        ▼
vault.bin (AES-256-GCM, cards as JSON)
```

Files, in `noBackupFilesDir`:

| File        | Contents                                                        |
|-------------|-----------------------------------------------------------------|
| `vault.key` | The data key, encrypted by the Keystore master key              |
| `vault.bin` | The cards, encrypted by the data key                            |

Each file is laid out as `[IV length: 1 byte][IV][ciphertext + GCM tag]`.

### Things to know

- **Removing the screen lock destroys the vault.** Android deletes keys that need user authentication when the secure lock screen is turned off. The app then offers to erase the vault and start over.
- **Losing the phone means losing the cards.** Nothing is backed up anywhere, by design. Your bank can always reissue a card.
- **Card data in memory.** The data key is zeroed on lock. Decrypted card fields are Kotlin `String`s, which the JVM can't reliably wipe. They're dropped on lock and left for the garbage collector.
- **Keyboard clipboard history is out of reach.** Samsung Keyboard, Gboard and similar keyboards can keep their own copy of what you copy. Airgap flags copies as sensitive, which many keyboards respect, but it can't clear a keyboard's history. Turn that feature off if it matters to you.
- **The CVV is optional.** Leave it empty if you'd rather not store it.

## Project layout

```
app/src/main/java/app/airgap/
├── MainActivity.kt           BiometricPrompt, FLAG_SECURE, lock on stop
├── security/
│   ├── KeystoreKey.kt        Master key generation and Cipher setup
│   └── SecureClipboard.kt    Sensitive clip with auto-clear
├── vault/
│   ├── Card.kt               Card model
│   ├── CardValidation.kt     Luhn, network detection, formatting
│   └── VaultRepository.kt    Envelope encryption and atomic file writes
└── ui/
    ├── VaultViewModel.kt     Vault state, single-threaded vault work
    ├── AirgapApp.kt          Screen routing and error dialog
    ├── LockScreen.kt
    ├── CardListScreen.kt
    ├── CardEditScreen.kt
    └── Theme.kt
```

## Building

Requirements:

- Android Studio (latest stable), or JDK 17+ with the Android SDK
- Android SDK platform 37
- A phone or emulator running Android 11 (API 30) or later, with a screen lock set

```sh
./gradlew assembleDebug                  # build (also runs the network-permission check)
./gradlew testDebugUnitTest              # unit tests
./gradlew verifyReleaseNoNetworkPermission
```

Stack: Kotlin 2.4, AGP 9.4 (built-in Kotlin), Jetpack Compose with Material 3, `androidx.biometric`, `kotlinx.serialization`.

Release signing isn't configured. Add a `signingConfigs` block that reads from an untracked `keystore.properties` before building a release APK.
