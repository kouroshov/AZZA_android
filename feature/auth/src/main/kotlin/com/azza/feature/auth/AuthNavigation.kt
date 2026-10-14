package com.azza.feature.auth

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object LoginRoute

@Serializable data object RegisterRoute

fun NavGraphBuilder.authScreens(
    onSignedIn: () -> Unit,
    onOpenRegister: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    composable<LoginRoute> { LoginScreen(onSignedIn = onSignedIn, onCreateAccount = onOpenRegister) }
    composable<RegisterRoute> { RegisterScreen(onSignedIn = onSignedIn, onHaveAccount = onOpenLogin) }
}
