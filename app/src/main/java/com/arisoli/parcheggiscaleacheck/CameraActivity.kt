package com.arisoli.parcheggiscaleacheck

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                CameraPreviewWithTextRecognition(
                    onBack = { finish() },
                    activity = this@CameraActivity
                )
            }
        }
    }
}

@Composable
fun CameraPreviewWithTextRecognition(
    onBack: () -> Unit,
    activity: CameraActivity? = null,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var matchedText by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var isOverlayVisible by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var requestState by remember { mutableStateOf(PlateRequestState.Idle) }
    var requestMessage by remember { mutableStateOf<String?>(null) }
    
    // Flag to track if we're waiting for a response
    var waitingForResponse by remember { mutableStateOf(false) }
    
    // Get TelegramClient and TdlibHandler instances via singleton
    val tdlibHandler = remember { TelegramClientSingleton.getTdlibHandler() }
    val telegramClient = remember { TelegramClientSingleton.getTelegramClient(context) }
    
    // License plate regex pattern
    val targaRegex = Regex(
        "([A-Z]{2})[.\\s-]*(\\d{3})[.\\s-]*([A-Z]{2})",
        RegexOption.IGNORE_CASE
    )
    
    // Osserva il flag per chiudere la camera quando torna a modalità testo
    LaunchedEffect(Unit) {
        while (true) {
            if (TelegramClientSingleton.shouldCloseCameraActivity) {
                Log.d("CameraActivity", "Ricevuto segnale di chiusura")
                TelegramClientSingleton.shouldCloseCameraActivity = false
                onBack()
                break
            }
            kotlinx.coroutines.delay(100)
        }
    }
    
    // Set up callback for new messages
    DisposableEffect(Unit) {
        val mainHandler = Handler(Looper.getMainLooper())
        val callback: (BotMessage) -> Unit = { message ->
            Log.d("CameraActivity", "Callback ricevuto! waitingForResponse=$waitingForResponse, isFromBot=${message.isFromBot}, messaggio=${message.text}")
            // Solo messaggi dal bot, non l'echo del messaggio inviato
            if (message.isFromBot) {
                mainHandler.post {
                    Log.d("CameraActivity", "mainHandler.post eseguito, waitingForResponse=$waitingForResponse")
                    if (waitingForResponse) {
                        Log.d("CameraActivity", "Aggiorno stato a Success")
                        requestState = PlateRequestState.Success
                        requestMessage = message.text
                        waitingForResponse = false
                    }
                }
            } else {
                Log.d("CameraActivity", "Messaggio ignorato (non dal bot)")
            }
        }
        Log.d("CameraActivity", "Callback impostato")
        tdlibHandler.onNewMessageCallback = callback
        
        onDispose {
            Log.d("CameraActivity", "DisposableEffect onDispose")
            // Clear callback when leaving
            if (tdlibHandler.onNewMessageCallback == callback) {
                tdlibHandler.onNewMessageCallback = null
            }
            // Pulisci i messaggi così non appaiono in HomeFragment
            tdlibHandler.clearMessages()
        }
    }
    
    // Also observe errors
    val errorMsg by tdlibHandler.errorMessage.collectAsState()
    LaunchedEffect(errorMsg) {
        if (!errorMsg.isNullOrBlank() && waitingForResponse) {
            requestState = PlateRequestState.Error
            requestMessage = errorMsg
            waitingForResponse = false
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
                
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            val mainHandler = Handler(Looper.getMainLooper())
                            it.setAnalyzer(cameraExecutor, TextAnalyzer(
                                context = ctx,
                                onTextDetected = { text ->
                                    // Only process if overlay is not visible and not already processing
                                    if (!isProcessing && !isOverlayVisible) {
                                        val match = targaRegex.find(text)
                                        if (match != null) {
                                            val detectedTarga = match.value
                                            mainHandler.post {
                                                if (!isProcessing && !isOverlayVisible) {
                                                    isProcessing = true
                                                    // Always show the license plate
                                                    matchedText = detectedTarga
                                                    isOverlayVisible = true
                                                    requestState = PlateRequestState.Sending
                                                    requestMessage = null
                                                    waitingForResponse = true
                                                    
                                                    // Use TelegramClient to send message via TDLib
                                                    telegramClient.sendMessage(
                                                        text = detectedTarga,
                                                        onSuccess = { messageId ->
                                                            requestState = PlateRequestState.WaitingResponse
                                                            requestMessage = "Messaggio inviato, in attesa risposta..."
                                                            isProcessing = false
                                                        },
                                                        onError = { error ->
                                                            requestState = PlateRequestState.Error
                                                            requestMessage = error
                                                            waitingForResponse = false
                                                            isProcessing = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            ))
                        }
                    
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    
                    try {
                        cameraProvider.unbindAll()
                        val cameraInstance = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalyzer
                        )
                        camera = cameraInstance
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                    } catch (exc: Exception) {
                        Log.e("CameraPreview", "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Back button (bottom-left)
        val btnSurfaceColor = colorResource(id = R.color.surface)
        val btnOnSurfaceColor = colorResource(id = R.color.text_primary)
        val btnPrimaryColor = colorResource(id = R.color.primary)
        
        FloatingActionButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            containerColor = btnSurfaceColor.copy(alpha = 0.9f),
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Torna indietro",
                tint = btnOnSurfaceColor
            )
        }
        
        // Flash toggle button (bottom-right)
        FloatingActionButton(
            onClick = {
                isFlashOn = !isFlashOn
                camera?.cameraControl?.enableTorch(isFlashOn)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = btnSurfaceColor.copy(alpha = 0.9f),
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
        ) {
            Icon(
                painter = painterResource(
                    id = if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
                ),
                contentDescription = if (isFlashOn) "Spegni flash" else "Accendi flash",
                tint = if (isFlashOn) {
                    btnPrimaryColor
                } else {
                    btnOnSurfaceColor
                }
            )
        }
        
        // Overlay for matched text
        if (isOverlayVisible && matchedText != null) {
            LicensePlateOverlay(
                text = matchedText!!,
                requestState = requestState,
                requestMessage = requestMessage,
                onDismiss = {
                    isOverlayVisible = false
                    matchedText = null
                    requestState = PlateRequestState.Idle
                    requestMessage = null
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun LicensePlateOverlay(
    text: String,
    requestState: PlateRequestState,
    requestMessage: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strippedText = text.replace(Regex("\\s+"), "")

    // Colori dal tema XML
    val surfaceColor = colorResource(id = R.color.surface)
    val primaryColor = colorResource(id = R.color.primary)
    val textOnSurface = colorResource(id = R.color.text_primary)
    val dangerColor = colorResource(id = R.color.danger)
    val dangerLightColor = colorResource(id = R.color.terracotta)

    val containerColor: androidx.compose.ui.graphics.Color
    val onContainerColor: androidx.compose.ui.graphics.Color
    val message: String
    val details: String?

    when (requestState) {
        PlateRequestState.Sending -> {
            containerColor = surfaceColor.copy(alpha = 0.95f)
            onContainerColor = primaryColor
            message = "Invio in corso..."
            details = null
        }
        PlateRequestState.WaitingResponse -> {
            containerColor = surfaceColor.copy(alpha = 0.95f)
            onContainerColor = primaryColor
            message = "In attesa risposta bot..."
            details = null
        }
        PlateRequestState.Success -> {
            containerColor = surfaceColor.copy(alpha = 0.95f)
            onContainerColor = textOnSurface
            message = "Richiesta inviata ✅"
            details = requestMessage
        }
        PlateRequestState.Error -> {
            containerColor = dangerLightColor.copy(alpha = 0.95f)
            onContainerColor = dangerColor
            message = "Errore invio ❌"
            details = requestMessage
        }
        PlateRequestState.Idle -> {
            containerColor = surfaceColor.copy(alpha = 0.95f)
            onContainerColor = textOnSurface
            message = "Targa rilevata"
            details = null
        }
    }
    
    Card(
        modifier = modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Municipal badge
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = primaryColor
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "🏛️",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = onContainerColor,
                textAlign = TextAlign.Center
            )
            
            // Show loading indicator if waiting for response
            if (requestState == PlateRequestState.WaitingResponse) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(
                    color = onContainerColor,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // License plate display
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (requestState == PlateRequestState.Error) {
                        dangerColor
                    } else {
                        surfaceColor
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = strippedText,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (requestState == PlateRequestState.Error) {
                        colorResource(id = R.color.white)
                    } else {
                        textOnSurface
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }

            if (!details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = details,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onContainerColor,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor
                )
            ) {
                Text(
                    text = "Continua Scansione",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private class TextAnalyzer(
    private val context: Context,
    private val onTextDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {
    
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val detectedText = visionText.text
                    if (detectedText.isNotEmpty()) {
                        onTextDetected(detectedText)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("TextAnalyzer", "Text recognition failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
