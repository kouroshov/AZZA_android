package com.azza.feature.auth

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object LoginRoute

@Serializable data object RegisterRoute

/** The emailed links, opened in the app. */
@Serializable data class ResetPasswordRoute(val token: String)
@Serializable data class ConfirmEmailRoute(val token: String)

fun NavGraphBuilder.authScreens(
    onSignedIn: () -> Unit,
    onOpenRegister: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    composable<LoginRoute> { LoginScreen(onSignedIn = onSignedIn, onCreateAccount = onOpenRegister) }
    composable<RegisterRoute> { RegisterScreen(onSignedIn = onSignedIn, onHaveAccount = onOpenLogin) }
    composable<ResetPasswordRoute> { ResetPasswordScreen(onSignIn = onOpenLogin) }
    composable<ConfirmEmailRoute> { ConfirmEmailScreen(onSignIn = onOpenLogin) }
}
