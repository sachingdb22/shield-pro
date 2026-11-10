# Caller Shield Pro

Caller Shield Pro is a local-first Android call-screening app with a dial pad, spam reporting, and a personal block list.

## Open and build
1. Install Android Studio (stable) and Android SDK Platform 36.
2. Open this project in Android Studio and allow Gradle sync to finish.
3. Connect an Android phone or use an emulator.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Install the generated debug APK from `app/build/outputs/apk/debug/`.

## Enable call screening
Open the app and tap **Enable call screening**. Accept Android's system role prompt. The app can only reject calls after it holds the Android call-screening role.

## Features and limitations
- Personal spam block list and on-device reports; submitting a report also adds that number to the block list.
- Optional private/withheld caller blocking and contact-aware unknown caller blocking. Contact permission is requested only when enabling the latter.
- A dashboard with the latest eight system call-log entries. Call-log permission is requested only when you choose to show them.
- An update checker in Settings that downloads APKs from this repository's GitHub Releases and hands installation to Android.
- A keypad that hands calls off to Android's system dialer; this app does not replace the default phone app.
- No shared reputation service, cloud reporting, caller-name database, antivirus, or protection against account takeovers and phishing. Reports stay on the device.
- Contact checks happen locally during call screening. Unknown-number blocking may block legitimate callers and is off by default.
- Behavior depends on Android version, device manufacturer, carrier, and call-screening role support. Test on your device before relying on call rejection.

## Privacy
Block-list entries, settings, and reports are stored in app-private local preferences. Recent calls are read from Android's system call log only after you grant permission and are not uploaded. Network access is used only to check GitHub Releases and download an APK update; reports and contacts are never uploaded. Android verifies the APK signature and asks you to confirm installation. Updates require a published GitHub Release with a `.apk` asset signed using the same key as the installed app and a higher Android version code.

To publish an update, create a signed APK with an incremented version code and version name (for example, `2` and `1.1`), using the same signing key as the installed app. Attach that APK to a GitHub Release tagged with the same version (for example, `v1.1`). Keep the signing key private. The repository currently needs a published release before the in-app updater can find an update.
