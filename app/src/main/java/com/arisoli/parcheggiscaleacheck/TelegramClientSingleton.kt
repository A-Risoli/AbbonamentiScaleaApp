package com.arisoli.parcheggiscaleacheck

import android.content.Context
import android.util.Log

/**
 * Singleton per TelegramClient
 * Garantisce una singola istanza condivisa tra Activity e Fragment
 */
object TelegramClientSingleton {
    private const val TAG = "TelegramClientSingleton"
    private var telegramClient: TelegramClient? = null
    private var tdlibHandler: TdlibHandler? = null
    
    // Flag per comunicare che la camera deve chiudersi
    @Volatile
    var shouldCloseCameraActivity = false
    
    /**
     * Inizializza o restituisce l'istanza esistente di TdlibHandler
     */
    fun getTdlibHandler(): TdlibHandler {
        if (tdlibHandler == null) {
            Log.d(TAG, "Creo nuova istanza TdlibHandler")
            tdlibHandler = TdlibHandler()
        } else {
            Log.d(TAG, "Riuso istanza TdlibHandler esistente")
        }
        return tdlibHandler!!
    }
    
    /**
     * Inizializza o restituisce l'istanza esistente di TelegramClient
     */
    fun getTelegramClient(context: Context): TelegramClient {
        if (telegramClient == null) {
            Log.d(TAG, "Creo nuova istanza TelegramClient")
            val handler = getTdlibHandler()
            telegramClient = TelegramClient(context.applicationContext, handler)
            telegramClient?.initialize()
        } else {
            Log.d(TAG, "Riuso istanza TelegramClient esistente, isReady=${telegramClient?.isReady()}")
        }
        return telegramClient!!
    }
    
    /**
     * Verifica se il client è pronto
     */
    fun isReady(): Boolean {
        val ready = telegramClient?.isReady() ?: false
        Log.d(TAG, "isReady: $ready")
        return ready
    }
    
    /**
     * Restituisce l'handler per osservare lo stato di autenticazione
     */
    fun getHandler(): TdlibHandler {
        return getTdlibHandler()
    }
    
    /**
     * Reset del singleton (per logout completo)
     */
    fun reset() {
        Log.d(TAG, "Reset singleton")
        telegramClient?.disconnect()
        telegramClient = null
        tdlibHandler?.clearMessages()
        tdlibHandler = null
    }
}
