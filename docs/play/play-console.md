# Google Play Console answers

Draft answers for Play Console's "App content" and listing sections, matching what the code does. Play's wording changes over time: read each question as it appears, and update this file if an answer changes.

## Store listing

- **App name:** Airgap
- **Short description:** from `fastlane/metadata/android/en-US/short_description.txt`
- **Full description:** from `full_description.txt` in the same folder (Play doesn't render the HTML tags; remove them or switch to plain line breaks)
- **Icon:** `fastlane/metadata/android/en-US/images/icon.png` (512 × 512)
- **Phone screenshots:** `fastlane/metadata/android/en-US/images/phoneScreenshots/`
- **Feature graphic (1024 × 500):** still needed; Play requires one
- **Category:** Finance (or Tools)
- **Contact email:** Play requires one on the listing; use an address you're happy to make public

## Privacy policy

`https://github.com/ThePubDoc/airgap/blob/main/PRIVACY.md`. The app links to it from **Privacy check → Read the privacy policy**.

## Data safety

- **Does your app collect or share any of the required user data types?** No.
  - Airgap can't transmit anything: it has no internet permission. Play counts only data that leaves the device as "collected".
  - Copy, autofill, call bank and text-to-block hand details to another app only when the person explicitly does it. Play treats user-initiated transfers the person expects as not "sharing".
- With "No", Play skips the encryption-in-transit and deletion questions.

## App access (for Play's reviewers)

All functionality is available without an account. Instructions to give the reviewer:

1. Make sure the test device has a screen lock (PIN is fine).
2. Open Airgap, tick "I understand there's no backup", tap Continue, then **Create vault** and confirm with the PIN.
3. **Add card** with test card `4111 1111 1111 1111`, any future expiry, name `TEST`.
4. **Privacy check** (lock screen or below the cards) shows the app holds no network permission.

## Ads

No ads.

## Content rating

Utility app with no user-generated content, chat, location sharing, gambling or purchases. The questionnaire should come out as "Everyone"/PEGI 3.

## Target audience

18 and over. Airgap stores payment cards and isn't meant for children.

## Financial features declaration

Airgap stores card details on the phone and fills them into forms, but doesn't move money, process payments, or offer banking, loans or trading. Pick the option closest to "my app doesn't provide these financial features", and use the free-text box, if offered, to say it's an offline card vault. **Check the current list of options before submitting**: Play may class card storage as a wallet-type feature.

## Permissions

Nothing needs a special declaration: there's no SMS, call log, location, accessibility or "all files" permission. The autofill service is a standard Android service and needs no extra form.

## Signing

Decide before the first upload:

- **Recommended:** Play App Signing with your existing key (`~/.airgap-signing/airgap-release.jks`, certificate `BD:35:A3…1C:88`). Play, F-Droid and GitHub builds then share one signature, and people can switch between them without uninstalling, which would erase the vault.
- **Alternative:** let Google generate a separate app signing key. Play builds then can't update F-Droid or GitHub installs, and the reverse. Say so in the listing.

## Before each release

- Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
- Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
- `./gradlew bundleRelease`, then upload `app/build/outputs/bundle/release/app-release.aab`.
- Tag `v<version>` and attach the APK to a GitHub release for F-Droid's check.
