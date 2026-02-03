package com.arisoli.parcheggiscaleacheck

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import com.arisoli.parcheggiscaleacheck.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        // Rimuovi qualsiasi icona/indicatori nella toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowHomeEnabled(false)
        supportActionBar?.setDisplayUseLogoEnabled(false)
        binding.toolbar.logo = null
        binding.toolbar.navigationIcon = null

        authManager = AuthManager(this)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)

        // Nascondi FAB
        binding.fab.visibility = android.view.View.GONE

        // Verifica autenticazione e naviga
        checkAuthenticationAndNavigate()
    }

    private fun checkAuthenticationAndNavigate() {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        
        if (authManager.isAuthenticated()) {
            // Utente autenticato, naviga a HomeFragment
            if (navController.currentDestination?.id != R.id.HomeFragment) {
                navController.navigate(R.id.HomeFragment)
            }
            // Inizializza TDLib e invia /start
            initializeTelegramClientAndSendStart()
            // Osserva lo stato di autenticazione TDLib
            observeTdlibAuthState()
        } else {
            // Utente non autenticato, naviga a LoginFragment
            if (navController.currentDestination?.id != R.id.LoginFragment) {
                navController.navigate(R.id.LoginFragment)
            }
        }
    }
    
    /**
     * Osserva lo stato di autenticazione TDLib.
     * Se TDLib richiede il login (WaitPhoneNumber), invalida AuthManager e naviga al login.
     */
    private fun observeTdlibAuthState() {
        val handler = TelegramClientSingleton.getHandler()
        lifecycleScope.launch {
            handler.authenticationState.collectLatest { state ->
                Log.d("MainActivity", "TDLib auth state: ${state.javaClass.simpleName}")
                when (state) {
                    is AuthState.WaitingPhoneNumber -> {
                        // TDLib richiede login - la sessione è persa
                        if (authManager.isAuthenticated()) {
                            Log.w("MainActivity", "Sessione TDLib persa, redirect al login")
                            authManager.logout()
                            runOnUiThread {
                                val navController = findNavController(R.id.nav_host_fragment_content_main)
                                if (navController.currentDestination?.id != R.id.LoginFragment) {
                                    navController.navigate(R.id.LoginFragment)
                                }
                            }
                        }
                    }
                    is AuthState.Ready -> {
                        Log.d("MainActivity", "TDLib autenticato")
                    }
                    else -> {
                        // Altri stati intermedi
                    }
                }
            }
        }
    }

    private fun initializeTelegramClientAndSendStart() {
        val telegramClient = TelegramClientSingleton.getTelegramClient(this)
        
        // Attendi che il client sia pronto e invia /start
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (TelegramClientSingleton.isReady()) {
                telegramClient.sendStartCommand()
            } else {
                // Riprova dopo un breve delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    telegramClient.sendStartCommand()
                }, 2000)
            }
        }, 1000)
    }

    fun onAuthenticationSuccess() {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        navController.navigate(R.id.action_LoginFragment_to_HomeFragment)
        
        // Inizializza TDLib e invia /start
        initializeTelegramClientAndSendStart()
    }

    fun onLogout() {
        TelegramClientSingleton.reset()
        
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        navController.navigate(R.id.action_HomeFragment_to_LoginFragment)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_my_id -> {
                // Mostra le informazioni dell'utente in un dialog
                val authManager = AuthManager(this)
                val user = authManager.getUser()
                if (user != null) {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("My ID")
                        .setMessage("ID: ${user.id}\nNome: ${user.firstName}")
                        .setCancelable(true)
                        .setPositiveButton("OK") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
                true
            }
            R.id.action_logout -> {
                // Effettua il logout
                authManager.logout()
                onLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Non chiamare reset() qui perché potrebbe essere solo un cambio configurazione
    }
}