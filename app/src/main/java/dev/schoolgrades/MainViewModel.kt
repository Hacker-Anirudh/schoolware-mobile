package dev.schoolgrades

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class Phase { Starting, SignIn, Verifying, Grades }

data class UiState(
    val phase: Phase = Phase.Starting,
    val subjects: List<Subject> = emptyList(),
    val todoItems: List<AgendaPoint> = emptyList(),
    val todoError: String? = null,
    val reportPdfs: List<ReportPdfRow> = emptyList(),
    val refreshing: Boolean = false,
    val error: String? = null,
    val loginAttempt: Int = 0,
    val studierichting: String = "",
    val biometricEnabled: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app)
    private val api = SchoolwareApi()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var verifying = false

    init {
        val cookie = store.loadCookie()
        val bio = store.isBiometricEnabled()
        if (cookie == null) {
            _state.value = UiState(phase = Phase.SignIn, biometricEnabled = bio)
        } else {
            _state.value = UiState(biometricEnabled = bio)
            viewModelScope.launch { load(cookie) }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        store.setBiometricEnabled(enabled)
        _state.update { it.copy(biometricEnabled = enabled) }
    }

    /** Called by the WebView once Schoolware's start page has loaded after Microsoft sign-in. */
    fun onSignInPageReached() {
        if (verifying) return
        verifying = true
        _state.update { it.copy(phase = Phase.Verifying, error = null) }
        viewModelScope.launch {
            var ok = false
            // The page finishes authenticating in the background, so poll until the cookie works.
            for (attempt in 1..20) {
                val cookie = CookieManager.getInstance().getCookie(Config.SITE).orEmpty()
                try {
                    val student = api.student(cookie)
                    store.saveCookie(cookie)
                    CookieManager.getInstance().flush()
                    load(cookie, student)
                    ok = true
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    delay(750)
                }
            }
            verifying = false
            if (!ok) {
                _state.update { it.copy(phase = Phase.SignIn, error = "Couldn't finish signing in. Please try again.") }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val cookie = store.loadCookie()
            if (cookie == null) signOut() else load(cookie)
        }
    }

    fun restartSignIn() = signOut(null)

    private fun reportsDir() = File(getApplication<Application>().cacheDir, "reports")

    /** Returns the cached PDF for [docId], downloading it first if needed. */
    suspend fun downloadReport(docId: Long): File {
        val dir = reportsDir().apply { mkdirs() }
        val file = File(dir, "$docId.pdf")
        if (file.exists() && (file.length() > 0)) return file
        val cookie = store.loadCookie() ?: run {
            signOut("Your session expired. Please sign in again.")
            throw SessionExpiredException()
        }
        try {
            api.downloadDocument(cookie, docId, file)
        } catch (e: SessionExpiredException) {
            signOut("Your session expired. Please sign in again.")
            throw e
        }
        return file
    }

    fun signOut(message: String? = null) {
        store.clear()
        reportsDir().deleteRecursively()   // don't leave downloaded reports behind
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        verifying = false
        _state.value = UiState(
            phase = Phase.SignIn,
            error = message,
            loginAttempt = _state.value.loginAttempt + 1,
        )
    }

    private suspend fun load(cookie: String, verifiedStudent: Student? = null) {
        _state.update { it.copy(refreshing = true, error = null) }
        try {
            coroutineScope {
                val studentDeferred = async { verifiedStudent ?: api.student(cookie) }
                val yearDeferred = async { api.currentSchoolYear(cookie) }
                val student = studentDeferred.await()
                val year = yearDeferred.await()

                val loopbaanDeferred = async { api.loopbaanRow(cookie, student.id) }
                val gradesDeferred = async { api.grades(cookie, student.id, year.from) }
                val todoDeferred = async {
                    try {
                        api.upcomingAgendaPoints(cookie, year.to) to null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: SessionExpiredException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList<AgendaPoint>() to (e.message ?: "Couldn't load tasks and tests.")
                    }
                }
                val metadataDeferred = async {
                    api.subjectMetadata(cookie, student.id, loopbaanDeferred.await())
                }
                val pdfsDeferred = async { api.reportPdfs(cookie, student.id) }

                val loopbaan = loopbaanDeferred.await()
                val records = gradesDeferred.await()
                val (todoItems, todoError) = todoDeferred.await()
                val metaMap = metadataDeferred.await()
                val pdfs = pdfsDeferred.await()

                _state.update {
                    it.copy(
                        phase = Phase.Grades,
                        subjects = records.toSubjects(metaMap),
                        todoItems = todoItems,
                        todoError = todoError,
                        reportPdfs = pdfs,
                        studierichting = api.studierichting(loopbaan),
                        refreshing = false,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            signOut("Your session expired. Please sign in again.")
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    phase = Phase.Grades,
                    refreshing = false,
                    error = e.message ?: "Something went wrong. Pull down to retry.",
                )
            }
        }
    }
}
