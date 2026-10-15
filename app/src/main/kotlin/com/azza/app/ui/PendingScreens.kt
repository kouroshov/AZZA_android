package com.azza.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.azza.app.R
import com.azza.core.ui.EmptyState
import kotlinx.serialization.Serializable

// Destinations whose feature modules are not built yet (docs/ROADMAP.md).
// Each becomes its own :feature module; the route moves there with it.

@Serializable data object CreateRoute

fun NavGraphBuilder.pendingScreens() {
    composable<CreateRoute> { ComingSoon() }
}

@Composable
private fun ComingSoon() = EmptyState(stringResource(R.string.coming_soon))
