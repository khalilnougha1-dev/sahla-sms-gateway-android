# Sahla SMS Gateway — Android app

Turns an Android phone into the SMS gateway for your Sahla SMS dashboard.

It calls three endpoints on your server:
- `POST /api/public/v1/pair` — exchanges the pairing code from the dashboard for a device API key
- `GET  /api/public/v1/gateway-poll` — pulls queued messages (header `x-api-key`)
- `POST /api/public/v1/message-status` — reports `sent` / `delivered` / `failed`

## Build the APK

1. Install Android Studio (Hedgehog or newer) and open this folder.
2. Let Gradle sync (it downloads the Android Gradle Plugin 8.5.2 + Kotlin 1.9.24).
3. Build > Build Bundle(s)/APK(s) > Build APK(s).
4. The debug APK appears at `app/build/outputs/apk/debug/app-debug.apk`.

Command line alternative (needs a local Android SDK + JDK 17):

    ./gradlew assembleDebug

## Install and pair

1. Copy the APK to the phone and install it (allow "unknown sources").
2. Open the app, grant SMS + phone + notification permissions.
3. In the dashboard go to Devices, add a device and copy the pairing code.
4. Paste the code into the app, keep the server URL, tap **Pair device**.
5. The gateway service starts and the device shows as online in the dashboard.

Messages you send from the dashboard are then delivered by the SIM card in this
phone, and their delivery status flows back into the message log.

## Build the APK automatically on GitHub (no PC needed)

This repo includes a GitHub Actions workflow (`.github/workflows/build-apk.yml`)
that builds the APK on every push:

1. Go to the **Actions** tab and wait for the "Build APK" run to finish.
2. Download the APK either from the run's **Artifacts** section
   (`sahla-gateway-debug-apk`) or from **Releases** (`app-debug.apk`).
3. Copy it to your Android phone, tap to install
   (allow "install from unknown sources" when asked).

## Permissions

The app requests `SEND_SMS`, `READ_PHONE_STATE`, `FOREGROUND_SERVICE`
and notification permissions. Grant them on first launch so it can send
SMS from the phone's SIM card.

## Pairing

1. In the Sahla dashboard open **Devices → Pair a device** and copy the code.
2. In the app, paste the code and tap **Pair**.
3. Keep the gateway service running; the phone polls every 5 seconds,
   sends queued messages via `SmsManager` (chosen SIM slot), and reports
   delivery status back to the dashboard.
