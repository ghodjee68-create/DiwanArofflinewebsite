# Diwan Offline for Android

This is an Android Studio project for making a local, offline copy of the public site:
https://www.diwanalarabia.com/Default.aspx

## Build
1. Install Android Studio (current stable) and Android SDK 35.
2. Open this folder in Android Studio.
3. Allow Gradle sync and install the Android SDK components if prompted.
4. Connect an Android phone with USB debugging enabled, or use an emulator.
5. Choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. The debug APK is normally written to `app/build/outputs/apk/debug/app-debug.apk`.

## Use
1. Install the APK.
2. Connect to the internet and open the app.
3. Tap **تنزيل الموقع** and keep the app open until it reports completion.
4. Tap **فتح النسخة المحفوظة**. The saved pages are stored in the app's private storage and can be opened without internet.

## Important limitations
- The crawler is bounded to 10,000 discovered HTML pages and same-host resources.
- It follows ordinary GET links. ASP.NET postbacks, search forms, session-dependent pages, JavaScript-generated links, CAPTCHA/login pages, and external resources may not be captured.
- A complete mirror cannot be guaranteed until a real crawl is run and its page/resource counts and broken links are checked.
- This project is source code, not a precompiled APK. Build it with Android Studio as described above.
- Respect the website owner's terms, copyright, and crawl restrictions. The app is intended for personal offline access.


## Build an APK in GitHub Actions (without installing Android Studio)
This project includes `.github/workflows/build-apk.yml`.

1. Create a new GitHub repository and upload the contents of this ZIP.
2. Open the repository's **Actions** tab.
3. Select **Build Android APK** and click **Run workflow** (or push to `main`).
4. When the run finishes, open it and download the `DiwanOffline-debug-apk` artifact.
5. Extract the artifact and install `app-debug.apk` on your Android phone. Android may ask you to allow installation from that source.

The APK produced by this workflow is a debug build, suitable for personal installation/testing.
