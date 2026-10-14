package com.azza.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.azza.core.common.AppError
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.User
import com.azza.core.ui.Avatar
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadingState
import kotlinx.serialization.Serializable

@Serializable data object ProfileRoute

fun NavGraphBuilder.profileScreen(onSignIn: () -> Unit) {
    composable<ProfileRoute> { ProfileScreen(onSignIn = onSignIn) }
}

@Composable
fun ProfileScreen(onSignIn: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        ProfileUiState.Loading -> LoadingState()
        ProfileUiState.SignedOut -> SignedOut(onSignIn)
        is ProfileUiState.Failed ->
            if (s.error == AppError.Unauthenticated) SignedOut(onSignIn) else ErrorState(s.error, onRetry = { viewModel.retry() })
        is ProfileUiState.Ready -> Profile(s.user, onLogout = { viewModel.logout() })
    }
}

/** The profile header's slots in their phone order: handle, photo, name/bio, then actions (web §4). */
@Composable
private fun Profile(user: User, onLogout: () -> Unit) {
    val colors = AzzaTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("@${user.username}", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(url = user.avatarUrl, size = 80)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(user.displayName, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            user.bio?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profile_sign_out))
        }
    }
}

@Composable
private fun SignedOut(onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.profile_signed_out),
            textAlign = TextAlign.Center,
            color = AzzaTheme.colors.textSecondary,
        )
        Button(onClick = onSignIn) { Text(stringResource(R.string.profile_sign_in)) }
    }
}
