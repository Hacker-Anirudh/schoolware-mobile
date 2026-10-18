# Schoolware++ (Inofficiële Schoolware-app)

Een moderne, snelle en privacyvriendelijke Android-app om je punten, taken en rapporten te bekijken op Schoolware WebLeerling.
Gebouwd met Kotlin, Jetpack Compose, Material 3 Expressive, minimale vereiste Android SDK 24 (Android 7.0+). Geen tracking, geen analytics en geen externe servers: de app communiceert rechtstreeks met de Schoolware-server van jouw school.

## Functionaliteiten
- **Puntenboek**: Bekijk al je vakken, evaluaties, percentages en gewogen gemiddelden per vak en algemeen.
- **Taken & Toetsen**: Overzicht van aankomende taken en toetsen.
- **PDF Rapporten**: Bekijk en download officiële PDF-rapporten rechtstreeks op je toestel.
- **Biometrische Beveiliging**: Optionele ontgrendeling via vingerafdruk, gezichtsherkenning of pincode.
- **Privacy & Veiligheid**: Inloggen gebeurt via Microsoft SSO in een beveiligde WebView. Je wachtwoord en MFA blijven volledig bij Microsoft.

## Bouwen
1. Open dit project in Android Studio.
2. Laat Gradle synchroniseren.
3. Bouw en voer uit op een toestel of emulator: `./gradlew assembleDebug`
