package com.azza.feature.auth

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

data class AuthUiState(
    val busy: Boolean = false,
    val error: AppError? = null,
    val done: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun login(email: String, password: String) = run { auth.login(email, password) }

    fun register(email: String, username: String, password: String, firstName: String, lastName: String) =
        run { auth.register(email, username, password, firstName, lastName) }

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
