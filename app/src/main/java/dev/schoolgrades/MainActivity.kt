package dev.schoolgrades

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.schoolgrades.ui.GradesScreen
import dev.schoolgrades.ui.GradesTheme
import dev.schoolgrades.ui.SignInScreen

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GradesTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                Surface(Modifier.fillMaxSize()) {
                    when (state.phase) {
                        Phase.Starting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                        Phase.SignIn, Phase.Verifying -> SignInScreen(
                            attempt = state.loginAttempt,
                            verifying = state.phase == Phase.Verifying,
                            error = state.error,
                            onReached = vm::onSignInPageReached,
                            onRetry = vm::restartSignIn,
                        )
                        Phase.Grades -> GradesScreen(
                            state = state,
                            onRefresh = vm::refresh,
                            onSignOut = { vm.signOut() },
                            onDownloadReport = vm::downloadReport,
                        )
                    }
                }
            }
        }
    }
}
