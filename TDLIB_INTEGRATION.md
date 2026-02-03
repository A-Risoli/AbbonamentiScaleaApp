# Integrazione TDLib Reale

## Stato Attuale

L'implementazione è pronta per TDLib reale. Il codice in `TdlibNativeClient.kt` contiene tutto il necessario, ma è commentato perché richiede TDLib compilato.

## Passi per Completare l'Integrazione

### 1. Ottieni API_ID e API_HASH

1. Vai su https://my.telegram.org
2. Accedi con il tuo numero di telefono
3. Vai su "API development tools"
4. Crea una nuova applicazione
5. Copia `api_id` e `api_hash`
6. Inseriscili in `Config.kt`:

```kotlin
const val API_ID = 12345678 // Il tuo API ID
const val API_HASH = "abcdef1234567890abcdef1234567890" // Il tuo API Hash
const val BOT_USERNAME = "nomebot" // Username del bot (senza @)
```

### 2. Compila TDLib per Android

**Opzione A: Usa precompilato (se disponibile)**
- Cerca librerie TDLib precompilate per Android
- Aggiungi al progetto come dipendenza

**Opzione B: Compila da sorgente**
1. Clona TDLib: `git clone https://github.com/tdlib/td.git`
2. Segui le istruzioni per Android: https://github.com/tdlib/td#building
3. Richiede:
   - Android NDK
   - CMake
   - Compilazione nativa (C++)

### 3. Aggiungi TDLib al Progetto

1. Aggiungi le librerie native compilate in `app/src/main/jniLibs/`
2. Aggiungi dipendenza Java (se disponibile) in `build.gradle.kts`:
```kotlin
dependencies {
    // Esempio (verifica versione corretta):
    implementation("org.drinkless:tdlib:1.8.0")
}
```

### 4. Attiva TDLib nel Codice

1. Apri `app/src/main/java/com/arisoli/parcheggiscaleacheck/tdlib/TdlibNativeClient.kt`
2. Scommenta tutto il codice commentato (rimuovi `/* */` e `//`)
3. Aggiungi gli import necessari:
```kotlin
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
```

### 5. Aggiorna isTdlibAvailable()

In `TelegramClient.kt`, aggiorna il metodo `isTdlibAvailable()`:
```kotlin
private fun isTdlibAvailable(): Boolean {
    return try {
        Class.forName("org.drinkless.tdlib.Client")
        true
    } catch (e: Exception) {
        false
    }
}
```

### 6. Testa l'Integrazione

1. Compila e avvia l'app
2. Inserisci il numero di telefono
3. Il codice di verifica dovrebbe arrivare via SMS o Telegram

## Struttura del Codice

- `TelegramClient.kt` - Wrapper principale (usa TdlibNativeClient se disponibile)
- `TdlibNativeClient.kt` - Implementazione TDLib reale (codice commentato, da scommentare)
- `TdlibHandler.kt` - Gestisce update e callback
- `Config.kt` - Configurazione (API_ID, API_HASH, BOT_USERNAME)

## Note Importanti

- **NON usare il bot token** - Quello è per Bot API, non per client utente
- **API_ID e API_HASH** sono diversi dal bot token
- TDLib richiede compilazione nativa per Android
- La sessione viene salvata automaticamente da TDLib
- Il codice è già strutturato per usare TDLib reale quando disponibile

## Risorse

- TDLib GitHub: https://github.com/tdlib/td
- Documentazione TDLib: https://core.telegram.org/tdlib
- API Development Tools: https://my.telegram.org
- Esempi Android: https://github.com/tdlib/td/tree/master/example/android
