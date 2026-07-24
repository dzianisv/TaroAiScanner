package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiTarotService
import com.example.data.*
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class TarotUIState {
    object Idle : TarotUIState()
    object Loading : TarotUIState()
    data class Success(val reading: TarotReading) : TarotUIState()
    data class Error(val message: String) : TarotUIState()
}

class TarotViewModel(private val repository: TarotRepository) : ViewModel() {

    companion object {
        /** Free-tier: total readings allowed before premium is required. */
        const val FREE_READING_QUOTA = 3
    }

    // Premium entitlement (fed from BillingManager.isPremium; default false)
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    /** Wire the BillingManager entitlement flow into this ViewModel. */
    fun bindPremiumFlow(flow: StateFlow<Boolean>) {
        viewModelScope.launch {
            flow.collect { _isPremium.value = it }
        }
    }

    /** True if the user may perform another reading (premium, or under free quota). */
    fun canDoReading(): Boolean =
        _isPremium.value || historyState.value.size < FREE_READING_QUOTA


    // Reading flow state
    private val _uiState = MutableStateFlow<TarotUIState>(TarotUIState.Idle)
    val uiState: StateFlow<TarotUIState> = _uiState.asStateFlow()

    private val _scannedBitmap = MutableStateFlow<Bitmap?>(null)
    val scannedBitmap: StateFlow<Bitmap?> = _scannedBitmap.asStateFlow()

    private val _spreadType = MutableStateFlow("Single Card Draw")
    val spreadType: StateFlow<String> = _spreadType.asStateFlow()

    // Virtual card draw state
    private val _drawnCard = MutableStateFlow<TarotCard?>(null)
    val drawnCard: StateFlow<TarotCard?> = _drawnCard.asStateFlow()

    private val _drawnCardOrientation = MutableStateFlow("Upright")
    val drawnCardOrientation: StateFlow<String> = _drawnCardOrientation.asStateFlow()

    // Persistent Settings & Auth Flow from Room
    val settingsState: StateFlow<TarotSettingsEntity> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TarotSettingsEntity()
        )

    // History Flow from Room (reactive based on current user)
    @OptIn(ExperimentalCoroutinesApi::class)
    val historyState: StateFlow<List<TarotReadingEntity>> = settingsState
        .flatMapLatest { settings ->
            val userId = if (settings.isSignedIn && !settings.isGuest) settings.signedInEmail else "guest"
            repository.getReadingsForUser(userId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Chat history flow from Room (reactive based on current user)
    @OptIn(ExperimentalCoroutinesApi::class)
    val chatMessagesState: StateFlow<List<TarotChatMessageEntity>> = settingsState
        .flatMapLatest { settings ->
            val userId = if (settings.isSignedIn && !settings.isGuest) settings.signedInEmail else "guest"
            repository.getChatMessagesForUser(userId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun clearAuthError() {
        _authError.value = null
    }

    fun setSpreadType(type: String) {
        _spreadType.value = type
    }

    // Triggered after scanning card
    fun startReading(bitmap: Bitmap) {
        _scannedBitmap.value = bitmap
        _uiState.value = TarotUIState.Loading

        viewModelScope.launch {
            try {
                val currentUserId = if (settingsState.value.isSignedIn && !settingsState.value.isGuest) settingsState.value.signedInEmail else "guest"
                val proxyUrl = settingsState.value.proxyUrl
                val offlineMode = settingsState.value.offlineMode
                val customApiKey = settingsState.value.customApiKey
                val idToken = getFreshIdToken()
                val result = GeminiTarotService.analyzeTarotCard(
                    bitmap = bitmap,
                    promptContext = _spreadType.value,
                    proxyUrl = proxyUrl,
                    offlineMode = offlineMode,
                    customApiKey = customApiKey,
                    idToken = idToken
                )
                
                if (result != null) {
                    _uiState.value = TarotUIState.Success(result)
                    
                    val entity = TarotReadingEntity(
                        userId = currentUserId,
                        cardName = result.cardName,
                        orientation = result.orientation,
                        summary = result.summary,
                        generalMeaning = result.generalMeaning,
                        advice = result.advice,
                        warning = result.warning,
                        luckyElementsJson = ListTypeConverter().fromList(result.luckyElements),
                        spreadType = _spreadType.value
                    )
                    repository.saveReading(entity)
                } else {
                    _uiState.value = TarotUIState.Error("Could not decode the mystic energies. Try another card.")
                }
            } catch (e: Exception) {
                _uiState.value = TarotUIState.Error("The ethereal link was interrupted: ${e.localizedMessage}")
            }
        }
    }

    // Virtual card draw operation
    fun drawVirtualCard() {
        _uiState.value = TarotUIState.Loading
        _scannedBitmap.value = null // clear physical image
        
        val card = TarotDeck.majorArcana.random()
        val orientation = if (Math.random() < 0.75) "Upright" else "Reversed"
        
        _drawnCard.value = card
        _drawnCardOrientation.value = orientation
        _spreadType.value = "Virtual Card Draw"

        viewModelScope.launch {
            try {
                val currentUserId = if (settingsState.value.isSignedIn && !settingsState.value.isGuest) settingsState.value.signedInEmail else "guest"
                val proxyUrl = settingsState.value.proxyUrl
                val offlineMode = settingsState.value.offlineMode
                val customApiKey = settingsState.value.customApiKey
                val idToken = getFreshIdToken()
                val result = GeminiTarotService.interpretVirtualCard(
                    cardName = card.name,
                    orientation = orientation,
                    spreadType = "Virtual Card Draw",
                    proxyUrl = proxyUrl,
                    offlineMode = offlineMode,
                    customApiKey = customApiKey,
                    idToken = idToken
                )

                if (result != null) {
                    _uiState.value = TarotUIState.Success(result)
                    
                    val entity = TarotReadingEntity(
                        userId = currentUserId,
                        cardName = result.cardName,
                        orientation = result.orientation,
                        summary = result.summary,
                        generalMeaning = result.generalMeaning,
                        advice = result.advice,
                        warning = result.warning,
                        luckyElementsJson = ListTypeConverter().fromList(result.luckyElements),
                        spreadType = "Virtual Card Draw"
                    )
                    repository.saveReading(entity)
                } else {
                    _uiState.value = TarotUIState.Error("The virtual cards did not align properly. Try drawing again.")
                }
            } catch (e: Exception) {
                _uiState.value = TarotUIState.Error("Celestial disturbance: ${e.localizedMessage}")
            }
        }
    }

    // Chat operations
    fun sendChatMessage(context: Context, text: String, attachedUri: String? = null) {
        if (text.trim().isEmpty() && attachedUri == null) return

        viewModelScope.launch {
            val mimeType = attachedUri?.let { uriStr ->
                try {
                    val uri = android.net.Uri.parse(uriStr)
                    context.contentResolver.getType(uri)
                } catch (e: Exception) {
                    null
                }
            }
            val bytes = attachedUri?.let { uriStr ->
                try {
                    val uri = android.net.Uri.parse(uriStr)
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (e: Exception) {
                    null
                }
            }

            val currentUserId = if (settingsState.value.isSignedIn && !settingsState.value.isGuest) settingsState.value.signedInEmail else "guest"
            // Save user message
            val userMsg = TarotChatMessageEntity(
                userId = currentUserId,
                sender = "user",
                text = text,
                mediaUri = attachedUri,
                mediaType = mimeType?.substringBefore("/")
            )
            repository.saveChatMessage(userMsg)

            _isChatLoading.value = true

            try {
                val proxyUrl = settingsState.value.proxyUrl
                val offlineMode = settingsState.value.offlineMode
                val customApiKey = settingsState.value.customApiKey
                val idToken = getFreshIdToken()
                val history = chatMessagesState.value
                val replyText = GeminiTarotService.chatWithTarotMaster(
                    history = history,
                    newMessageText = text,
                    attachedMimeType = mimeType,
                    attachedBytes = bytes,
                    proxyUrl = proxyUrl,
                    offlineMode = offlineMode,
                    customApiKey = customApiKey,
                    idToken = idToken
                )
                if (replyText != null) {
                    val modelMsg = TarotChatMessageEntity(
                        userId = currentUserId,
                        sender = "model",
                        text = replyText
                    )
                    repository.saveChatMessage(modelMsg)
                }
            } catch (e: Exception) {
                val errMsg = TarotChatMessageEntity(
                    userId = currentUserId,
                    sender = "model",
                    text = "The cosmic connection was disturbed: ${e.localizedMessage}"
                )
                repository.saveChatMessage(errMsg)
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            val userId = if (settingsState.value.isSignedIn && !settingsState.value.isGuest) settingsState.value.signedInEmail else "guest"
            repository.clearChatHistoryForUser(userId)
        }
    }

    // Proxy setting save
    fun saveProxyUrl(url: String) {
        viewModelScope.launch {
            repository.updateProxyUrl(url.trim())
        }
    }

    fun saveOfflineMode(offline: Boolean) {
        viewModelScope.launch {
            repository.updateOfflineMode(offline)
        }
    }

    fun saveCustomApiKey(key: String) {
        viewModelScope.launch {
            repository.updateCustomApiKey(key.trim())
        }
    }

    // Sign in flows
    fun handleGoogleSignIn(context: Context) {
        _isAuthLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId("248382356220-r6rc4lcskohiarh07v5qptu2pcamnnbs.apps.googleusercontent.com")
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                
                // Real parsing if available
                val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                
                // Sign in to Firebase Auth with the Google ID Token
                val firebaseAuth = FirebaseAuth.getInstance()
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                
                val tokenResult = authResult.user?.getIdToken(true)?.await()
                val firebaseIdToken = tokenResult?.token ?: ""

                // Merge guest history first
                repository.mergeGuestHistory(googleIdTokenCredential.id)
                
                repository.updateUserProfile(
                    email = googleIdTokenCredential.id,
                    name = googleIdTokenCredential.displayName ?: "Cosmic Traveler",
                    photoUrl = googleIdTokenCredential.profilePictureUri?.toString() ?: "",
                    isSignedIn = true,
                    isGuest = false,
                    idToken = firebaseIdToken
                )
            } catch (e: Exception) {
                // Remove mock fallback and propagate real error
                _authError.value = e.localizedMessage ?: "Google Sign-In failed"
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun handleGuestSignIn() {
        _isAuthLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val authResult = auth.signInAnonymously().await()
                val tokenResult = authResult.user?.getIdToken(true)?.await()
                val idToken = tokenResult?.token ?: ""
                
                repository.updateUserProfile(
                    email = "guest.seeker@cosmos.net",
                    name = "Guest Seeker",
                    photoUrl = "",
                    isSignedIn = true,
                    isGuest = true,
                    idToken = idToken
                )
            } catch (e: Exception) {
                _authError.value = e.localizedMessage ?: "Guest Sign-In failed"
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun handleSignOut() {
        viewModelScope.launch {
            FirebaseAuth.getInstance().signOut()
            repository.updateUserProfile(
                email = "",
                name = "",
                photoUrl = "",
                isSignedIn = false,
                isGuest = false,
                idToken = ""
            )
        }
    }

    fun deleteReading(id: Long) {
        viewModelScope.launch {
            repository.deleteReading(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            val userId = if (settingsState.value.isSignedIn && !settingsState.value.isGuest) settingsState.value.signedInEmail else "guest"
            repository.clearHistoryForUser(userId)
        }
    }

    fun reset() {
        _uiState.value = TarotUIState.Idle
        _scannedBitmap.value = null
    }

    private suspend fun getFreshIdToken(): String {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return ""
        return try {
            val tokenResult = currentUser.getIdToken(false).await()
            val token = tokenResult.token ?: ""
            repository.updateIdToken(token)
            token
        } catch (e: Exception) {
            settingsState.value.idToken
        }
    }
}

class TarotViewModelFactory(private val repository: TarotRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TarotViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TarotViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// Extends Google Play Services Task to work smoothly with coroutines
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(task.exception ?: Exception("Task failed"))
        }
    }
}
