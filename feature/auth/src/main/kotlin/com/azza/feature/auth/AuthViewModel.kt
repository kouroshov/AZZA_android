package com.azza.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
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
