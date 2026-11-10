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
- A keypad that hands calls off to Android's system dialer; this app does not replace the default phone app.
- No shared reputation service, cloud reporting, caller-name database, antivirus, or protection against account takeovers and phishing. Reports stay on the device.
- Contact checks happen locally during call screening. Unknown-number blocking may block legitimate callers and is off by default.
- Behavior depends on Android version, device manufacturer, carrier, and call-screening role support. Test on your device before relying on call rejection.

## Privacy
Block-list entries, settings, and reports are stored in app-private local preferences. The app does not request network access or upload contacts and call data. Android system call and dialer behavior remains subject to Android and device settings.
