# Schoolware++ (unofficial Schoolware client)

Material 3 Expressive grade viewer for Schoolware WebLeerling (hhscholen.schoolware.be).
Kotlin + Jetpack Compose, minSdk 21 (Android 5.0), no analytics, no backend: the app talks only
to your school's Schoolware server and keeps everything on the phone.

## Build
1. Open this folder in a current Android Studio (it will sync Gradle; accept any version-upgrade prompts).
   If the Gradle wrapper jar is missing, let Studio download Gradle, or run `gradle wrapper --gradle-version 8.11.1`.
2. Run on a device or emulator.

## How it works
- Sign-in: a WebView opens Schoolware's own Microsoft login. Passwords/MFA go to Microsoft only.
  When Schoolware's start page loads, the app reads the session cookie, verifies it with a real API
  call, then stores it (Android Keystore encrypted on Android 6+).
- Data: `REST/Leerling/` -> `REST/schooljaar` -> `REST/PuntenbladGridLeerling`.
  Score = `NumeriekAsString` x `BeoordelingMomentNoemer`.
- Sign out clears the stored cookie and all WebView cookies.

## Known unknowns (verify first)
- That the session cookie alone is enough for the REST calls (the sign-in screen's verification step tests this).
- Exact behaviour when a session expires (treated as: 401/403 or a non-JSON reply).
- Files in `ui/` use experimental Material 3 Expressive APIs; names can shift between releases.
