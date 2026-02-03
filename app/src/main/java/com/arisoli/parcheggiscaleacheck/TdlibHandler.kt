package com.arisoli.parcheggiscaleacheck

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handler per gestire update e callback TDLib
 */
class TdlibHandler {
    // Initialize with Ready state if authenticated, otherwise WaitingPhoneNumber
    private var _initialState: AuthState = AuthState.WaitingPhoneNumber
    
    private val _authenticationState = MutableStateFlow<AuthState>(_initialState)
    val authenticationState: StateFlow<AuthState> = _authenticationState.asStateFlow()
    
    private val _receivedMessages = MutableStateFlow<List<BotMessage>>(emptyList())
    val receivedMessages: StateFlow<List<BotMessage>> = _receivedMessages.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Callback for new messages - can be set by CameraActivity
    var onNewMessageCallback: ((BotMessage) -> Unit)? = null

    // When true, HomeFragment should not display incoming messages
    var suppressHomeMessages: Boolean = false
    
    /**
     * Initialize authentication state from persisted data
     */
    fun initializeAuthenticationState(isAuthenticated: Boolean) {
        _initialState = if (isAuthenticated) AuthState.Ready else AuthState.WaitingPhoneNumber
        _authenticationState.value = _initialState
        Log.d("TdlibHandler", "Inizializzato stato autenticazione: ${_initialState.javaClass.simpleName}")
    }
    
    fun onUpdate(update: TdlibUpdate) {
        when (update) {
            is TdlibUpdate.AuthorizationState -> {
                // Pulisci errori quando cambia lo stato
                _errorMessage.value = null
                _authenticationState.value = when (update.state) {
                    "authorizationStateWaitPhoneNumber" -> AuthState.WaitingPhoneNumber
                    "authorizationStateWaitCode" -> AuthState.WaitingCode
                    "authorizationStateWaitPassword" -> AuthState.WaitingPassword
                    "authorizationStateReady" -> AuthState.Ready
                    "authorizationStateLoggingOut" -> AuthState.LoggingOut
                    "authorizationStateClosed" -> AuthState.Closed
                    else -> AuthState.WaitingPhoneNumber
                }
            }
            is TdlibUpdate.NewMessage -> {
                val message = BotMessage(
                    text = update.text,
                    timestamp = update.date,
                    isFromBot = update.isFromBot
                )
                _receivedMessages.value = _receivedMessages.value + message
                Log.d("TdlibHandler", "Nuovo messaggio ricevuto: ${update.text}")
                // Notify callback if set
                Log.d("TdlibHandler", "onNewMessageCallback è null? ${onNewMessageCallback == null}")
                onNewMessageCallback?.invoke(message)
                Log.d("TdlibHandler", "Callback invocato (se non null)")
            }
            is TdlibUpdate.Error -> {
                Log.e("TdlibHandler", "Errore TDLib: ${update.message}")
                _errorMessage.value = update.message
            }
        }
    }
    
    fun clearMessages() {
        _receivedMessages.value = emptyList()
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}

sealed class AuthState {
    object WaitingPhoneNumber : AuthState()
    object WaitingCode : AuthState()
    object WaitingPassword : AuthState()
    object Ready : AuthState()
    object LoggingOut : AuthState()
    object Closed : AuthState()
}

sealed class TdlibUpdate {
    data class AuthorizationState(val state: String) : TdlibUpdate()
    data class NewMessage(val text: String, val date: Long, val isFromBot: Boolean) : TdlibUpdate()
    data class Error(val message: String) : TdlibUpdate()
}
