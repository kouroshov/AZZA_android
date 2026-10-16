package com.azza.feature.auth

import androidx.navigation.toRoute
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import com.azza.core.ui.latinDigits
import com.azza.core.model.Area
import com.azza.core.data.UsernameCheck
import com.azza.core.data.GeographyRepository
import com.azza.core.data.MyAccountRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.AccountRepository
import com.azza.core.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the sign-up form collects; [photo] is a content URI. */
data class SignUpForm(
    val firstName: String,
    val lastName: String,
    val username: String,
    val email: String,
    val phone: String,
    val province: String,
    val city: String,
    val password: String,
    val photo: String?,
)

data class AuthUiState(
    val busy: Boolean = false,
    val error: AppError? = null,
    val done: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val account: MyAccountRepository,
    private val geography: GeographyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun login(email: String, password: String) = run { auth.login(email, password) }

    fun register(email: String, username: String, password: String, firstName: String, lastName: String) =
        run { auth.register(email, username, password, firstName, lastName) }

    /** The web's sign-up: everything it asks, then the photo (if one was chosen) once the account exists. */
    fun register(form: SignUpForm) = run {
        val r = auth.register(
            form.email, form.username, form.password, form.firstName, form.lastName,
            phoneNumber = latinDigits(form.phone.trim()), province = form.province, city = form.city,
        )
        // The account is made either way; a photo that fails can be added again from Edit profile.
        if (r is Outcome.Success) form.photo?.let { account.uploadAvatar(it) }
        r
    }

    // ---- what the sign-up form reads as it is filled in

    private val _provinces = MutableStateFlow<List<Area>>(emptyList())
    val provinces: StateFlow<List<Area>> = _provinces.asStateFlow()
    private val _cities = MutableStateFlow<List<Area>>(emptyList())
    val cities: StateFlow<List<Area>> = _cities.asStateFlow()
    private val _username = MutableStateFlow<UsernameCheck?>(null)
    /** null while nothing (or too little) is typed. */
    val usernameCheck: StateFlow<UsernameCheck?> = _username.asStateFlow()
    private var checkJob: Job? = null

    fun loadProvinces() {
        if (_provinces.value.isNotEmpty()) return
        viewModelScope.launch { (geography.provinces() as? Outcome.Success)?.let { _provinces.value = it.value } }
    }

    fun loadCities(provinceName: String) {
        _cities.value = emptyList()
        val province = _provinces.value.firstOrNull { it.name == provinceName } ?: return
        viewModelScope.launch { (geography.cities(province.id) as? Outcome.Success)?.let { _cities.value = it.value } }
    }

    /** Asked half a second after the typing stops, as on the web. */
    fun checkUsername(name: String) {
        checkJob?.cancel()
        _username.value = null
        if (name.trim().length < 3) return
        checkJob = viewModelScope.launch {
            delay(500)
            _username.value = (account.checkUsername(name.trim().lowercase()) as? Outcome.Success)?.value
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun run(block: suspend () -> Outcome<Unit>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = block()) {
                is Outcome.Success -> _state.update { it.copy(busy = false, done = true) }
                is Outcome.Failure -> _state.update { it.copy(busy = false, error = result.error) }
            }
        }
    }
}

data class ForgotPasswordState(val sending: Boolean = false, val sent: Boolean = false, val error: AppError? = null)

/**
 * "Forgot password": apps/api answers the same whether or not the account exists,
 * so a success only ever says "if an account exists, a link was sent".
 */
@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(private val account: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow(ForgotPasswordState())
    val state: StateFlow<ForgotPasswordState> = _state.asStateFlow()

    fun send(email: String) {
        if (_state.value.sending || email.isBlank()) return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            when (val r = account.forgotPassword(email)) {
                is Outcome.Success -> _state.update { it.copy(sending = false, sent = true) }
                is Outcome.Failure -> _state.update { it.copy(sending = false, error = r.error) }
            }
        }
    }

    fun reset() = _state.update { ForgotPasswordState() }
}

data class TokenPageState(val busy: Boolean = false, val done: Boolean = false, val error: AppError? = null)

/** The emailed "choose a new password" link. */
@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val account: AccountRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val token = savedStateHandle.toRoute<ResetPasswordRoute>().token
    private val _state = MutableStateFlow(TokenPageState())
    val state: StateFlow<TokenPageState> = _state.asStateFlow()

    fun reset(password: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = account.resetPassword(token, password)) {
                // Every session of the account ended: this phone signs in again too.
                is Outcome.Success -> { auth.logout(); _state.update { it.copy(busy = false, done = true) } }
                is Outcome.Failure -> _state.update { it.copy(busy = false, error = r.error) }
            }
        }
    }
}

/** The emailed "confirm your new address" link: confirmed as soon as it is opened, like the web's page. */
@HiltViewModel
class ConfirmEmailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val account: AccountRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val token = savedStateHandle.toRoute<ConfirmEmailRoute>().token
    private val _state = MutableStateFlow(TokenPageState(busy = true))
    val state: StateFlow<TokenPageState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = account.confirmEmailChange(token)) {
                is Outcome.Success -> { auth.logout(); _state.value = TokenPageState(done = true) }
                is Outcome.Failure -> _state.value = TokenPageState(error = r.error)
            }
        }
    }
}
