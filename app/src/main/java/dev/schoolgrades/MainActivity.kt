package dev.schoolgrades

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.schoolgrades.ui.GradesScreen
import dev.schoolgrades.ui.GradesTheme
import dev.schoolgrades.ui.SignInScreen

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GradesTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                var authenticated by rememberSaveable { mutableStateOf(false) }

                Surface(Modifier.fillMaxSize()) {
                    when (state.phase) {
                        Phase.Starting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                        Phase.SignIn, Phase.Verifying -> {
                            authenticated = false
                            SignInScreen(
                                attempt = state.loginAttempt,
                                verifying = state.phase == Phase.Verifying,
                                error = state.error,
                                onReached = vm::onSignInPageReached,
                                onRetry = vm::restartSignIn,
                            )
                        }
                        Phase.Grades -> {
                            if (state.biometricEnabled && !authenticated) {
                                LaunchedEffect(Unit) {
                                    promptBiometric(
                                        onSuccess = { authenticated = true },
                                        onError = { /* Keep locked screen */ }
                                    )
                                }
                                LockedScreen(
                                    onUnlock = {
                                        promptBiometric(
                                            onSuccess = { authenticated = true },
                                            onError = { /* Error handled in prompt */ }
                                        )
                                    }
                                )
                            } else {
                                GradesScreen(
                                    state = state,
                                    onRefresh = vm::refresh,
                                    onSignOut = {
                                        authenticated = false
                                        vm.signOut()
                                    },
                                    onToggleBiometric = { enabled ->
                                        if (enabled) {
                                            promptBiometric(
                                                onSuccess = { vm.setBiometricEnabled(true) },
                                                onError = { /* Biometric prompt cancelled/failed */ }
                                            )
                                        } else {
                                            vm.setBiometricEnabled(false)
                                        }
                                    },
                                    onDownloadReport = vm::downloadReport,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun promptBiometric(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Schoolware++ Vergrendeld")
            .setSubtitle("Ontgrendel met je vingerafdruk, gezicht of pincode")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}

@Composable
private fun LockedScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text("Schoolware++ is vergrendeld", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Ontgrendel de app met je biometrische gegevens om je punten te bekijken.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) {
                Text("Ontgrendelen")
            }
        }
    }
}
