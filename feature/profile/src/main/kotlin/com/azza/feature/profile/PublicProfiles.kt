package com.azza.feature.profile

import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.ui.platform.LocalConfiguration
import com.azza.core.data.StoresRepository
import com.azza.core.data.Branch
import androidx.compose.material.icons.outlined.Share
import com.azza.core.ui.LocalWebLinks
import com.azza.core.ui.shareLink
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import com.azza.core.ui.ReportDialog
import com.azza.core.data.ReportTarget
import com.azza.core.data.ReportReason
import com.azza.core.data.ReportsRepository
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.AuthRepository
import com.azza.core.data.CursorPager
import com.azza.core.data.FollowRepository
import com.azza.core.data.PagedState
import com.azza.core.data.ProfilesRepository
import com.azza.core.data.UserRepository
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.PostTile
import com.azza.core.model.PublicProfile
import com.azza.core.model.StorePage
import com.azza.core.ui.Avatar
import com.azza.core.ui.EmptyState
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadMoreEffect
import com.azza.core.ui.LoadingState
import com.azza.core.ui.handle
import com.azza.core.ui.loadingMoreFooter
import com.azza.core.ui.postGrid
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** The web's /u/[username]. */
@Serializable data class UserProfileRoute(val username: String)

/** The web's /stores/[slug]; an id works too. */
@Serializable data class StoreProfileRoute(val idOrSlug: String)

sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val error: AppError) : Loadable<Nothing>
}

/** The Follow button: hidden on one's own page, a sign-in prompt when signed out. */
enum class FollowButton { HIDDEN, FOLLOW, FOLLOWING }

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profiles: ProfilesRepository,
    private val follow: FollowRepository,
    private val auth: AuthRepository,
    private val reports: ReportsRepository,
    users: UserRepository,
) : ViewModel() {
    private val username = savedStateHandle.toRoute<UserProfileRoute>().username

    private val _state = MutableStateFlow<Loadable<PublicProfile>>(Loadable.Loading)
    val state: StateFlow<Loadable<PublicProfile>> = _state.asStateFlow()

    private var userId: String? = null
    val posts = CursorPager<PostTile>(viewModelScope, { it.id }) { cursor ->
        profiles.personalPosts(userId ?: return@CursorPager Outcome.Failure(AppError.Unexpected(IllegalStateException())), cursor)
    }

    val followButton: StateFlow<FollowButton> = combine(_state, follow.followedUsers, users.currentUser) { s, followed, me ->
        val profile = (s as? Loadable.Ready)?.value ?: return@combine FollowButton.HIDDEN
        when {
            me?.id == profile.id -> FollowButton.HIDDEN
            profile.id in followed -> FollowButton.FOLLOWING
            else -> FollowButton.FOLLOW
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FollowButton.HIDDEN)

    private val _signInRequired = Channel<Unit>(Channel.CONFLATED)
    val signInRequired = _signInRequired.receiveAsFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.value = Loadable.Loading
        _state.value = when (val r = profiles.user(username)) {
            is Outcome.Success -> Loadable.Ready(r.value).also { userId = r.value.id; posts.refresh() }
            is Outcome.Failure -> Loadable.Failed(r.error)
        }
    }

    /** Reporting needs an account; a guest is sent to sign in, as for Follow. */
    suspend fun canReport(): Boolean {
        if (auth.isSignedIn.first()) return true
        _signInRequired.send(Unit)
        return false
    }

    suspend fun report(reason: ReportReason, details: String): Outcome<Unit> {
        val id = userId ?: return Outcome.Failure(AppError.Unexpected(IllegalStateException()))
        return reports.report(ReportTarget.USER, id, reason, details)
    }

    fun toggleFollow() = viewModelScope.launch {
        val profile = (_state.value as? Loadable.Ready)?.value ?: return@launch
        if (!auth.isSignedIn.first()) return@launch _signInRequired.send(Unit)
        val now = followButton.value == FollowButton.FOLLOW
        if (follow.setFollowingUser(profile.id, now) is Outcome.Success) {
            // The count is the server's; nudge it locally rather than reload the profile.
            _state.value = Loadable.Ready(profile.copy(followerCount = (profile.followerCount + if (now) 1 else -1).coerceAtLeast(0)))
        }
    }
}

@HiltViewModel
class StoreProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profiles: ProfilesRepository,
    private val follow: FollowRepository,
    private val auth: AuthRepository,
    private val reports: ReportsRepository,
    private val stores: StoresRepository,
    users: UserRepository,
) : ViewModel() {
    private val idOrSlug = savedStateHandle.toRoute<StoreProfileRoute>().idOrSlug

    private val _branches = MutableStateFlow<List<Branch>>(emptyList())
    /** Its active branches (public), for the branch list. */
    val branches: StateFlow<List<Branch>> = _branches.asStateFlow()

    private val _state = MutableStateFlow<Loadable<StorePage>>(Loadable.Loading)
    val state: StateFlow<Loadable<StorePage>> = _state.asStateFlow()

    private var storeId: String? = null
    val posts = CursorPager<PostTile>(viewModelScope, { it.id }) { cursor ->
        profiles.storePosts(storeId ?: return@CursorPager Outcome.Failure(AppError.Unexpected(IllegalStateException())), cursor)
    }

    val followButton: StateFlow<FollowButton> = combine(_state, follow.followedStores, users.currentUser) { s, followed, me ->
        val store = (s as? Loadable.Ready)?.value ?: return@combine FollowButton.HIDDEN
        when {
            me?.id == store.ownerId -> FollowButton.HIDDEN
            store.id in followed -> FollowButton.FOLLOWING
            else -> FollowButton.FOLLOW
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FollowButton.HIDDEN)

    /** The signed-in person runs this store: its page offers "New post". */
    val isOwner: StateFlow<Boolean> = combine(_state, users.currentUser) { s, me ->
        val store = (s as? Loadable.Ready)?.value
        store != null && me?.id == store.ownerId
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _signInRequired = Channel<Unit>(Channel.CONFLATED)
    val signInRequired = _signInRequired.receiveAsFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.value = Loadable.Loading
        _state.value = when (val r = profiles.store(idOrSlug)) {
            is Outcome.Success -> Loadable.Ready(r.value).also {
                storeId = r.value.id
                posts.refresh()
                viewModelScope.launch {
                    _branches.value = (stores.branches(r.value.id) as? Outcome.Success)?.value.orEmpty().filter { it.status == "ACTIVE" }
                }
            }
            is Outcome.Failure -> Loadable.Failed(r.error)
        }
    }

    /** Reporting needs an account; a guest is sent to sign in, as for Follow. */
    suspend fun canReport(): Boolean {
        if (auth.isSignedIn.first()) return true
        _signInRequired.send(Unit)
        return false
    }

    suspend fun report(reason: ReportReason, details: String): Outcome<Unit> {
        val id = storeId ?: return Outcome.Failure(AppError.Unexpected(IllegalStateException()))
        return reports.report(ReportTarget.STORE, id, reason, details)
    }

    fun toggleFollow() = viewModelScope.launch {
        val store = (_state.value as? Loadable.Ready)?.value ?: return@launch
        if (!auth.isSignedIn.first()) return@launch _signInRequired.send(Unit)
        val now = followButton.value == FollowButton.FOLLOW
        if (follow.setFollowingStore(store.id, now) is Outcome.Success) {
            _state.value = Loadable.Ready(store.copy(followerCount = (store.followerCount + if (now) 1 else -1).coerceAtLeast(0)))
        }
    }
}

@Composable
fun UserProfileScreen(
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onSignIn: () -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val button by viewModel.followButton.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.signInRequired.collect { onSignIn() } }
    val title = (state as? Loadable.Ready)?.value?.username?.let(::handle).orEmpty()
    var reporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (reporting) ReportDialog(ReportTarget.USER, onDismiss = { reporting = false }, submit = viewModel::report)
    ProfileScaffold(title, onBack, onReport = { scope.launch { if (viewModel.canReport()) reporting = true }; Unit }.takeIf { button != FollowButton.HIDDEN }, shareUrl = (state as? Loadable.Ready)?.value?.username?.let(LocalWebLinks.current::user)) {
        when (val s = state) {
            Loadable.Loading -> LoadingState()
            is Loadable.Failed -> notFoundOrError(s.error, R.string.profile_user_not_found, viewModel::load)
            is Loadable.Ready -> PostsPage(viewModel.posts, onOpenProduct) {
                item(key = "header") {
                    ProfileHeader(
                        avatarUrl = s.value.avatarUrl,
                        isStore = false,
                        name = s.value.displayName,
                        lines = listOfNotNull(s.value.cityName, s.value.bio?.takeIf { it.isNotBlank() }),
                        followers = s.value.followerCount,
                        button = button,
                        onFollow = viewModel::toggleFollow,
                        posts = s.value.postCount,
                    )
                }
            }
        }
    }
}

@Composable
fun StoreProfileScreen(
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onSignIn: () -> Unit,
    onOpen: (Any) -> Unit,
    onDirections: (branchId: String, name: String) -> Unit,
    onPostFromStore: (storeId: String) -> Unit,
    viewModel: StoreProfileViewModel = hiltViewModel(),
) {
    val isOwner by viewModel.isOwner.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    val button by viewModel.followButton.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.signInRequired.collect { onSignIn() } }
    val title = (state as? Loadable.Ready)?.value?.slug?.let(::handle).orEmpty()
    var reporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (reporting) ReportDialog(ReportTarget.STORE, onDismiss = { reporting = false }, submit = viewModel::report)
    ProfileScaffold(title, onBack, onReport = { scope.launch { if (viewModel.canReport()) reporting = true }; Unit }.takeIf { button != FollowButton.HIDDEN }, shareUrl = (state as? Loadable.Ready)?.value?.slug?.let(LocalWebLinks.current::store)) {
        when (val s = state) {
            Loadable.Loading -> LoadingState()
            is Loadable.Failed -> notFoundOrError(s.error, R.string.profile_store_not_found, viewModel::load)
            is Loadable.Ready -> PostsPage(viewModel.posts, onOpenProduct) {
                val store = s.value
                item(key = "header") {
                    ProfileHeader(
                        avatarUrl = store.avatarUrl,
                        isStore = true,
                        name = store.name,
                        lines = listOfNotNull(store.occupation?.forLanguage(language), store.description?.takeIf { it.isNotBlank() }),
                        followers = store.followerCount,
                        button = button,
                        onFollow = viewModel::toggleFollow,
                        posts = store.postCount,
                        onNewPost = { onPostFromStore(store.id) }.takeIf { isOwner },
                    )
                }
                item(key = "info") {
                    Column {
                        // With a single branch the address IS the branch: one tap to its page, no list.
                        val only = branches.singleOrNull()
                        store.address?.let { address ->
                            InfoLine(Icons.Outlined.Place, address, onClick = only?.let { b -> { onOpen(BranchPageRoute(store.id, b.id, store.slug, store.name)) } })
                        }
                        // One branch with a pin: the way there is one tap from the store's page.
                        only?.takeIf { it.location != null }?.let { b ->
                            InfoLine(Icons.Outlined.Directions, stringResource(R.string.profile_directions)) { onDirections(b.id, store.name) }
                        }
                        store.phone?.let { phone -> InfoLine(Icons.Outlined.Phone, phone) { dial(context, phone) } }
                        if (store.hours.isNotEmpty()) HoursCard(store.hours, store.openStatus)
                        else if (store.openStatus != "UNKNOWN") Row(Modifier.padding(horizontal = 16.dp)) { OpenBadge(store.openStatus) }
                        store.ownerUsername?.let { owner ->
                            Box(Modifier.padding(top = 8.dp)) { RunBy(store.ownerName, owner) { onOpen(UserProfileRoute(owner)) } }
                        }
                        BranchesSection(branches.takeIf { it.size > 1 }.orEmpty()) { b ->
                            onOpen(BranchPageRoute(store.id, b.id, store.slug, store.name))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScaffold(title: String, onBack: () -> Unit, onReport: (() -> Unit)?, shareUrl: String? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        containerColor = AzzaTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.profile_back)) }
                },
                actions = {
                    if (shareUrl != null) {
                        IconButton(onClick = { shareLink(context, title, shareUrl) }) {
                            Icon(Icons.Outlined.Share, stringResource(com.azza.core.ui.R.string.share))
                        }
                    }
                    if (onReport != null) {
                        IconButton(onClick = onReport) {
                            Icon(Icons.Outlined.Flag, stringResource(com.azza.core.ui.R.string.report_button), tint = AzzaTheme.colors.textSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzzaTheme.colors.bg),
            )
        },
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { content() } }
}

@Composable
private fun notFoundOrError(error: AppError, notFound: Int, retry: () -> Unit) {
    if ((error as? AppError.Api)?.status == 404) EmptyState(stringResource(notFound)) else ErrorState(error, onRetry = { retry() })
}

/** Header item(s) first, then the grid of posts, in one scroll. */
@Composable
internal fun PostsPage(pager: CursorPager<PostTile>, onOpenProduct: (String) -> Unit, header: LazyListScope.() -> Unit) {
    val posts: PagedState<PostTile> by pager.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, posts.items.size, pager::loadMore)
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        header()
        when {
            posts.loadingFirst -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("…") } }
            posts.items.isEmpty() -> item {
                Text(stringResource(R.string.profile_no_posts), color = AzzaTheme.colors.textMuted, modifier = Modifier.padding(16.dp))
            }
            else -> postGrid(posts.items, onOpenProduct)
        }
        loadingMoreFooter(posts.loadingMore)
    }
}

/** The web's profile header in its phone order: photo with the counter beside it, name and lines, then the action. */
@Composable
internal fun ProfileHeader(
    avatarUrl: String?,
    isStore: Boolean,
    name: String,
    lines: List<String>,
    followers: Int,
    button: FollowButton,
    onFollow: () -> Unit,
    posts: Int? = null,
    /** Your own store's page: a "New post" button (there is no Follow there). */
    onNewPost: (() -> Unit)? = null,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Avatar(url = avatarUrl, isStore = isStore, size = 80)
            if (posts != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(posts.toString(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        pluralStringResource(R.plurals.profile_posts, posts),
                        color = AzzaTheme.colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(followers.toString(), style = MaterialTheme.typography.titleMedium)
                Text(
                    pluralStringResource(R.plurals.profile_followers, followers),
                    color = AzzaTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            // Written by the person, in their language: laid out by its own direction, not the UI's.
            lines.forEach {
                Text(
                    it,
                    color = AzzaTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        onNewPost?.let { newPost ->
            Button(onClick = newPost, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Add, null)
                Text(" " + stringResource(R.string.profile_new_post))
            }
        }
        when (button) {
            FollowButton.HIDDEN -> Unit
            FollowButton.FOLLOW -> Button(onClick = onFollow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.profile_follow)) }
            FollowButton.FOLLOWING -> OutlinedButton(onClick = onFollow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.profile_following))
            }
        }
    }
}

fun NavGraphBuilder.publicProfileScreens(
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onSignIn: () -> Unit,
    onOpen: (Any) -> Unit,
    onDirections: (branchId: String, name: String) -> Unit,
    onPostFromStore: (storeId: String) -> Unit = {},
) {
    composable<UserProfileRoute> { UserProfileScreen(onBack, onOpenProduct, onSignIn) }
    composable<StoreProfileRoute> { StoreProfileScreen(onBack, onOpenProduct, onSignIn, onOpen, onDirections, onPostFromStore) }
    composable<BranchPageRoute> { BranchPageScreen(onBack, onOpenStore = { onOpen(StoreProfileRoute(it)) }, onOpenProduct = onOpenProduct, onDirections = onDirections) }
}
