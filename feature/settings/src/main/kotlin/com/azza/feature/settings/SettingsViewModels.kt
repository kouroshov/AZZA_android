package com.azza.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.AccountRepository
import com.azza.core.data.AuthRepository
import com.azza.core.data.CursorPager
import com.azza.core.data.SupportRepository
import com.azza.core.data.ThemeChoice
import com.azza.core.data.TicketDetail
import com.azza.core.data.TicketSummary
import com.azza.core.data.UserRepository
import com.azza.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val account: AccountRepository,
    private val auth: AuthRepository,
    users: UserRepository,
) : ViewModel() {
    val signedIn: StateFlow<Boolean> = auth.isSignedIn.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val user: StateFlow<User?> = users.currentUser

    val theme: StateFlow<ThemeChoice> = account.theme
        .map { raw -> raw?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() } ?: ThemeChoice.DARK }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeChoice.DARK)

    fun setTheme(choice: ThemeChoice) = viewModelScope.launch { account.setTheme(choice) }

    val phoneNotifications: StateFlow<Boolean> = account.phoneNotifications.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setPhoneNotifications(on: Boolean) = viewModelScope.launch { account.setPhoneNotifications(on) }

    val appLock: StateFlow<Boolean> = account.appLock.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setAppLock(on: Boolean) = viewModelScope.launch { account.setAppLock(on) }

    fun signOut() = viewModelScope.launch { auth.logout() }
}

data class EditProfileState(
    val firstName: String = "",
    val lastName: String = "",
    val bio: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: AppError? = null,
)

/** The web's bio limit, in characters as a person counts them (code points). */
const val BIO_MAX = 150

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val account: AccountRepository,
    users: UserRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        users.currentUser.value.let { EditProfileState(it?.firstName.orEmpty(), it?.lastName.orEmpty(), it?.bio.orEmpty()) },
    )
    val state: StateFlow<EditProfileState> = _state.asStateFlow()

    fun edit(transform: EditProfileState.() -> EditProfileState) = _state.update { it.transform().copy(error = null, saved = false) }

    fun save() {
        val s = _state.value
        if (s.saving || s.firstName.isBlank()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = account.updateProfile(s.firstName, s.lastName, s.bio)) {
                is Outcome.Success -> _state.update { it.copy(saving = false, saved = true) }
                is Outcome.Failure -> _state.update { it.copy(saving = false, error = r.error) }
            }
        }
    }
}

@HiltViewModel
class SupportListViewModel @Inject constructor(support: SupportRepository) : ViewModel() {
    val pager = CursorPager<TicketSummary>(viewModelScope, { it.id }) { support.page(it) }
}

/** apps/api's TICKET_CATEGORIES. */
val TICKET_CATEGORIES = listOf("ACCOUNT", "ORDER", "PAYMENT", "LISTING", "STORE", "ABUSE", "OTHER")

data class NewTicketState(
    val category: String = "OTHER",
    val subject: String = "",
    val body: String = "",
    val sending: Boolean = false,
    val error: AppError? = null,
    val createdId: String? = null,
)

@HiltViewModel
class NewTicketViewModel @Inject constructor(private val support: SupportRepository) : ViewModel() {
    private val _state = MutableStateFlow(NewTicketState())
    val state: StateFlow<NewTicketState> = _state.asStateFlow()

    fun edit(transform: NewTicketState.() -> NewTicketState) = _state.update { it.transform().copy(error = null) }

    fun send() {
        val s = _state.value
        if (s.sending || s.subject.trim().length < 3 || s.body.isBlank()) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            when (val r = support.create(s.subject, s.category, s.body)) {
                is Outcome.Success -> _state.update { it.copy(sending = false, createdId = r.value.id) }
                is Outcome.Failure -> _state.update { it.copy(sending = false, error = r.error) }
            }
        }
    }
}

data class TicketState(
    val ticket: TicketDetail? = null,
    val loading: Boolean = true,
    val error: AppError? = null,
    val reply: String = "",
    val sending: Boolean = false,
    val actionError: AppError? = null,
)

@HiltViewModel
class TicketViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val support: SupportRepository,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<TicketRoute>().ticketId

    private val _state = MutableStateFlow(TicketState())
    val state: StateFlow<TicketState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.update {
            when (val r = support.detail(id)) {
                is Outcome.Success -> it.copy(ticket = r.value, loading = false, error = null)
                is Outcome.Failure -> it.copy(loading = false, error = r.error)
            }
        }
    }

    fun editReply(text: String) = _state.update { it.copy(reply = text.take(4000), actionError = null) }

    fun send() {
        if (_state.value.reply.isBlank()) return
        act { support.reply(id, _state.value.reply) }
    }

    fun close() = act { support.close(id) }

    private fun act(call: suspend () -> Outcome<TicketDetail>) {
        if (_state.value.sending) return
        _state.update { it.copy(sending = true, actionError = null) }
        viewModelScope.launch {
            when (val r = call()) {
                is Outcome.Success -> _state.update { it.copy(sending = false, ticket = r.value, reply = "") }
                is Outcome.Failure -> _state.update { it.copy(sending = false, actionError = r.error) }
            }
        }
    }
}
