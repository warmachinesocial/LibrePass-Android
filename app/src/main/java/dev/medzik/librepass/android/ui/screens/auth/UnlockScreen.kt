package dev.medzik.librepass.android.ui.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import dev.medzik.libcrypto.AesCbc
import dev.medzik.libcrypto.EncryptException
import dev.medzik.librepass.android.R
import dev.medzik.librepass.android.data.Repository
import dev.medzik.librepass.android.ui.Argument
import dev.medzik.librepass.android.ui.Screen
import dev.medzik.librepass.android.ui.composables.common.LoadingIndicator
import dev.medzik.librepass.android.ui.composables.common.TextInputField
import dev.medzik.librepass.android.ui.composables.common.TopBar
import dev.medzik.librepass.android.utils.KeyStoreAlias
import dev.medzik.librepass.android.utils.KeyStoreUtils
import dev.medzik.librepass.android.utils.navigation.navigate
import dev.medzik.librepass.android.utils.showBiometricPrompt
import dev.medzik.librepass.client.utils.Cryptography.computeBasePasswordHash
import dev.medzik.librepass.types.api.auth.UserArgon2idParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun UnlockScreen(navController: NavController) {
    // get composable context
    // must be FragmentActivity because of biometric prompt
    val context = LocalContext.current as FragmentActivity

    // get credentials from database
    val repository = Repository(context = context)
    val dbCredentials = repository.credentials.get()!!
    val encryptedEncryptionKey = dbCredentials.encryptionKey

    // password state
    val password = remember { mutableStateOf("") }
    // loading state
    var loading by remember { mutableStateOf(false) }

    // snackbar state
    val snackbarHostState = remember { SnackbarHostState() }
    // coroutine scope
    val scope = rememberCoroutineScope()

    fun onUnlock(password: String) {
        // disable button
        loading = true

        lateinit var encryptionKey: String

        scope.launch(Dispatchers.IO) {
            try {
                loading = true

                // compute base password hash
                val basePassword = computeBasePasswordHash(
                    password = password,
                    email = dbCredentials.email,
                    parameters = UserArgon2idParameters(
                        memory = dbCredentials.memory,
                        iterations = dbCredentials.iterations,
                        parallelism = dbCredentials.parallelism,
                        version = dbCredentials.version
                    )
                ).toHexHash()

                // decrypt encryption key
                encryptionKey = AesCbc.decrypt(
                    encryptedEncryptionKey,
                    basePassword
                )
            } catch (e: EncryptException) {
                // if password is invalid
                loading = false
                snackbarHostState.showSnackbar(context.getString(R.string.Error_InvalidCredentials))
            } finally {
                // run only if loading is true (if no error occurred)
                if (loading) {
                    scope.launch(Dispatchers.Main) {
                        navController.navigate(
                            screen = Screen.Dashboard,
                            argument = Argument.EncryptionKey to encryptionKey,
                            disableBack = true
                        )
                    }
                }
            }
        }
    }

    fun showBiometric() {
        showBiometricPrompt(
            context = context,
            cipher = KeyStoreUtils.getCipherForDecryption(
                alias = KeyStoreAlias.ENCRYPTION_KEY.name,
                initializationVector = dbCredentials.biometricEncryptionKeyIV!!
            ),
            onAuthenticationSucceeded = { cipher ->
                val encryptionKey = KeyStoreUtils.decrypt(
                    cipher = cipher,
                    data = dbCredentials.biometricEncryptionKey!!
                )

                scope.launch(Dispatchers.IO) {
                    scope.launch(Dispatchers.Main) {
                        navController.navigate(
                            Screen.Dashboard.fill(
                                Argument.EncryptionKey to encryptionKey
                            )
                        ) {
                            // disable back navigation
                            popUpTo(Screen.Unlock.get) { inclusive = true }
                        }
                    }
                }
            },
            onAuthenticationFailed = { }
        )
    }

    LaunchedEffect(scope) {
        if (dbCredentials.biometricEnabled) {
            showBiometric()
        }
    }

    Scaffold(
        topBar = {
            TopBar(title = stringResource(id = R.string.TopBar_Unlock))
        },
        modifier = Modifier.navigationBarsPadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            TextInputField(
                label = stringResource(id = R.string.InputField_Password),
                value = password.value,
                onValueChange = { password.value = it },
                hidden = true,
                keyboardType = KeyboardType.Password
            )

            Button(
                onClick = { onUnlock(password.value) },
                enabled = password.value.isNotEmpty() && !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .padding(horizontal = 80.dp)
            ) {
                if (loading) {
                    LoadingIndicator(animating = true)
                } else {
                    Text(text = stringResource(id = R.string.Button_Unlock))
                }
            }

            if (dbCredentials.biometricEnabled) {
                OutlinedButton(
                    onClick = { showBiometric() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .padding(horizontal = 80.dp)
                ) {
                    Text(text = stringResource(id = R.string.Button_UseBiometric))
                }
            }
        }
    }
}