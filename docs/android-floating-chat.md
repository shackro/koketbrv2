# Android floating chat

The Android companion keeps the Kokwet public chat above TikTok and other apps. Unlike a website, Android can draw an overlay after the user explicitly enables **Display over other apps** and starts the foreground service. It does not inspect which app is in the foreground; the user starts the overlay before switching apps and closes it when finished.

## Build and run a development APK

The project has a Gradle wrapper and targets Android 8.0 (API 26) or newer. From the repository root, open `mobile/android` in Android Studio, or run:

```sh
cd mobile/android
./gradlew assembleDebug
```

The installable development APK is `app/build/outputs/apk/debug/app-debug.apk`. Install it on an Android device using Android Studio or `adb install app/build/outputs/apk/debug/app-debug.apk`. This debug APK is for development/sideload testing only; it is not a Play Store release.

## Run on a device

1. Install the debug APK on an Android 8.0 (API 26) or newer device.
3. Enter the deployed Kokwet website origin, for example `https://your-kokwet-domain.example`.
4. Choose **Allow floating window** and enable Android's **Display over other apps** permission.
5. Return to Kokwet Voice Circle and choose **Start floating chat**.
6. Switch to TikTok. Drag the overlay title to move it; use `−` and `+` to resize it, `□` to collapse it to a bubble, and `×` or the notification's **Stop** action to close it.

The companion opens `/culture?open_chat=1#live-lounge` in a WebView, so chat messages continue using the existing API and refresh behavior. The site origin must use HTTPS. The overlay remains visible until stopped; it is not automatically hidden or activated based on app-foreground detection.

## Website recommendation flow

Recommend installation at the moment it solves a real problem, not as a homepage pop-up:

1. The visitor opens the Voice Circle and chooses **Open chat & float**.
2. The browser requests desktop Document Picture-in-Picture where available. On a phone, the site checks for Android using browser platform information and explains that a website cannot overlay TikTok.
3. If the Android companion has a published, verified download URL, show a dismissible prompt: **Keep chat visible while you watch TikTok?** The primary action opens the official Play Store listing; a secondary action keeps using the website/split-screen. The current website build uses `VITE_ANDROID_COMPANION_STORE_URL` for the official listing URL; leave it unset until the app is published.
4. If the app is not yet published, do not show a fake install button. Explain that cross-app chat is Android-only and keep the website fallback available.
5. After installation, the user opens Kokwet Voice Circle, enters the HTTPS site origin once, grants Android's overlay permission, and starts the foreground chat overlay. The app does not silently launch or request overlay permission on installation.

The prompt belongs in the live-chat flow, after the user asks to float chat. Do not display it to desktop users whose browser supports PiP, iOS users, or visitors who have dismissed it. Persist dismissal for a limited period (for example, seven days) and always leave a way to reopen the recommendation from the chat controls.

Use the official Play Store URL as the distribution source. Make that URL a managed site setting (for example, `android_companion_store_url`) so staff can publish it only after release approval; never link to an unverified APK mirror. An installed-app deep link can be added later with verified Android App Links, while the store URL remains the fallback.

## Effectiveness and acceptance checks

Measure the feature as a funnel: eligible Android chat opens → recommendation shown → store listing opened → Play Store installs → overlay permission granted → overlay started → chat used while another app is foregrounded. Use aggregate Play Console install reporting first. If first-party event collection is added, disclose it, collect only aggregate events, and do not record foreground app names or chat content. Browser pages cannot reliably confirm that the app was installed or that the user switched to TikTok.

Before promoting installation, verify on real Android devices that:

- The site prompt appears only after a visitor requests floating chat and offers a working website fallback.
- The Play Store listing installs the signed package `com.kokwet.voicecircle` and the app accepts the production HTTPS site origin.
- Denying overlay access does not crash the app; the app explains how to enable it and lets the visitor continue on the website.
- After the user starts the overlay, the chat stays available while TikTok is in front; the user can drag, resize, collapse, restore, and stop it.
- The keyboard, microphone denial/grant, poor network, ended live topic, Android process recovery, and Android versions 8 through current are checked.
- iOS and unsupported browsers receive honest split-screen/browser guidance, not an Android install prompt.

## Platform and release notes

- The overlay is an Android-only capability. iOS does not allow arbitrary third-party app overlays over TikTok.
- Android requires the user's explicit overlay permission and displays a foreground-service notification while the chat is active.
- The project declares the Android `specialUse` foreground-service type for this user-started overlay. Before Play Store release, review the current foreground-service and overlay policies and complete the required Play Console declarations. Sideloading can be used for development.
- Build and device verification require Android Studio/SDK and Gradle. Those tools are not installed in the current workspace, so this project has not yet been compiled or run on a device. Until a signed app is built, policy-reviewed, and published, the website must not claim that installation is available.
