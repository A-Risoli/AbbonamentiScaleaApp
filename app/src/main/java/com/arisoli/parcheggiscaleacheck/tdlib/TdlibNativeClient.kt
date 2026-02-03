package com.arisoli.parcheggiscaleacheck.tdlib

import android.content.Context
import android.util.Log
import com.arisoli.parcheggiscaleacheck.AuthManager
import com.arisoli.parcheggiscaleacheck.AuthState
import com.arisoli.parcheggiscaleacheck.Config
import com.arisoli.parcheggiscaleacheck.TdlibHandler
import com.arisoli.parcheggiscaleacheck.TdlibUpdate
import com.arisoli.parcheggiscaleacheck.TelegramUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File

/**
 * Client TDLib nativo
 * Questa classe gestisce l'integrazione con TDLib reale
 */
class TdlibNativeClient(
    private val context: Context,
    private val handler: TdlibHandler
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var client: Client? = null
    private var isInitialized = false
    private var isAuthReady = false
    private var botChatId: Long? = null
    
    companion object {
        private const val TAG = "TdlibNativeClient"
        init {
            try {
                // La libreria viene caricata automaticamente da Client.java
                Log.d(TAG, "TDLib nativo disponibile")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Libreria TDLib nativa non trovata", e)
            }
        }
    }
    
    /**
     * Inizializza TDLib reale
     */
    fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "Client già inizializzato")
            return
        }
        
        scope.launch {
            try {
                // Verifica configurazione
                if (Config.API_ID == 0 || Config.API_HASH.isEmpty()) {
                    handler.onUpdate(TdlibUpdate.Error(
                        "API_ID e API_HASH non configurati!\n" +
                        "Vedi Config.kt e https://my.telegram.org"
                    ))
                    return@launch
                }
                
                // Prepara directory per TDLib
                val databaseDir = File(context.filesDir, Config.TDLIB_DATABASE_DIR)
                if (!databaseDir.exists()) {
                    databaseDir.mkdirs()
                }
                
                // Crea client TDLib
                client = Client.create(object : Client.ResultHandler {
                    override fun onResult(obj: TdApi.Object) {
                        handleUpdate(obj)
                    }
                }, null, null)
                
                // Imposta parametri TDLib
                val clientParams = TdApi.SetTdlibParameters().apply {
                    useTestDc = false
                    databaseDirectory = databaseDir.absolutePath
                    filesDirectory = "" // Usa databaseDirectory se vuoto
                    databaseEncryptionKey = null
                    useFileDatabase = true
                    useChatInfoDatabase = true
                    useMessageDatabase = true
                    useSecretChats = false
                    apiId = Config.API_ID
                    apiHash = Config.API_HASH
                    systemLanguageCode = "it"
                    deviceModel = android.os.Build.MODEL
                    systemVersion = android.os.Build.VERSION.RELEASE
                    applicationVersion = "1.0"
                }
                
                client?.send(clientParams, object : Client.ResultHandler {
                    override fun onResult(obj: TdApi.Object) {
                        handleUpdate(obj)
                    }
                })
                
                isInitialized = true
                Log.d(TAG, "TDLib client inizializzato")
            } catch (e: Exception) {
                Log.e(TAG, "Errore inizializzazione TDLib", e)
                handler.onUpdate(TdlibUpdate.Error("Errore inizializzazione: ${e.message}"))
            }
        }
    }
    
    /**
     * Gestisce gli update da TDLib
     * IMPORTANTE: Gli update vengono chiamati da thread TDLib, non dal thread principale
     */
    private fun handleUpdate(obj: TdApi.Object) {
        // Esegui sul thread principale per aggiornare l'UI
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            handleUpdateOnMainThread(obj)
        }
    }
    
    private fun handleUpdateOnMainThread(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> {
                when (obj.authorizationState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        // Già gestito in initialize()
                        Log.d(TAG, "Attesa parametri TDLib")
                    }
                    is TdApi.AuthorizationStateWaitPhoneNumber -> {
                        Log.d(TAG, "Attesa numero telefono")
                        handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateWaitPhoneNumber"))
                    }
                    is TdApi.AuthorizationStateWaitCode -> {
                        Log.d(TAG, "Attesa codice verifica")
                        handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateWaitCode"))
                    }
                    is TdApi.AuthorizationStateWaitPassword -> {
                        Log.d(TAG, "Richiesta password 2FA")
                        handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateWaitPassword"))
                    }
                    is TdApi.AuthorizationStateWaitRegistration -> {
                        Log.w(TAG, "Richiesta registrazione - non gestita")
                        handler.onUpdate(TdlibUpdate.Error("Account non registrato. Registrati prima su Telegram."))
                    }
                    is TdApi.AuthorizationStateWaitEmailAddress -> {
                        Log.w(TAG, "Richiesta email - non gestita")
                        handler.onUpdate(TdlibUpdate.Error("Richiesta email per verifica. Non supportato al momento."))
                    }
                    is TdApi.AuthorizationStateWaitEmailCode -> {
                        Log.w(TAG, "Richiesta codice email - non gestita")
                        handler.onUpdate(TdlibUpdate.Error("Richiesta codice email. Non supportato al momento."))
                    }
                    is TdApi.AuthorizationStateReady -> {
                        Log.d(TAG, "Autenticazione completata, ottengo informazioni utente")
                        isAuthReady = true
                        // Notifica subito che l'autenticazione è pronta
                        handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateReady"))
                        
                        // Ottieni informazioni utente in background
                        scope.launch {
                            try {
                                client?.send(TdApi.GetMe(), object : Client.ResultHandler {
                                    override fun onResult(obj: TdApi.Object) {
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            when (obj) {
                                                is TdApi.User -> {
                                                    val user = TelegramUser(
                                                        id = obj.id,
                                                        firstName = obj.firstName,
                                                        lastName = obj.lastName,
                                                        username = obj.usernames?.editableUsername,
                                                        phoneNumber = obj.phoneNumber
                                                    )
                                                    val authManager = AuthManager(context)
                                                    authManager.saveUser(user)
                                                    Log.d(TAG, "Utente autenticato: ${obj.firstName}")
                                                }
                                                is TdApi.Error -> {
                                                    Log.e(TAG, "Errore ottenendo informazioni utente: ${obj.message}")
                                                    // Non bloccare l'autenticazione per questo errore
                                                }
                                                else -> {
                                                    Log.w(TAG, "Risposta inattesa da GetMe: ${obj.javaClass.simpleName}")
                                                }
                                            }
                                        }
                                    }
                                })
                            } catch (e: Exception) {
                                Log.e(TAG, "Errore chiamando GetMe", e)
                            }
                        }
                    }
                    is TdApi.AuthorizationStateLoggingOut -> {
                        Log.d(TAG, "Logout in corso")
                        handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateLoggingOut"))
                    }
                    is TdApi.AuthorizationStateClosed -> {
                        Log.d(TAG, "Autenticazione chiusa")
                        handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateClosed"))
                    }
                    else -> {
                        Log.w(TAG, "Stato autenticazione non gestito: ${obj.authorizationState.javaClass.simpleName}")
                    }
                }
            }
            is TdApi.UpdateNewMessage -> {
                val message = obj.message
                if (message.content is TdApi.MessageText) {
                    val text = (message.content as TdApi.MessageText).text.text
                    // Verifica se il messaggio è da un bot
                    // Confronta con l'ID utente salvato
                    val authManager = AuthManager(context)
                    val currentUserId = authManager.getUser()?.id ?: 0L
                    
                    val isFromBot = when (message.senderId) {
                        is TdApi.MessageSenderUser -> {
                            val senderUserId = (message.senderId as TdApi.MessageSenderUser).userId
                            // Se senderUserId è diverso dall'utente corrente, è dal bot
                            senderUserId != currentUserId
                        }
                        is TdApi.MessageSenderChat -> {
                            // Messaggio da una chat (gruppo/canale) - non è da bot
                            false
                        }
                        else -> false
                    }
                    Log.d(TAG, "Messaggio ricevuto: isFromBot=$isFromBot, senderId=${(message.senderId as? TdApi.MessageSenderUser)?.userId}, currentUserId=$currentUserId, text=$text")
                    handler.onUpdate(TdlibUpdate.NewMessage(text, message.date.toLong(), isFromBot))
                }
            }
            is TdApi.Error -> {
                Log.e(TAG, "Errore TDLib: ${obj.message}")
                handler.onUpdate(TdlibUpdate.Error(obj.message))
            }
            else -> {
                // Altri update non gestiti
                Log.d(TAG, "Update ricevuto: ${obj.javaClass.simpleName}")
            }
        }
    }
    
    /**
     * Invia numero telefono per autenticazione
     */
    fun setAuthenticationPhoneNumber(phoneNumber: String) {
        scope.launch {
            try {
                client?.send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null), object : Client.ResultHandler {
                    override fun onResult(obj: TdApi.Object) {
                        when (obj) {
                            is TdApi.Error -> {
                                handler.onUpdate(TdlibUpdate.Error(obj.message))
                            }
                            is TdApi.Ok -> {
                                // Il codice verrà ricevuto tramite UpdateAuthorizationState
                                Log.d(TAG, "Numero telefono inviato, in attesa del codice")
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Errore invio numero", e)
                handler.onUpdate(TdlibUpdate.Error("Errore: ${e.message}"))
            }
        }
    }
    
    /**
     * Verifica codice autenticazione
     */
    fun checkAuthenticationCode(code: String) {
        scope.launch {
            try {
                Log.d(TAG, "Verifica codice: $code")
                client?.send(TdApi.CheckAuthenticationCode(code), object : Client.ResultHandler {
                    override fun onResult(obj: TdApi.Object) {
                        when (obj) {
                            is TdApi.Error -> {
                                Log.e(TAG, "Errore verifica codice: ${obj.code} - ${obj.message}")
                                handler.onUpdate(TdlibUpdate.Error("Errore: ${obj.message}"))
                            }
                            is TdApi.Ok -> {
                                // L'autenticazione verrà completata tramite UpdateAuthorizationState
                                Log.d(TAG, "Codice verificato correttamente, in attesa di AuthorizationStateReady...")
                            }
                            else -> {
                                Log.w(TAG, "Risposta inattesa da CheckAuthenticationCode: ${obj.javaClass.simpleName}")
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Errore verifica codice", e)
                handler.onUpdate(TdlibUpdate.Error("Errore: ${e.message}"))
            }
        }
    }
    
    /**
     * Verifica password 2FA
     */
    fun checkAuthenticationPassword(password: String) {
        scope.launch {
            try {
                Log.d(TAG, "Verifica password 2FA: ***")
                client?.send(TdApi.CheckAuthenticationPassword(password), object : Client.ResultHandler {
                    override fun onResult(obj: TdApi.Object) {
                        when (obj) {
                            is TdApi.Error -> {
                                Log.e(TAG, "Errore verifica password: ${obj.code} - ${obj.message}")
                                handler.onUpdate(TdlibUpdate.Error("Password errata: ${obj.message}"))
                            }
                            is TdApi.Ok -> {
                                // L'autenticazione verrà completata tramite UpdateAuthorizationState
                                Log.d(TAG, "Password verificata correttamente, in attesa di AuthorizationStateReady...")
                            }
                            else -> {
                                Log.w(TAG, "Risposta inattesa da CheckAuthenticationPassword: ${obj.javaClass.simpleName}")
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Errore verifica password", e)
                handler.onUpdate(TdlibUpdate.Error("Errore: ${e.message}"))
            }
        }
    }
    
    /**
     * Invia messaggio
     */
    fun sendMessage(text: String, onSuccess: ((Long) -> Unit)?, onError: ((String) -> Unit)?) {
        scope.launch {
            try {
                Log.d(TAG, "sendMessage chiamato: text='$text', botChatId=$botChatId, client=${client != null}, isAuthReady=$isAuthReady")
                val authError = ensureAuthReady()
                if (authError != null) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onError?.invoke(authError)
                    }
                    return@launch
                }

                // Usa chatId salvato se disponibile, altrimenti cerca il bot
                if (botChatId != null) {
                    Log.d(TAG, "Uso botChatId salvato: $botChatId")
                    sendMessageToChat(botChatId!!, text, onSuccess, onError)
                } else {
                    // Cerca bot per username
                    Log.d(TAG, "Cerco bot per username: ${Config.BOT_USERNAME}")
                    client?.send(TdApi.SearchPublicChat(Config.BOT_USERNAME), object : Client.ResultHandler {
                        override fun onResult(obj: TdApi.Object) {
                            Log.d(TAG, "SearchPublicChat risultato: ${obj.javaClass.simpleName}")
                            when (obj) {
                                is TdApi.Chat -> {
                                    Log.d(TAG, "Bot trovato! chatId=${obj.id}, title=${obj.title}")
                                    botChatId = obj.id
                                    sendMessageToChat(obj.id, text, onSuccess, onError)
                                }
                                is TdApi.Error -> {
                                    Log.e(TAG, "Errore SearchPublicChat: ${obj.code} - ${obj.message}")
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        onError?.invoke("Bot non trovato: ${obj.message}")
                                    }
                                }
                                else -> {
                                    Log.w(TAG, "Risposta inattesa da SearchPublicChat: ${obj.javaClass.simpleName}")
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        onError?.invoke("Risposta inattesa da SearchPublicChat")
                                    }
                                }
                            }
                        }
                    }) ?: run {
                        Log.e(TAG, "Client è null!")
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onError?.invoke("Client TDLib non inizializzato")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore invio messaggio", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError?.invoke("Errore: ${e.message}")
                }
            }
        }
    }

    private suspend fun ensureAuthReady(): String? {
        if (isAuthReady) {
            Log.d(TAG, "ensureAuthReady: già pronto (flag)")
            return null
        }

        val currentState = handler.authenticationState.value
        Log.d(TAG, "ensureAuthReady: stato handler = ${currentState?.javaClass?.simpleName}")
        if (currentState is AuthState.Ready) {
            isAuthReady = true
            return null
        }
        
        // Se lo stato dell'handler indica che serve login, fallisci subito
        if (currentState is AuthState.WaitingPhoneNumber) {
            return "Autenticazione non completata. Effettua il login prima."
        }
        if (currentState is AuthState.WaitingCode) {
            return "Autenticazione non completata. Inserisci il codice di verifica."
        }
        if (currentState is AuthState.WaitingPassword) {
            return "Autenticazione non completata. Inserisci la password 2FA."
        }

        val currentClient = client ?: return "Client TDLib non inizializzato"

        // Query TDLib direttamente solo se lo stato non è chiaro
        Log.d(TAG, "ensureAuthReady: query TDLib GetAuthorizationState")
        val authObj = withTimeoutOrNull(3000) { getAuthorizationState(currentClient) }
        Log.d(TAG, "ensureAuthReady: risposta = ${authObj?.javaClass?.simpleName}")

        return when (authObj) {
            is TdApi.AuthorizationStateReady -> {
                isAuthReady = true
                handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateReady"))
                null
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateWaitPhoneNumber"))
                "Autenticazione non completata. Effettua il login prima."
            }
            is TdApi.AuthorizationStateWaitCode -> {
                handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateWaitCode"))
                "Autenticazione non completata. Inserisci il codice di verifica."
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateWaitPassword"))
                "Autenticazione non completata. Inserisci la password 2FA."
            }
            is TdApi.Error -> {
                "Errore autenticazione: ${authObj.message}"
            }
            null -> {
                // Timeout - usa lo stato dell'handler come fallback
                "Autenticazione non pronta. Riprova tra qualche secondo."
            }
            else -> {
                Log.w(TAG, "ensureAuthReady: stato sconosciuto ${authObj.javaClass.simpleName}")
                "Autenticazione non completata. Effettua il login prima."
            }
        }
    }

    private suspend fun getAuthorizationState(currentClient: Client): TdApi.Object? {
        val deferred = CompletableDeferred<TdApi.Object?>()
        try {
            currentClient.send(TdApi.GetAuthorizationState(), object : Client.ResultHandler {
                override fun onResult(obj: TdApi.Object) {
                    Log.d(TAG, "getAuthorizationState callback: ${obj.javaClass.simpleName}")
                    deferred.complete(obj)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "getAuthorizationState errore", e)
            deferred.complete(null)
        }
        return deferred.await()
    }
    
    private fun sendMessageToChat(chatId: Long, text: String, onSuccess: ((Long) -> Unit)?, onError: ((String) -> Unit)?) {
        Log.d(TAG, "sendMessageToChat: chatId=$chatId, text='$text'")
        val inputMessage = TdApi.InputMessageText(
            TdApi.FormattedText(text, emptyArray()),
            null, // linkPreviewOptions
            false // clearDraft
        )
        client?.send(
            TdApi.SendMessage(
                chatId,
                null, // topicId
                null, // replyTo
                null, // options
                null, // replyMarkup
                inputMessage
            ),
            object : Client.ResultHandler {
                override fun onResult(result: TdApi.Object) {
                    Log.d(TAG, "SendMessage risultato: ${result.javaClass.simpleName}")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        when (result) {
                            is TdApi.Message -> {
                                Log.d(TAG, "Messaggio inviato! id=${result.id}")
                                onSuccess?.invoke(result.id)
                            }
                            is TdApi.Error -> {
                                Log.e(TAG, "Errore SendMessage: ${result.code} - ${result.message}")
                                onError?.invoke(result.message)
                            }
                            else -> {
                                Log.w(TAG, "Risposta inattesa da SendMessage")
                                onError?.invoke("Risposta inattesa da SendMessage")
                            }
                        }
                    }
                }
            }
        ) ?: run {
            Log.e(TAG, "Client è null in sendMessageToChat!")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onError?.invoke("Client TDLib non inizializzato")
            }
        }
    }
    
    fun disconnect() {
        isInitialized = false
        isAuthReady = false
        
        scope.launch {
            try {
                // Close the client first
                client?.send(TdApi.Close(), object : Client.ResultHandler {
                    override fun onResult(obj: TdApi.Object) {
                        // Handled by UpdateAuthorizationState
                    }
                })
                
                // Wait a bit for close to complete
                kotlinx.coroutines.delay(500)
                
                // Delete TDLib database to clear session
                val databaseDir = File(context.filesDir, Config.TDLIB_DATABASE_DIR)
                if (databaseDir.exists()) {
                    databaseDir.deleteRecursively()
                    Log.d(TAG, "Database TDLib cancellato")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore logout", e)
            } finally {
                client = null
                botChatId = null
            }
        }
    }
    
    fun isReady(): Boolean = isInitialized && isAuthReady && client != null
}
