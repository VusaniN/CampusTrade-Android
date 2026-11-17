# CampusTrade security

## Reporting

Do not open a public issue for a vulnerability that could expose student data, account access, locations or payment instructions. Contact the campus team privately.

## Secrets policy

- Never put relay secrets, AI provider keys, Cloudinary API secrets, keystore passwords or service-account JSON in this repository or in the Android APK.
- The invoice relay no longer receives a shared secret from the app. It must verify the caller's Firebase ID token and App Check token instead.
- The old invoice relay secret must be rotated in the Apps Script deployment. Treat the previous value as compromised because it existed in source and APK builds.
- The old helper AI direct-key build fields have been removed. The helper always uses the signed-in relay.
- Firebase Android API keys from `google-services.json` are not server secrets. Firestore rules and App Check are the security boundary.

## Required relay validation

### Push relay (`/api/send`)

- Verify the Firebase ID token on every request.
- Verify the App Check token when App Check is enforced.
- Do not trust `recipientEmail`, `recipientUid`, `title`, `body` or `orderId` until the server has checked that the caller and recipient are participants in the same order or chat.
- Reject arbitrary recipients, self-notifications and requests with no valid relationship.
- Cap title/body length and rate-limit per caller.

### Helper relay (`/api/ultron`)

- Verify the Firebase ID token and App Check token.
- Keep provider/model names, keys and system prompts server-side.
- Rate-limit per student and cap prompt/response sizes.
- Do not return upstream provider errors to the app.

### Invoice relay (Apps Script/Gmail)

- Verify the Firebase ID token and App Check token.
- Load the order from Firestore and confirm the caller is the buyer or seller and the order is `COMPLETED`.
- Derive recipient addresses from the order; do not trust addresses supplied by the client.
- Validate the invoice number, PDF size/type and reject arbitrary attachments.
- Rate-limit per order and per student.
- Rotate the old shared secret and remove the `secret` field from the accepted payload.

Until the invoice relay is updated, the app falls back to opening the phone's email composer with the PDF attached.

## Firebase App Check

The app initialises App Check with Play Integrity in release builds and the debug provider in debug builds.

1. In Firebase Console, open **App Check** for the Android app.
2. Register the Play Integrity provider.
3. For debug builds, copy the debug token from logcat into the App Check debug token list.
4. Enable monitoring first. After the relay endpoints and app are stable, enforce App Check for Firestore and Authentication.
5. Keep debug tokens out of public logs and rotate them if exposed.

App Check does not replace Firebase Authentication or Firestore rules. All three are required.

## Cloudinary

The upload preset is public by design. In the Cloudinary console:

- Restrict incoming formats to JPEG, PNG, WebP, HEIC/HEIF and the allowed document formats.
- Set a maximum upload size (images 8 MB, documents 10 MB).
- Restrict upload folders and public IDs.
- Enable moderation or malware scanning where available.
- Do not expose the Cloudinary API secret in the app. Use signed uploads from a server for sensitive documents if stronger control is needed.

## Firestore rules

- Offers use an explicit turn/state machine.
- Pickup place/time changes require a new revision and reset confirmations.
- Orders, chats, ratings and delivery locations remain participant-only.
- Admin roles are console-only. Run the emulator rules tests before deploying any rule change.

## Sensitive actions

- Admin entry and account deletion/deactivation require a recent phone screen-lock check or Firebase password re-authentication.
- Admin and payment screens force screen-capture protection.
- Admins should still use MFA at the Firebase account level where available.
- Exit admin mode when finished.

## Release signing

Set these in `local.properties` or environment variables on the release machine/CI:

```text
CAMPUSTRADE_KEYSTORE_PATH
CAMPUSTRADE_KEYSTORE_PASSWORD
CAMPUSTRADE_KEY_ALIAS
CAMPUSTRADE_KEY_PASSWORD
```

If they are absent, `assembleRelease` produces an unsigned APK. Never commit the keystore or passwords.

## Data at rest

The app uses `EncryptedSharedPreferences` for local session, order, message, profile and feature caches. Existing plain preferences are copied once on upgrade and then cleared. If a device's Keystore is unavailable, the app falls back to app-private storage and logs the failure without exposing values. `android:allowBackup="false"` remains set. Android full-disk/file-based encryption still protects the device as a whole.

## Recommended follow-up

- Enable Firebase App Check enforcement and deploy the server-side relay checks.
- Restrict the Cloudinary unsigned preset in its console.
- Rotate the old invoice relay secret in Apps Script and update the relay to verify Firebase ID and App Check tokens.
- Add Firestore emulator tests for auth bypass, offers, pickups and admin-only actions.
- Add dependency and container scanning in CI.
- Add account-level MFA for moderators.
- Review data retention for phone numbers, addresses, location history and invoice PDFs.
