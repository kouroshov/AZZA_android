package com.azza.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.PostAuthor
import com.azza.core.ui.LoadingState
import com.azza.core.ui.ProductFeed
import kotlinx.serialization.Serializable

/** The app's start destination — the web's phone bar puts /following first. */
@Serializable data object FeedRoute

fun NavGraphBuilder.feedScreen(
    onOpenProduct: (String) -> Unit,
    onOpenAuthor: (PostAuthor) -> Unit,
    onSignIn: () -> Unit,
    onExplore: () -> Unit,
) {
    composable<FeedRoute> { FollowingFeedScreen(onOpenProduct, onOpenAuthor, onSignIn, onExplore) }
}

@Composable
fun FollowingFeedScreen(
    onOpenProduct: (String) -> Unit,
    onOpenAuthor: (PostAuthor) -> Unit,
    onSignIn: () -> Unit,
    onExplore: () -> Unit,
    viewModel: FollowingFeedViewModel = hiltViewModel(),
) {
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    when (signedIn) {
        null -> LoadingState()
        false -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.feed_following_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.feed_signed_out), color = AzzaTheme.colors.textSecondary, textAlign = TextAlign.Center)
            Button(onClick = onSignIn) { Text(stringResource(R.string.feed_sign_in)) }
            OutlinedButton(onClick = onExplore) { Text(stringResource(R.string.feed_explore_cta)) }
        }
        true -> ProductFeed(
            pager = viewModel.pager,
            emptyText = stringResource(R.string.feed_following_empty),
            onOpenProduct = onOpenProduct,
            onOpenAuthor = onOpenAuthor,
            emptyAction = { Button(onClick = onExplore) { Text(stringResource(R.string.feed_explore_cta)) } },
        )
    }
}
