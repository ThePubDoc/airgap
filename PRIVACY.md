# Airgap privacy policy

**Effective:** 25 October 2026
**App:** Airgap (`app.airgap`) for Android
**Developer:** ThePubDoc · contact through [GitHub issues](https://github.com/ThePubDoc/airgap/issues)

## The short version

Airgap collects nothing. It has no internet or network permission, so Android itself prevents it from sending anything off your phone. There are no accounts, no analytics, no advertising, no crash reporting and no backups. Everything you save stays on your phone, encrypted, until you delete it.

## What Airgap stores on your phone

| What | Where | Protection |
|---|---|---|
| The cards you add: number, expiry, name on card, nickname, colour, credit or debit, and optionally the CVV, bill due day, bank helpline and block-SMS number | Airgap's private app storage (`vault.bin`, `vault.key`) | Encrypted with AES-256-GCM. The key is held in the phone's secure hardware (Android Keystore) and only works after your fingerprint, face or device PIN. |
| A short list for reminders: each card's nickname, last 4 digits, bill due day and expiry date (never the full number or CVV) | Airgap's private app storage (`reminders.bin`) | Encrypted with AES-256-GCM using a separate Keystore key, so reminders can run while the vault is locked. |
| App settings: whether you've seen the first-run notice, dismissed the autofill suggestion or been asked about notifications, and which reminders have already been shown (identified by random card IDs and dates) | Airgap's private app storage | Android's app sandbox. Contains no card details. |

None of this is backed up: Android backup and device-to-device transfer are turned off for Airgap. It is all removed when you delete a card, erase the vault, or uninstall the app.

## What leaves your phone

Nothing is sent by Airgap. It cannot open network connections.

Some features hand information to another app on your phone, only when you choose to:

- **Copy:** copied card details go to Android's clipboard, where other apps and your keyboard may be able to read them. Airgap marks copies as sensitive and clears the clipboard 30 to 50 seconds later. Some keyboards keep their own clipboard history, which Airgap can't clear.
- **Autofill:** if you turn Airgap on as your autofill service and pick a card after confirming with your fingerprint, the card details are filled into the app or website you're using. That app or website then handles them under its own privacy policy. Airgap shows you which app or site is asking before you pick a card, and never saves anything typed into other apps.
- **Lost card:** "Call bank" opens your phone app with the helpline number filled in, and "Text to block" opens your SMS app with the number and message filled in. You decide whether to call or send.
- **Pay:** opens Google Wallet or Samsung Wallet. Nothing is passed to them.

## Permissions

| Permission | Used for |
|---|---|
| Biometric | Unlocking the vault with your fingerprint or face |
| NFC | Reading the number, expiry and (if present) name from a card you tap on the phone, to fill the Add card form. The radio range is a few centimetres. Nothing is saved unless you save the card. |
| Notifications | Showing bill due-date and card expiry reminders. They're scheduled on the phone, and on the lock screen they don't name the card. |
| Run at startup | Scheduling reminders again after the phone restarts |

Airgap requests no permission for the internet, networks, location, contacts, camera, microphone, phone calls, SMS or files. You can confirm this in the app under **Privacy check**, or in Android under Settings → Apps → Airgap → Permissions.

## Your choices

- Delete any card from its Edit screen, or uninstall Airgap to remove everything.
- Notifications, NFC use and autofill can each be turned off in Android settings.
- There is no copy of your data anywhere else, so there's nothing for the developer to access, export or delete on your behalf.

## Children

Airgap is not directed at children and is meant for adults managing their own payment cards.

## Changes

Changes to this policy are published in this file, and its history is visible in the [repository](https://github.com/ThePubDoc/airgap/commits/main/PRIVACY.md). The effective date above changes with each update.
