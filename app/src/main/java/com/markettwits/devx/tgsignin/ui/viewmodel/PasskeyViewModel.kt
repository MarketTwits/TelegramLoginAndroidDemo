package com.markettwits.devx.tgsignin.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markettwits.devx.tgsignin.data.model.PasskeyError
import com.markettwits.devx.tgsignin.data.model.PasskeyInfo
import com.markettwits.devx.tgsignin.data.model.sessionOrNull
import com.markettwits.devx.tgsignin.data.repository.AuthenticationRepository
import com.markettwits.devx.tgsignin.data.repository.PasskeyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface PasskeyPendingAction {
    data class Create(val suggestedName: String) : PasskeyPendingAction
    data class Rename(val passkeyId: String, val newName: String) : PasskeyPendingAction
    data class Delete(val passkeyId: String) : PasskeyPendingAction
}

sealed interface PasskeyUiState {
    data object Loading : PasskeyUiState
    data class Ready(val passkeys: List<PasskeyInfo>) : PasskeyUiState
    data class Creating(val passkeys: List<PasskeyInfo> = emptyList()) : PasskeyUiState
    data class Renaming(val passkeyId: String, val passkeys: List<PasskeyInfo> = emptyList()) : PasskeyUiState
    data class Deleting(val passkeyId: String, val passkeys: List<PasskeyInfo> = emptyList()) : PasskeyUiState
    data class ReauthenticationRequired(
        val pendingAction: PasskeyPendingAction,
        val passkeys: List<PasskeyInfo> = emptyList()
    ) : PasskeyUiState
    data class ExternalAuthenticationInProgress(
        val pendingAction: PasskeyPendingAction? = null,
        val passkeys: List<PasskeyInfo> = emptyList()
    ) : PasskeyUiState
    data class Error(
        val previousState: PasskeyUiState,
        val error: PasskeyError
    ) : PasskeyUiState
}

val PasskeyUiState.passkeys: List<PasskeyInfo>
    get() = when (this) {
        is PasskeyUiState.Loading -> emptyList()
        is PasskeyUiState.Ready -> passkeys
        is PasskeyUiState.Creating -> passkeys
        is PasskeyUiState.Renaming -> passkeys
        is PasskeyUiState.Deleting -> passkeys
        is PasskeyUiState.ReauthenticationRequired -> passkeys
        is PasskeyUiState.ExternalAuthenticationInProgress -> passkeys
        is PasskeyUiState.Error -> previousState.passkeys
    }

val PasskeyUiState.isBusy: Boolean
    get() = this is PasskeyUiState.Loading ||
        this is PasskeyUiState.Creating ||
        this is PasskeyUiState.Renaming ||
        this is PasskeyUiState.Deleting ||
        this is PasskeyUiState.ExternalAuthenticationInProgress

sealed interface PasskeyUiEvent {
    data class LaunchCredentialCreation(
        val operationId: String,
        val requestJson: String,
        val name: String
    ) : PasskeyUiEvent

    data class LaunchCredentialAuthentication(
        val operationId: String,
        val requestJson: String,
        val pendingAction: PasskeyPendingAction
    ) : PasskeyUiEvent

    data class LaunchTelegramReauthentication(
        val pendingAction: PasskeyPendingAction
    ) : PasskeyUiEvent

    data class ShowSnackbar(
        val error: PasskeyError
    ) : PasskeyUiEvent
}

private const val KEY_PENDING_ACTION_TYPE = "passkey_pending_action_type"
private const val KEY_PENDING_ACTION_ID = "passkey_pending_action_id"
private const val KEY_PENDING_ACTION_NAME = "passkey_pending_action_name"
private const val KEY_PENDING_OPERATION_ID = "passkey_pending_operation_id"

private const val ACTION_TYPE_CREATE = "CREATE"
private const val ACTION_TYPE_RENAME = "RENAME"
private const val ACTION_TYPE_DELETE = "DELETE"

class PasskeyViewModel(
    private val passkeyRepository: PasskeyRepository,
    private val authenticationRepository: AuthenticationRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<PasskeyUiState>(PasskeyUiState.Loading)
    val uiState: StateFlow<PasskeyUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PasskeyUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PasskeyUiEvent> = _events.asSharedFlow()

    private val mutex = Mutex()
    private var activeJob: Job? = null

    val isPasskeySupported: Boolean
        get() = passkeyRepository.isSupported

    init {
        val restoredPending = restorePendingAction()
        if (restoredPending != null) {
            _uiState.value = PasskeyUiState.ExternalAuthenticationInProgress(
                pendingAction = restoredPending
            )
        }
        viewModelScope.launch {
            authenticationRepository.state.collect { authState ->
                val token = authState.sessionOrNull?.accessToken
                if (token != null && _uiState.value is PasskeyUiState.Loading) {
                    loadPasskeysInternal(token)
                }
            }
        }
    }

    fun loadPasskeys() {
        if (_uiState.value.isBusy && _uiState.value !is PasskeyUiState.Loading) return
        val token = currentAccessToken() ?: return
        viewModelScope.launch {
            loadPasskeysInternal(token)
        }
    }

    private suspend fun loadPasskeysInternal(token: String) {
        _uiState.value = PasskeyUiState.Loading
        val result = passkeyRepository.getPasskeys(token)
        result.onSuccess { passkeys ->
            _uiState.value = PasskeyUiState.Ready(passkeys)
        }.onFailure { error ->
            val passkeyError = (error as? PasskeyError) ?: PasskeyError.Unknown(originalCause = error)
            _uiState.value = PasskeyUiState.Error(
                previousState = PasskeyUiState.Ready(emptyList()),
                error = passkeyError
            )
        }
    }

    fun createPasskey(suggestedName: String) {
        if (_uiState.value.isBusy) return
        val token = currentAccessToken() ?: return
        val currentPasskeys = _uiState.value.passkeys
        val cleanName = suggestedName.trim().take(80).ifBlank { "Passkey" }
        _uiState.value = PasskeyUiState.Creating(currentPasskeys)

        activeJob = viewModelScope.launch {
            mutex.withLock {
                val action = PasskeyPendingAction.Create(cleanName)
                savePendingAction(action)

                val result = passkeyRepository.beginRegistration(token)
                result.onSuccess { options ->
                    savedStateHandle[KEY_PENDING_OPERATION_ID] = options.operationId
                    _uiState.value = PasskeyUiState.ExternalAuthenticationInProgress(action, currentPasskeys)
                    _events.emit(
                        PasskeyUiEvent.LaunchCredentialCreation(
                            operationId = options.operationId,
                            requestJson = options.requestJson,
                            name = cleanName
                        )
                    )
                }.onFailure { error ->
                    handleOperationFailure(error, action, currentPasskeys)
                }
            }
        }
    }

    fun onCredentialCreated(operationId: String, credentialJson: String, name: String) {
        val token = currentAccessToken() ?: return
        val currentPasskeys = _uiState.value.passkeys
        viewModelScope.launch {
            mutex.withLock {
                _uiState.value = PasskeyUiState.Creating(currentPasskeys)
                val result = passkeyRepository.finishRegistration(token, operationId, credentialJson, name)
                result.onSuccess { updatedPasskeys ->
                    clearSavedPendingAction()
                    _uiState.value = PasskeyUiState.Ready(updatedPasskeys)
                }.onFailure { error ->
                    val passkeyError = (error as? PasskeyError) ?: PasskeyError.Unknown(originalCause = error)
                    clearSavedPendingAction()
                    _uiState.value = PasskeyUiState.Error(
                        previousState = PasskeyUiState.Ready(currentPasskeys),
                        error = passkeyError
                    )
                    _events.emit(PasskeyUiEvent.ShowSnackbar(passkeyError))
                }
            }
        }
    }

    fun onCredentialError(error: PasskeyError) {
        clearSavedPendingAction()
        val currentPasskeys = _uiState.value.passkeys
        if (error is PasskeyError.Cancelled) {
            _uiState.value = PasskeyUiState.Ready(currentPasskeys)
        } else {
            _uiState.value = PasskeyUiState.Error(
                previousState = PasskeyUiState.Ready(currentPasskeys),
                error = error
            )
            viewModelScope.launch {
                _events.emit(PasskeyUiEvent.ShowSnackbar(error))
            }
        }
    }

    fun completePasskeyReauthentication(
        operationId: String,
        credentialJson: String,
        pendingAction: PasskeyPendingAction
    ) {
        val token = currentAccessToken() ?: return
        val currentPasskeys = _uiState.value.passkeys
        viewModelScope.launch {
            mutex.withLock {
                val result = passkeyRepository.finishPasskeyReauthentication(token, operationId, credentialJson)
                result.onSuccess {
                    clearSavedPendingAction()
                    executePendingAction(pendingAction)
                }.onFailure { error ->
                    val passkeyError = (error as? PasskeyError) ?: PasskeyError.SessionExpired(error)
                    clearSavedPendingAction()
                    _uiState.value = PasskeyUiState.Error(
                        previousState = PasskeyUiState.Ready(currentPasskeys),
                        error = passkeyError
                    )
                    _events.emit(PasskeyUiEvent.ShowSnackbar(passkeyError))
                }
            }
        }
    }

    fun onTelegramReauthenticationStarted(pendingAction: PasskeyPendingAction) {
        savePendingAction(pendingAction)
        _uiState.value = PasskeyUiState.ExternalAuthenticationInProgress(
            pendingAction = pendingAction,
            passkeys = _uiState.value.passkeys
        )
    }

    fun resumeAfterExternalAuth() {
        val token = currentAccessToken() ?: return
        val pendingAction = restorePendingAction()
        viewModelScope.launch {
            mutex.withLock {
                val result = passkeyRepository.getPasskeys(token)
                result.onSuccess { passkeys ->
                    _uiState.value = PasskeyUiState.Ready(passkeys)
                    if (pendingAction != null) {
                        clearSavedPendingAction()
                        executePendingAction(pendingAction)
                    }
                }.onFailure { error ->
                    val passkeyError = (error as? PasskeyError) ?: PasskeyError.Unknown(originalCause = error)
                    _uiState.value = PasskeyUiState.Error(
                        previousState = PasskeyUiState.Ready(_uiState.value.passkeys),
                        error = passkeyError
                    )
                }
            }
        }
    }

    fun renamePasskey(id: String, newName: String) {
        if (_uiState.value.isBusy) return
        val token = currentAccessToken() ?: return
        val cleanName = newName.trim().take(80)
        if (cleanName.isBlank()) return
        val currentPasskeys = _uiState.value.passkeys
        _uiState.value = PasskeyUiState.Renaming(id, currentPasskeys)

        activeJob = viewModelScope.launch {
            mutex.withLock {
                val action = PasskeyPendingAction.Rename(id, cleanName)
                val result = passkeyRepository.renamePasskey(token, id, cleanName)
                result.onSuccess { updatedPasskeys ->
                    clearSavedPendingAction()
                    _uiState.value = PasskeyUiState.Ready(updatedPasskeys)
                }.onFailure { error ->
                    handleOperationFailure(error, action, currentPasskeys)
                }
            }
        }
    }

    fun deletePasskey(id: String) {
        if (_uiState.value.isBusy) return
        val token = currentAccessToken() ?: return
        val currentPasskeys = _uiState.value.passkeys
        _uiState.value = PasskeyUiState.Deleting(id, currentPasskeys)

        activeJob = viewModelScope.launch {
            mutex.withLock {
                val action = PasskeyPendingAction.Delete(id)
                val result = passkeyRepository.deletePasskey(token, id)
                result.onSuccess { deleted ->
                    clearSavedPendingAction()
                    passkeyRepository.signalUnknownCredential(deleted.rpId, deleted.credentialId)
                    val remaining = currentPasskeys.filterNot { it.id == id }
                    _uiState.value = PasskeyUiState.Ready(remaining)
                }.onFailure { error ->
                    handleOperationFailure(error, action, currentPasskeys)
                }
            }
        }
    }

    private suspend fun handleOperationFailure(
        error: Throwable,
        action: PasskeyPendingAction,
        passkeys: List<PasskeyInfo>
    ) {
        val passkeyError = (error as? PasskeyError) ?: PasskeyError.Unknown(originalCause = error)
        if (passkeyError is PasskeyError.SessionExpired) {
            savePendingAction(action)
            _uiState.value = PasskeyUiState.ReauthenticationRequired(action, passkeys)
            val token = currentAccessToken()
            if (token != null) {
                val reauthOptionsResult = passkeyRepository.beginPasskeyReauthentication(token)
                if (reauthOptionsResult.isSuccess) {
                    val options = reauthOptionsResult.getOrThrow()
                    savedStateHandle[KEY_PENDING_OPERATION_ID] = options.operationId
                    _uiState.value = PasskeyUiState.ExternalAuthenticationInProgress(action, passkeys)
                    _events.emit(
                        PasskeyUiEvent.LaunchCredentialAuthentication(
                            operationId = options.operationId,
                            requestJson = options.requestJson,
                            pendingAction = action
                        )
                    )
                    return
                }
            }
            _uiState.value = PasskeyUiState.ExternalAuthenticationInProgress(action, passkeys)
            _events.emit(PasskeyUiEvent.LaunchTelegramReauthentication(action))
        } else {
            clearSavedPendingAction()
            _uiState.value = PasskeyUiState.Error(
                previousState = PasskeyUiState.Ready(passkeys),
                error = passkeyError
            )
            _events.emit(PasskeyUiEvent.ShowSnackbar(passkeyError))
        }
    }

    private suspend fun executePendingAction(action: PasskeyPendingAction) {
        when (action) {
            is PasskeyPendingAction.Create -> {
                val token = currentAccessToken() ?: return
                val currentPasskeys = _uiState.value.passkeys
                _uiState.value = PasskeyUiState.Creating(currentPasskeys)
                savePendingAction(action)
                val result = passkeyRepository.beginRegistration(token)
                result.onSuccess { options ->
                    savedStateHandle[KEY_PENDING_OPERATION_ID] = options.operationId
                    _uiState.value = PasskeyUiState.ExternalAuthenticationInProgress(action, currentPasskeys)
                    _events.emit(
                        PasskeyUiEvent.LaunchCredentialCreation(
                            operationId = options.operationId,
                            requestJson = options.requestJson,
                            name = action.suggestedName
                        )
                    )
                }.onFailure { error ->
                    handleOperationFailure(error, action, currentPasskeys)
                }
            }
            is PasskeyPendingAction.Rename -> {
                val token = currentAccessToken() ?: return
                val currentPasskeys = _uiState.value.passkeys
                _uiState.value = PasskeyUiState.Renaming(action.passkeyId, currentPasskeys)
                val result = passkeyRepository.renamePasskey(token, action.passkeyId, action.newName)
                result.onSuccess { updatedPasskeys ->
                    clearSavedPendingAction()
                    _uiState.value = PasskeyUiState.Ready(updatedPasskeys)
                }.onFailure { error ->
                    handleOperationFailure(error, action, currentPasskeys)
                }
            }
            is PasskeyPendingAction.Delete -> {
                val token = currentAccessToken() ?: return
                val currentPasskeys = _uiState.value.passkeys
                _uiState.value = PasskeyUiState.Deleting(action.passkeyId, currentPasskeys)
                val result = passkeyRepository.deletePasskey(token, action.passkeyId)
                result.onSuccess { deleted ->
                    clearSavedPendingAction()
                    passkeyRepository.signalUnknownCredential(deleted.rpId, deleted.credentialId)
                    _uiState.value = PasskeyUiState.Ready(currentPasskeys.filterNot { it.id == action.passkeyId })
                }.onFailure { error ->
                    handleOperationFailure(error, action, currentPasskeys)
                }
            }
        }
    }

    fun dismissError() {
        val current = _uiState.value
        if (current is PasskeyUiState.Error) {
            _uiState.value = current.previousState
        }
    }

    fun cancelPendingAction() {
        clearSavedPendingAction()
        _uiState.value = PasskeyUiState.Ready(_uiState.value.passkeys)
    }

    private fun savePendingAction(action: PasskeyPendingAction) {
        when (action) {
            is PasskeyPendingAction.Create -> {
                savedStateHandle[KEY_PENDING_ACTION_TYPE] = ACTION_TYPE_CREATE
                savedStateHandle[KEY_PENDING_ACTION_NAME] = action.suggestedName
                savedStateHandle.remove<String>(KEY_PENDING_ACTION_ID)
            }
            is PasskeyPendingAction.Rename -> {
                savedStateHandle[KEY_PENDING_ACTION_TYPE] = ACTION_TYPE_RENAME
                savedStateHandle[KEY_PENDING_ACTION_ID] = action.passkeyId
                savedStateHandle[KEY_PENDING_ACTION_NAME] = action.newName
            }
            is PasskeyPendingAction.Delete -> {
                savedStateHandle[KEY_PENDING_ACTION_TYPE] = ACTION_TYPE_DELETE
                savedStateHandle[KEY_PENDING_ACTION_ID] = action.passkeyId
                savedStateHandle.remove<String>(KEY_PENDING_ACTION_NAME)
            }
        }
    }

    private fun restorePendingAction(): PasskeyPendingAction? {
        val type = savedStateHandle.get<String>(KEY_PENDING_ACTION_TYPE) ?: return null
        return when (type) {
            ACTION_TYPE_CREATE -> {
                val name = savedStateHandle.get<String>(KEY_PENDING_ACTION_NAME).orEmpty()
                PasskeyPendingAction.Create(name)
            }
            ACTION_TYPE_RENAME -> {
                val id = savedStateHandle.get<String>(KEY_PENDING_ACTION_ID).orEmpty()
                val name = savedStateHandle.get<String>(KEY_PENDING_ACTION_NAME).orEmpty()
                PasskeyPendingAction.Rename(id, name)
            }
            ACTION_TYPE_DELETE -> {
                val id = savedStateHandle.get<String>(KEY_PENDING_ACTION_ID).orEmpty()
                PasskeyPendingAction.Delete(id)
            }
            else -> null
        }
    }

    private fun clearSavedPendingAction() {
        savedStateHandle.remove<String>(KEY_PENDING_ACTION_TYPE)
        savedStateHandle.remove<String>(KEY_PENDING_ACTION_ID)
        savedStateHandle.remove<String>(KEY_PENDING_ACTION_NAME)
        savedStateHandle.remove<String>(KEY_PENDING_OPERATION_ID)
    }

    private fun currentAccessToken(): String? =
        authenticationRepository.state.value.sessionOrNull?.accessToken
}
