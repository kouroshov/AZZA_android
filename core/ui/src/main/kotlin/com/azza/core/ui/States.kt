package com.azza.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.azza.core.common.AppError
import com.azza.core.designsystem.theme.AzzaTheme

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorState(error: AppError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = errorMessage(error),
            style = MaterialTheme.typography.bodyMedium,
            color = AzzaTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Button(onClick = onRetry) { Text(stringResource(R.string.core_ui_retry)) }
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = AzzaTheme.colors.textMuted, textAlign = TextAlign.Center)
    }
}

/** A sentence for the person, never the raw code — the code is for logic. */
@Composable
fun errorMessage(error: AppError): String = when (error) {
    AppError.Network -> stringResource(R.string.core_ui_error_network)
    AppError.Unauthenticated -> stringResource(R.string.core_ui_error_signed_out)
    is AppError.Api -> when {
        error.status == 404 -> stringResource(R.string.core_ui_error_not_found)
        error.status == 429 -> stringResource(R.string.core_ui_error_rate_limited)
        error.status >= 500 -> stringResource(R.string.core_ui_error_server)
        else -> error.message.ifBlank { stringResource(R.string.core_ui_error_generic) }
    }
    is AppError.Unexpected -> stringResource(R.string.core_ui_error_generic)
}
