package com.arisoli.parcheggiscaleacheck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.arisoli.parcheggiscaleacheck.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var telegramClient: TelegramClient
    private lateinit var tdlibHandler: TdlibHandler
    
    // Flag to track if we should handle Ready state
    // We skip navigation if already authenticated when fragment starts
    private var hasStartedObserving = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Usa il singleton per TelegramClient e TdlibHandler
        tdlibHandler = TelegramClientSingleton.getTdlibHandler()
        telegramClient = TelegramClientSingleton.getTelegramClient(requireContext())
        
        // Osserva lo stato di autenticazione
        observeAuthState()
        
        binding.sendPhoneButton.setOnClickListener {
            val phoneNumber = binding.phoneInput.text?.toString()?.trim()
            if (phoneNumber.isNullOrEmpty()) {
                showError("Inserisci un numero di telefono")
                return@setOnClickListener
            }
            sendPhoneNumber(phoneNumber)
        }
        
        binding.verifyCodeButton.setOnClickListener {
            val input = binding.codeInput.text?.toString()?.trim()
            if (input.isNullOrEmpty()) {
                val currentState = tdlibHandler.authenticationState.value
                if (currentState is AuthState.WaitingPassword) {
                    showError("Inserisci la password 2FA")
                } else {
                    showError("Inserisci il codice di verifica")
                }
                return@setOnClickListener
            }
            
            // Determina se stiamo verificando codice o password
            val currentState = tdlibHandler.authenticationState.value
            if (currentState is AuthState.WaitingPassword) {
                verifyPassword(input)
            } else {
                verifyCode(input)
            }
        }
    }
    
    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            tdlibHandler.authenticationState.collect { state ->
                android.util.Log.d("LoginFragment", "Stato autenticazione: ${state.javaClass.simpleName}")
                when (state) {
                    is AuthState.WaitingPhoneNumber -> {
                        showPhoneInput()
                    }
                    is AuthState.WaitingCode -> {
                        showCodeInput()
                    }
                    is AuthState.WaitingPassword -> {
                        showPasswordInput()
                    }
                    is AuthState.Ready -> {
                        android.util.Log.d("LoginFragment", "Autenticazione pronta, hasStartedObserving=$hasStartedObserving")
                        // Skip navigation if this is the initial collection and user is already authenticated
                        // MainActivity will handle navigation in this case
                        if (hasStartedObserving) {
                            android.util.Log.d("LoginFragment", "Navigando a HomeFragment")
                            navigateToHome()
                        } else {
                            android.util.Log.d("LoginFragment", "Skipping navigation - MainActivity gestirà la navigazione")
                        }
                    }
                    is AuthState.LoggingOut -> {
                        // Mostra loading durante il logout
                        showLoadingState()
                    }
                    is AuthState.Closed -> {
                        // Dopo logout completato, mostra input telefono
                        showPhoneInput()
                    }
                }
                // After first collection, enable navigation handling
                hasStartedObserving = true
            }
        }
        
        // Osserva gli errori
        viewLifecycleOwner.lifecycleScope.launch {
            tdlibHandler.errorMessage.collect { error ->
                if (error != null) {
                    android.util.Log.e("LoginFragment", "Errore ricevuto: $error")
                    showError(error)
                    // Pulisci l'errore dopo averlo mostrato
                    tdlibHandler.clearError()
                }
            }
        }
    }
    
    private fun sendPhoneNumber(phoneNumber: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE
        binding.sendPhoneButton.isEnabled = false
        
        telegramClient.setAuthenticationPhoneNumber(phoneNumber)
    }
    
    private fun verifyCode(code: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE
        binding.verifyCodeButton.isEnabled = false
        binding.codeInput.isEnabled = false
        
        telegramClient.checkAuthenticationCode(code)
        
        // Timeout: se dopo 30 secondi non c'è risposta, riabilita il bottone
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (_binding == null) return@postDelayed
            if (binding.progressBar.visibility == View.VISIBLE) {
                binding.progressBar.visibility = View.GONE
                binding.verifyCodeButton.isEnabled = true
                binding.codeInput.isEnabled = true
                showError("Timeout: nessuna risposta. Verifica il codice e riprova.")
            }
        }, 30000)
    }
    
    private fun verifyPassword(password: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE
        binding.verifyCodeButton.isEnabled = false
        binding.codeInput.isEnabled = false
        
        telegramClient.checkAuthenticationPassword(password)
        
        // Timeout: se dopo 30 secondi non c'è risposta, riabilita il bottone
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (_binding == null) return@postDelayed
            if (binding.progressBar.visibility == View.VISIBLE) {
                binding.progressBar.visibility = View.GONE
                binding.verifyCodeButton.isEnabled = true
                binding.codeInput.isEnabled = true
                showError("Timeout: nessuna risposta. Verifica la password e riprova.")
            }
        }, 30000)
    }
    
    private fun showPhoneInput() {
        binding.phoneInputLayout.visibility = View.VISIBLE
        binding.codeInputLayout.visibility = View.GONE
        binding.sendPhoneButton.visibility = View.VISIBLE
        binding.verifyCodeButton.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.sendPhoneButton.isEnabled = true
        binding.phoneInput.isEnabled = true
    }

    private fun showLoadingState() {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE
        binding.sendPhoneButton.isEnabled = false
        binding.verifyCodeButton.isEnabled = false
        binding.codeInput.isEnabled = false
        binding.phoneInput.isEnabled = false
    }
    
    private fun showCodeInput() {
        android.util.Log.d("LoginFragment", "Mostra input codice")
        binding.phoneInputLayout.visibility = View.VISIBLE
        binding.codeInputLayout.visibility = View.VISIBLE
        binding.codeInputLayout.hint = "Codice di verifica"
        binding.codeInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        binding.codeInputLayout.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
        binding.sendPhoneButton.visibility = View.GONE
        binding.verifyCodeButton.visibility = View.VISIBLE
        binding.verifyCodeButton.text = "Verifica codice"
        binding.progressBar.visibility = View.GONE
        binding.verifyCodeButton.isEnabled = true
        binding.codeInput.isEnabled = true
        binding.codeInput.text?.clear()
    }
    
    private fun showPasswordInput() {
        android.util.Log.d("LoginFragment", "Mostra input password 2FA")
        binding.phoneInputLayout.visibility = View.VISIBLE
        binding.codeInputLayout.visibility = View.VISIBLE
        binding.codeInputLayout.hint = "Password 2FA"
        binding.codeInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        binding.codeInputLayout.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
        binding.sendPhoneButton.visibility = View.GONE
        binding.verifyCodeButton.visibility = View.VISIBLE
        binding.verifyCodeButton.text = "Inserisci password"
        binding.progressBar.visibility = View.GONE
        binding.verifyCodeButton.isEnabled = true
        binding.codeInput.isEnabled = true
        binding.codeInput.text?.clear()
    }
    
    private fun showError(message: String) {
        android.util.Log.d("LoginFragment", "Mostra errore: $message")
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        binding.sendPhoneButton.isEnabled = true
        binding.verifyCodeButton.isEnabled = true
        binding.codeInput.isEnabled = true
    }
    
    private fun navigateToHome() {
        android.util.Log.d("LoginFragment", "navigateToHome chiamato")
        // Nascondi progress bar
        binding.progressBar.visibility = View.GONE
        binding.verifyCodeButton.isEnabled = true
        binding.codeInput.isEnabled = true
        
        // La navigazione sarà gestita da MainActivity
        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            android.util.Log.d("LoginFragment", "Chiamando onAuthenticationSuccess su MainActivity")
            mainActivity.onAuthenticationSuccess()
        } else {
            android.util.Log.e("LoginFragment", "Activity non è MainActivity!")
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
