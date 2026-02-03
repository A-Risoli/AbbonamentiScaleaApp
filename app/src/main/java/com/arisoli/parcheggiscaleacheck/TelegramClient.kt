package com.arisoli.parcheggiscaleacheck

import android.content.Context
import android.util.Log
import com.arisoli.parcheggiscaleacheck.tdlib.TdlibNativeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wrapper per TDLib client
 * Usa TdlibNativeClient se TDLib è integrato, altrimenti usa implementazione simulata
 */
class TelegramClient(
    private val context: Context,
    private val handler: TdlibHandler
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isInitialized = false
    private var isAuthenticated = false
    private var botChatId: Long? = null
    
    // Prova a usare TDLib nativo se disponibile
    private val nativeClient: TdlibNativeClient? = try {
        TdlibNativeClient(context, handler)
    } catch (e: Exception) {
        Log.w("TelegramClient", "TDLib nativo non disponibile, usa implementazione simulata", e)
        null
    }
    
    // Flag per usare TDLib nativo
    private val useNativeTdlib = nativeClient != null && isTdlibAvailable()
    
    companion object {
        private const val TAG = "TelegramClient"
        
        /**
         * Verifica se TDLib è disponibile
         */
        private fun isTdlibAvailable(): Boolean {
            return try {
                // Prova a caricare la classe TDLib
                Class.forName("org.drinkless.tdlib.Client")
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Inizializza il client TDLib
     */
    fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "Client già inizializzato")
            return
        }
        
        // Restore authentication state from persistent storage
        val authManager = AuthManager(context)
        if (authManager.isAuthenticated()) {
            isAuthenticated = true
        }
        
        // Initialize TdlibHandler with persisted authentication state
        handler.initializeAuthenticationState(isAuthenticated)
        
        // Se TDLib nativo è disponibile, usalo
        if (useNativeTdlib && nativeClient != null) {
            Log.d(TAG, "Usa TDLib nativo")
            nativeClient.initialize()
            isInitialized = true
            return
        }
        
        // Altrimenti usa implementazione simulata
        scope.launch {
            try {
                // Verifica che API_ID e API_HASH siano configurati
                if (Config.API_ID == 0 || Config.API_HASH.isEmpty()) {
                    val errorMsg = "ERRORE: API_ID e API_HASH non configurati!\n\n" +
                            "Per far funzionare l'autenticazione Telegram:\n" +
                            "1. Vai su https://my.telegram.org\n" +
                            "2. Accedi con il tuo numero di telefono\n" +
                            "3. Vai su 'API development tools'\n" +
                            "4. Crea una nuova applicazione\n" +
                            "5. Copia API_ID e API_HASH\n" +
                            "6. Inseriscili in Config.kt"
                    Log.e(TAG, errorMsg)
                    handler.onUpdate(TdlibUpdate.Error(errorMsg))
                    return@launch
                }
                
                // Prepara directory per TDLib
                val databaseDir = File(context.filesDir, Config.TDLIB_DATABASE_DIR)
                if (!databaseDir.exists()) {
                    databaseDir.mkdirs()
                }
                
                isInitialized = true
                Log.d(TAG, "TDLib client inizializzato (simulato - integra TDLib reale per funzionalità completa)")
                
                // Se autenticato, non verificare di nuovo lo stato
                if (!isAuthenticated) {
                    // Verifica stato autenticazione solo se non autenticato
                    checkAuthenticationState()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore inizializzazione TDLib", e)
                handler.onUpdate(TdlibUpdate.Error("Errore inizializzazione: ${e.message}"))
            }
        }
    }
    
    /**
     * Verifica lo stato di autenticazione
     */
    private suspend fun checkAuthenticationState() {
        // In produzione, qui si verificherebbe lo stato reale di TDLib
        // Per ora, assumiamo che se c'è una sessione salvata, l'utente è autenticato
        val authManager = AuthManager(context)
        if (authManager.isAuthenticated()) {
            isAuthenticated = true
            handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateReady"))
        } else {
            handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateWaitPhoneNumber"))
        }
    }
    
    /**
     * Invia il numero di telefono per l'autenticazione
     */
    fun setAuthenticationPhoneNumber(phoneNumber: String) {
        // Se TDLib nativo è disponibile, usalo
        if (useNativeTdlib && nativeClient != null) {
            nativeClient.setAuthenticationPhoneNumber(phoneNumber)
            return
        }
        
        // Altrimenti usa implementazione simulata
        scope.launch {
            try {
                // Verifica configurazione
                if (Config.API_ID == 0 || Config.API_HASH.isEmpty()) {
                    handler.onUpdate(TdlibUpdate.Error(
                        "Configura API_ID e API_HASH in Config.kt\n" +
                        "Vedi https://my.telegram.org"
                    ))
                    return@launch
                }
                
                Log.d(TAG, "Numero telefono impostato: $phoneNumber (simulato)")
                
                // Simula cambio di stato
                handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateWaitCode"))
                
                // Mostra messaggio informativo
                handler.onUpdate(TdlibUpdate.Error(
                    "ATTENZIONE: Implementazione simulata!\n\n" +
                    "Il codice di verifica NON arriverà perché TDLib non è ancora integrato.\n\n" +
                    "Per integrare TDLib reale:\n" +
                    "1. Compila TDLib per Android (vedi TDLIB_INTEGRATION.md)\n" +
                    "2. Aggiungi le librerie native al progetto\n" +
                    "3. Scommenta il codice in TdlibNativeClient.kt\n" +
                    "4. Configura correttamente API_ID e API_HASH"
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Errore invio numero telefono", e)
                handler.onUpdate(TdlibUpdate.Error("Errore: ${e.message}"))
            }
        }
    }
    
    /**
     * Verifica il codice di autenticazione
     */
    fun checkAuthenticationCode(code: String) {
        // Se TDLib nativo è disponibile, usalo
        if (useNativeTdlib && nativeClient != null) {
            nativeClient.checkAuthenticationCode(code)
            return
        }
        
        // Altrimenti usa implementazione simulata
        scope.launch {
            try {
                Log.d(TAG, "Codice verificato: $code (simulato)")
                
                // Simula ricezione dati utente
                val user = TelegramUser(
                    id = System.currentTimeMillis(),
                    firstName = "Utente",
                    phoneNumber = AuthManager(context).getPhoneNumber()
                )
                
                val authManager = AuthManager(context)
                authManager.saveUser(user)
                
                isAuthenticated = true
                handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateReady"))
            } catch (e: Exception) {
                Log.e(TAG, "Errore verifica codice", e)
                handler.onUpdate(TdlibUpdate.Error("Errore: ${e.message}"))
            }
        }
    }
    
    /**
     * Verifica la password 2FA
     */
    fun checkAuthenticationPassword(password: String) {
        // Se TDLib nativo è disponibile, usalo
        if (useNativeTdlib && nativeClient != null) {
            nativeClient.checkAuthenticationPassword(password)
            return
        }
        
        // Altrimenti usa implementazione simulata
        scope.launch {
            try {
                Log.d(TAG, "Password 2FA verificata: *** (simulato)")
                
                // Simula ricezione dati utente
                val user = TelegramUser(
                    id = System.currentTimeMillis(),
                    firstName = "Utente",
                    phoneNumber = AuthManager(context).getPhoneNumber()
                )
                
                val authManager = AuthManager(context)
                authManager.saveUser(user)
                
                isAuthenticated = true
                handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateReady"))
            } catch (e: Exception) {
                Log.e(TAG, "Errore verifica password 2FA", e)
                handler.onUpdate(TdlibUpdate.Error("Errore: ${e.message}"))
            }
        }
    }
    
    /**
     * Invia un messaggio al bot
     */
    fun sendMessage(text: String, onSuccess: ((Long) -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        Log.d(TAG, "sendMessage: useNativeTdlib=$useNativeTdlib, nativeClient=${nativeClient != null}")
        
        // Se TDLib nativo è disponibile, usalo
        if (useNativeTdlib && nativeClient != null) {
            Log.d(TAG, "Uso TDLib nativo per inviare messaggio")
            nativeClient.sendMessage(text, onSuccess, onError)
            return
        }
        
        Log.d(TAG, "Uso implementazione SIMULATA per inviare messaggio")
        // Altrimenti usa implementazione simulata
        scope.launch {
            try {
                if (!isAuthenticated) {
                    withContext(Dispatchers.Main) {
                        onError?.invoke("Non autenticato")
                    }
                    return@launch
                }
                
                Log.d(TAG, "Invio messaggio al bot: $text (simulato)")
                
                // Simula risposta del bot
                withContext(Dispatchers.Main) {
                    onSuccess?.invoke(System.currentTimeMillis())
                    
                    // Simula ricezione risposta dopo un breve delay
                    scope.launch {
                        kotlinx.coroutines.delay(1000)
                        handler.onUpdate(
                            TdlibUpdate.NewMessage(
                                text = "Risposta simulata per: $text",
                                date = System.currentTimeMillis(),
                                isFromBot = true
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore invio messaggio", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke("Errore: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Invia /start al bot all'avvio
     */
    fun sendStartCommand() {
        sendMessage("/start")
    }
    
    /**
     * Disconnette il client
     */
    fun disconnect() {
        // Se TDLib nativo è disponibile, usalo
        if (useNativeTdlib && nativeClient != null) {
            nativeClient.disconnect()
            isAuthenticated = false
            isInitialized = false
            return
        }
        
        // Altrimenti usa implementazione simulata
        scope.launch {
            try {
                handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateLoggingOut"))
                
                isAuthenticated = false
                isInitialized = false
                
                handler.onUpdate(TdlibUpdate.AuthorizationState("authorizationStateClosed"))
                Log.d(TAG, "Client disconnesso")
            } catch (e: Exception) {
                Log.e(TAG, "Errore disconnessione", e)
            }
        }
    }
    
    fun isReady(): Boolean {
        return if (useNativeTdlib && nativeClient != null) {
            nativeClient.isReady()
        } else {
            isAuthenticated && isInitialized
        }
    }
}
