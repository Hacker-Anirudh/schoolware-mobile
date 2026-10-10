package dev.schoolgrades

/** Everything school-specific lives here. */
object Config {
    const val SITE = "https://hhscholen.schoolware.be"
    const val APP = "$SITE/webleerling"
    const val API = "$APP/bin/server.fcgi"
    const val LOGIN_URL = "$API/oauth2/login/office365"   // starts the Microsoft sign-in
    const val START_PAGE = "$APP/start.html"              // where Schoolware lands after sign-in
}
