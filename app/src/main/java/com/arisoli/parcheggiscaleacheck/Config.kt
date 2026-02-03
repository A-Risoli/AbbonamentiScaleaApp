package com.arisoli.parcheggiscaleacheck

object Config {
    // Bot username (es. "nomebot" senza @)
    // Questo è l'username del bot a cui inviare i messaggi
    const val BOT_USERNAME = BuildConfig.BOT_USERNAME
    
    // API credentials per TDLib
    // IMPORTANTE: Questi NON sono il bot token!
    // Per ottenerli:
    // 1. Vai su https://my.telegram.org
    // 2. Accedi con il tuo numero di telefono
    // 3. Vai su "API development tools"
    // 4. Crea una nuova applicazione
    // 5. Copia api_id e api_hash qui sotto
    const val API_ID = BuildConfig.API_ID
    const val API_HASH = BuildConfig.API_HASH
    
    // Directory per TDLib database
    const val TDLIB_DATABASE_DIR = "tdlib"
}
