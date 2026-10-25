# Airgap

An Android card vault that keeps your credit and debit cards on your phone and nowhere else.

- **No internet access.** The app declares no network permission, and Android enforces that. The build fails if any dependency tries to add one. **Privacy check** in the app shows this live from Android, even before you unlock.
- **Hardware-backed encryption.** Cards are encrypted with AES-256-GCM. The key is protected by a master key in Android Keystore (StrongBox when the phone has it). That master key can't be used without your fingerprint, face or device PIN.
- **No backups.** The vault lives in `noBackupFilesDir`, and backup and device-to-device transfer are turned off.
- **Locks itself.** The vault locks whenever the app leaves the screen, and after 2 minutes without a touch while it's open. While you're adding or editing a card, it stays open for 60 seconds in the background so you can copy details from another app, and anything typed survives a lock. Screenshots, screen recording and the recent-apps preview are blocked.
- **Tap your card to add it.** On the Add card screen, hold a contactless card to the back of the phone and Airgap reads its number and expiry over NFC (the same public data a shop terminal sees). The CVV isn't readable this way; you type it.
- **Bill and expiry reminders.** Set a credit card's due day and Airgap notifies you 3 days before and on the day. Every card gets a reminder from the month before it expires. All scheduling happens on the phone.
- **Lost-card quick actions.** Save the bank's helpline and block-by-SMS number per card. **Lost card?** opens the dialler or the SMS app with everything filled in.
- **Card colours.** Pick a colour per card so they're easy to tell apart. Numbers are shown in printed groups as you type them.
- **Fill online checkouts.** Airgap can be Android's autofill service for card fields: tap **Fill card with Airgap** on a checkout form, confirm with your fingerprint, and pick a card. See [Online checkout autofill](#online-checkout-autofill).
- **Copy for checkout.** Copied details are flagged sensitive, and the clipboard is cleared 30 to 50 seconds later, even if Android has frozen or closed the app in the meantime.

## What it doesn't do

**Contactless (tap-to-pay) payments.** A terminal needs a one-time cryptogram for every tap. Only the card's chip or a bank-issued token can produce it. Tokens come from Visa and Mastercard tokenization services (VTS, MDES), which are open only to certified wallet providers. A saved card number, expiry and CVV can't produce that cryptogram. For tap-to-pay, use Google Wallet, Samsung Wallet or your bank's app; Airgap's **Pay** button opens whichever of the two wallets is installed.

## Online checkout autofill

Airgap can fill the card number, expiry (text or drop-downs), CVV and name into checkout forms in Chrome and other apps.

1. **Turn it on.** Tap **Turn on** on the card at the top of Airgap's list, or go to Android **Settings → Passwords, passkeys & autofill → Preferred service** and pick **Airgap card autofill**. Android allows one autofill service at a time, so this replaces Samsung Pass or Google autofill, passwords included.
2. **In Chrome**, also choose **Settings → Autofill services → Autofill using another service**. Chrome restarts. Without this, Chrome only uses Google's own autofill.
3. **On a checkout form**, tap the card number field, then **Fill card with Airgap**. Confirm it's you and pick a card. The picker shows which website or app is asking; check it before you pick.

How it stays safe:

- The suggestion shows no card data. Nothing is read from the vault until you confirm with your fingerprint or PIN.
- The vault is unlocked only long enough to read the cards for the picker, then locked again.
- Only forms with a card number or security code field get the suggestion. Login, address and other forms are left alone.
- Airgap never saves what you type into other apps.
- Chrome only offers autofill on secure (HTTPS) pages.

## Verify it yourself

You don't have to take the claims above on trust.

1. **In the app:** tap **Privacy check** (on the lock screen or below your cards). It reads Airgap's permissions, backup and debug flags, and signing certificate straight from Android.
2. **In Android:** Settings → Apps → Airgap → Permissions shows the same list. There's no network permission to grant.
3. **The signing certificate.** Official builds are signed with this certificate (SHA-256):

   ```
   BD:35:A3:5A:23:0F:22:53:45:FB:6E:09:1B:B2:63:D3:3E:A4:B6:A1:CF:2C:CC:5E:7A:F4:A7:FA:C0:50:1C:88
   ```

   A different fingerprint in **Privacy check** means a different publisher.
4. **The build is reproducible.** Building a tagged release from source gives the same APK as the published one, apart from the signature:

   ```sh
   git checkout v0.2.0
   ./gradlew assembleRelease          # without a signing key: app-release-unsigned.apk
   pip install apksigcopier           # needs apksigner from the Android SDK build-tools on PATH
   apksigcopier compare airgap-0.2.0.apk --unsigned app/build/outputs/apk/release/app-release-unsigned.apk
   ```

   No output and exit code 0 means they match. Release builds leave out the git commit (`vcsInfo`) and Google Play's encrypted dependency report so the output doesn't depend on the checkout.

## Permissions

| Permission | Why |
|---|---|
| `USE_BIOMETRIC` | Unlock with fingerprint, face or PIN |
| `NFC` | Read a tapped card's number and expiry (local radio only) |
| `POST_NOTIFICATIONS` | Bill and expiry reminders; asked for once, after the first card is saved |
| `RECEIVE_BOOT_COMPLETED` | Set the daily reminder check again after a restart |

No network permission of any kind; the build fails if one appears. Calling the bank or texting a block SMS opens the dialler or SMS app, so Airgap needs no phone or SMS permission.

### Reminders and the vault

The vault can't be opened in the background: its key needs your fingerprint. Reminders therefore read a separate file, `reminders.bin`, holding only each card's label (nickname and last 4 digits), due day and expiry, never the number or CVV. It's encrypted with its own Keystore key that doesn't need authentication. On the lock screen, reminders only say "You have a card reminder".

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
├── MainActivity.kt           BiometricPrompt, FLAG_SECURE, lock on stop, NFC reader mode
├── ui/PrivacyCheck.kt        Privacy check screen: permissions and flags read live from Android
├── nfc/
│   ├── EmvCardReader.kt      Reads number and expiry from a contactless EMV card
│   └── Tlv.kt                BER-TLV parsing for card replies
├── reminders/
│   ├── ReminderRules.kt      Which bill and expiry reminders are due on a given day
│   ├── ReminderStore.kt      The small encrypted list reminders read
│   └── Reminders.kt          Daily alarm, notifications, boot receiver
├── autofill/
│   ├── CardAutofillService.kt  Offers a locked "Fill card with Airgap" suggestion
│   ├── AutofillActivity.kt     Unlocks, shows the card picker, returns the filled values
│   ├── CardForm.kt             Finds card fields in a form and builds the fill
│   ├── CardFieldClassifier.kt  Autofill hints, autocomplete attributes, then field names
│   └── CardFillValues.kt       Expiry formats and drop-down matching
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
    ├── PaymentApps.kt        Finds and opens Google Wallet / Samsung Wallet
    └── Theme.kt
```

## Building

Requirements:

- Android Studio (latest stable), or JDK 17+ with the Android SDK
- Android SDK platform 37
- A phone or emulator running Android 11 (API 30) or later, with a screen lock set

```sh
./gradlew assembleDebug                  # debug build, installs as app.airgap.debug next to the real app
./gradlew testDebugUnitTest              # unit tests (card rules, NFC parsing, reminders, autofill)
./gradlew lintRelease                    # Android lint; errors fail the build
./gradlew connectedDebugAndroidTest      # UI tests on a connected device or emulator
./gradlew verifyReleaseNoNetworkPermission
./gradlew bundleRelease                  # Android App Bundle for Google Play: app/build/outputs/bundle/release/app-release.aab
```

`connectedDebugAndroidTest` runs on every connected device. Set `ANDROID_SERIAL` (for example `ANDROID_SERIAL=emulator-5554`) to pick one, and make sure its screen is unlocked.

GitHub Actions runs the unit tests, lint and a release build (with the network-permission check) on every push to `main` and on pull requests. See `.github/workflows/ci.yml`.

Stack: Kotlin 2.4, AGP 9.4 (built-in Kotlin), Jetpack Compose with Material 3, `androidx.biometric`, `kotlinx.serialization`.

### Release signing

Release builds are signed with a key kept outside the repo, in `~/.airgap-signing/`:

| File                  | Contents                                         |
|-----------------------|--------------------------------------------------|
| `airgap-release.jks`  | The signing key (RSA 4096, PKCS12)               |
| `keystore.properties` | Its path, alias and password                     |

`keystore.properties.example` shows the format. To use another location, point the `airgap.signingProperties` Gradle property or the `AIRGAP_SIGNING_PROPERTIES` environment variable at your properties file. Without it, `assembleRelease` produces an unsigned APK.

**Back up both files together** (a password manager works). Android only installs an update signed with the same key. Lose the key and the only way to update is to uninstall, which deletes the vault.

```sh
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk
adb install app/build/outputs/apk/release/app-release.apk
```

Use the release build for real cards. The debug build can be inspected with `adb run-as` and a debugger, and switching between the two needs an uninstall because they're signed with different keys.

## Publishing on F-Droid

The store listing (description, icon, screenshots, changelogs) lives in `fastlane/metadata/android/`, where F-Droid reads it. The build recipe is in `docs/fdroid/app.airgap.yml`.

1. Tag the release (`v0.2.0`) and attach the signed APK to a GitHub release as `airgap-0.2.0.apk`.
2. Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata) on GitLab, copy the recipe to `metadata/app.airgap.yml`, and open a merge request.
3. F-Droid builds from source and checks the result against the signed APK. If they match, it publishes the app with Airgap's own signature, so updates from GitHub and F-Droid are interchangeable.

## License

Airgap is free software under the [GNU General Public License v3.0 or later](LICENSE). You can use, study, change and share it; anything built from it must be shared under the same licence.

