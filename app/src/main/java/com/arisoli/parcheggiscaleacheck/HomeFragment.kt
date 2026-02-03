package com.arisoli.parcheggiscaleacheck

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.arisoli.parcheggiscaleacheck.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var authManager: AuthManager
    private lateinit var telegramClient: TelegramClient
    private lateinit var tdlibHandler: TdlibHandler
    
    private var isCameraMode = false
    
    companion object {
        private const val PREF_NAME = "HomeFragmentPrefs"
        private const val KEY_INPUT_MODE = "input_mode_preference"
        private const val MODE_CAMERA = "camera"
        private const val MODE_TEXTBOX = "textbox"
    }
    
    // Camera permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCameraActivity()
        } else {
            android.widget.Toast.makeText(
                requireContext(),
                "Permesso fotocamera necessario per la scansione",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    // ActivityResult launcher per CameraActivity
    // Quando CameraActivity finisce, torna a modalità Text
    private val cameraActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: androidx.activity.result.ActivityResult ->
        // Camera finita, torna a modalità Text se ancora in Camera mode
        if (isCameraMode) {
            isCameraMode = false
            saveInputMode()
            // Pulisci i messaggi ricevuti dalla camera
            tdlibHandler.clearMessages()
            updateUIForInputMode()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        authManager = AuthManager(requireContext())
        
        // Usa il singleton per TelegramClient e TdlibHandler
        tdlibHandler = TelegramClientSingleton.getTdlibHandler()
        telegramClient = TelegramClientSingleton.getTelegramClient(requireContext())
        
        // Mostra informazioni utente
        val user = authManager.getUser()
        if (user != null) {
            binding.welcomeText.text = "Benvenuto, ${user.firstName}"
        }
        
        // Restore input mode preference
        restoreInputMode()
        
        // Osserva i messaggi ricevuti
        observeMessages()
        
        binding.sendPlateButton.setOnClickListener {
            val plate = binding.plateInput.text?.toString()?.trim()
            if (plate.isNullOrEmpty()) {
                showResponse("Inserisci una targa", isError = true)
                return@setOnClickListener
            }
            sendPlate(plate)
        }
        
        binding.scanPlateButton.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }
        
        binding.inputModeToggle.setOnClickListener {
            toggleInputMode()
        }
    }
    
    private fun restoreInputMode() {
        val prefs = requireContext().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        val savedMode = prefs.getString(KEY_INPUT_MODE, MODE_TEXTBOX) ?: MODE_TEXTBOX
        isCameraMode = (savedMode == MODE_CAMERA)
        updateUIForInputMode()
    }
    
    private fun saveInputMode() {
        val prefs = requireContext().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_INPUT_MODE, if (isCameraMode) MODE_CAMERA else MODE_TEXTBOX).apply()
    }
    
    private fun toggleInputMode() {
        isCameraMode = !isCameraMode
        saveInputMode()
        updateUIForInputMode()
    }
    
    private fun updateUIForInputMode() {
        if (isCameraMode) {
            // Camera mode - auto-open camera
            binding.inputModeToggle.setImageResource(R.drawable.ic_keyboard)
            binding.plateInputLayout.visibility = View.GONE
            binding.sendPlateButton.visibility = View.GONE
            binding.scanPlateButton.visibility = View.GONE
            // Pulisci risposta quando si passa a camera
            binding.responseText.visibility = View.GONE
            binding.responseLabel.visibility = View.GONE
            tdlibHandler.clearMessages()
            // Reset flag e apri camera automaticamente
            TelegramClientSingleton.shouldCloseCameraActivity = false
            checkCameraPermissionAndLaunch()
        } else {
            // Textbox mode - close camera if open
            binding.inputModeToggle.setImageResource(R.drawable.ic_camera)
            binding.plateInputLayout.visibility = View.VISIBLE
            binding.sendPlateButton.visibility = View.VISIBLE
            binding.scanPlateButton.visibility = View.GONE
            // Segnala a CameraActivity di chiudersi
            TelegramClientSingleton.shouldCloseCameraActivity = true
        }
    }
    
    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCameraActivity()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun launchCameraActivity() {
        val intent = Intent(requireContext(), CameraActivity::class.java)
        cameraActivityLauncher.launch(intent)
    }
    
    private fun observeMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            tdlibHandler.receivedMessages.collect { messages ->
                if (tdlibHandler.suppressHomeMessages) {
                    binding.responseText.visibility = View.GONE
                    binding.responseLabel.visibility = View.GONE
                    return@collect
                }
                // Controlla ogni volta se siamo in modalità camera
                if (isCameraMode) {
                    // In modalità camera: nascondi sempre la risposta
                    binding.responseText.visibility = View.GONE
                    binding.responseLabel.visibility = View.GONE
                } else {
                    // In modalità textbox: mostra i messaggi
                    if (messages.isNotEmpty()) {
                        val lastMessage = messages.last()
                        showResponse(lastMessage.text, isError = false)
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Ricarica modalità input da preferenze
        restoreInputMode()
        // Non mostrare messaggi se si usa la camera (vengono mostrati lì)
        // Mostra solo se si usa la textbox
        if (isCameraMode) {
            // Nascondi la risposta e pulisci i messaggi quando si torna dalla camera
            binding.responseText.visibility = View.GONE
            binding.responseLabel.visibility = View.GONE
            tdlibHandler.clearMessages()
        } else {
            // Solo in modalità textbox ricarica i messaggi
            val messages = tdlibHandler.receivedMessages.value
            if (messages.isNotEmpty()) {
                val lastMessage = messages.last()
                showResponse(lastMessage.text, isError = false)
            }
        }
    }
    
    private fun sendPlate(plate: String) {
        binding.loadingProgress.visibility = View.VISIBLE
        binding.responseText.visibility = View.GONE
        binding.responseLabel.visibility = View.GONE
        binding.sendPlateButton.isEnabled = false
        
        telegramClient.sendMessage(
            text = plate,
            onSuccess = { messageId ->
                binding.loadingProgress.visibility = View.GONE
                binding.sendPlateButton.isEnabled = true
                showResponse("In attesa della risposta del bot...", isError = false)
            },
            onError = { error ->
                binding.loadingProgress.visibility = View.GONE
                binding.sendPlateButton.isEnabled = true
                showResponse("Errore: $error", isError = true)
            }
        )
    }
    
    private fun showResponse(text: String, isError: Boolean) {
        if (tdlibHandler.suppressHomeMessages) {
            binding.responseText.visibility = View.GONE
            binding.responseLabel.visibility = View.GONE
            return
        }
        // In modalità camera non mostrare mai la risposta in HomeFragment
        if (isCameraMode) {
            binding.responseText.visibility = View.GONE
            binding.responseLabel.visibility = View.GONE
            return
        }
        if (text.isBlank()) {
            binding.responseText.visibility = View.GONE
            binding.responseLabel.visibility = View.GONE
            return
        }
        
        binding.responseText.text = text
        val textColor = if (isError) {
            ContextCompat.getColor(requireContext(), R.color.danger)
        } else {
            ContextCompat.getColor(requireContext(), R.color.text_primary)
        }
        binding.responseText.setTextColor(textColor)
        binding.responseText.visibility = View.VISIBLE
        binding.responseLabel.visibility = View.VISIBLE
    }
    
    private fun logout() {
        authManager.logout()
        TelegramClientSingleton.reset()
        
        // La navigazione sarà gestita da MainActivity
        (activity as? MainActivity)?.onLogout()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
