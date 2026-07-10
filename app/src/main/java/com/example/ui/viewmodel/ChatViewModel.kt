package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class User(
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val isGuest: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val chatRepository: ChatRepository
    val allSessions: StateFlow<List<ChatSession>>

    private val sharedPrefs = application.getSharedPreferences("logic_coach_prefs", android.content.Context.MODE_PRIVATE)
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _activeSession = MutableStateFlow<ChatSession?>(null)
    val activeSession: StateFlow<ChatSession?> = _activeSession.asStateFlow()

    private val _isCoachTyping = MutableStateFlow(false)
    val isCoachTyping: StateFlow<Boolean> = _isCoachTyping.asStateFlow()

    val activeMessages: StateFlow<List<ChatMessage>>

    init {
        val database = AppDatabase.getDatabase(application)
        chatRepository = ChatRepository(database.chatDao())
        
        // Load persistent user session
        val email = sharedPrefs.getString("user_email", null)
        val name = sharedPrefs.getString("user_name", null)
        val photoUrl = sharedPrefs.getString("user_photo", null)
        val isGuest = sharedPrefs.getBoolean("user_is_guest", false)
        if (email != null && name != null) {
            _currentUser.value = User(name, email, photoUrl, isGuest)
        }
        
        allSessions = chatRepository.allSessions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        activeMessages = _activeSession.flatMapLatest { session ->
            if (session != null) {
                chatRepository.getMessagesForSession(session.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun login(displayName: String, email: String, photoUrl: String? = null, isGuest: Boolean = false) {
        viewModelScope.launch {
            val user = User(displayName, email, photoUrl, isGuest)
            sharedPrefs.edit()
                .putString("user_email", email)
                .putString("user_name", displayName)
                .putString("user_photo", photoUrl)
                .putBoolean("user_is_guest", isGuest)
                .apply()
            _currentUser.value = user
        }
    }

    fun logout() {
        viewModelScope.launch {
            sharedPrefs.edit().clear().apply()
            _currentUser.value = null
            _activeSession.value = null
        }
    }

    fun createSessionAndSelect(problemTitle: String, problemDescription: String) {
        viewModelScope.launch {
            val sessionId = chatRepository.createSession(problemTitle, problemDescription)
            val initialGreeting = """
                Namaste! Coding seekhne se pehle, apni preferred language choose karo:
                1. English
                2. हिंदी (Hindi)
                3. मराठी (Marathi)
                4. Hinglish (Hindi + English mix)

                Reply with 1, 2, 3, or 4.
            """.trimIndent()

            // Insert initial coach greeting
            chatRepository.insertMessage(
                ChatMessage(
                    sessionId = sessionId,
                    sender = "COACH",
                    text = initialGreeting
                )
            )

            // Select the session
            selectSession(sessionId)
        }
    }

    fun selectSession(sessionId: Int) {
        viewModelScope.launch {
            val session = chatRepository.getSessionById(sessionId)
            _activeSession.value = session
        }
    }

    fun deselectSession() {
        _activeSession.value = null
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            if (_activeSession.value?.id == sessionId) {
                _activeSession.value = null
            }
            chatRepository.deleteSession(sessionId)
        }
    }

    fun updateSessionLanguage(languageOption: Int) {
        val session = _activeSession.value ?: return
        viewModelScope.launch {
            val updated = session.copy(language = languageOption, status = "IN_PROGRESS")
            chatRepository.updateSession(updated)
            _activeSession.value = updated
        }
    }

    fun toggleSessionStatus() {
        val session = _activeSession.value ?: return
        val nextStatus = if (session.status == "SOLVED") "IN_PROGRESS" else "SOLVED"
        viewModelScope.launch {
            val updated = session.copy(status = nextStatus)
            chatRepository.updateSession(updated)
            _activeSession.value = updated
        }
    }

    fun sendMessage(text: String) {
        val session = _activeSession.value ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            // Save STUDENT's message
            val studentMessage = ChatMessage(
                sessionId = session.id,
                sender = "STUDENT",
                text = text
            )
            chatRepository.insertMessage(studentMessage)

            // Auto-detect language selection response
            if (session.language == null) {
                val cleanedText = text.trim()
                val selectedLanguage = when {
                    cleanedText == "1" || cleanedText.equals("english", ignoreCase = true) -> 1
                    cleanedText == "2" || cleanedText.equals("hindi", ignoreCase = true) -> 2
                    cleanedText == "3" || cleanedText.equals("marathi", ignoreCase = true) -> 3
                    cleanedText == "4" || cleanedText.equals("hinglish", ignoreCase = true) -> 4
                    else -> null
                }
                if (selectedLanguage != null) {
                    val updatedSession = session.copy(language = selectedLanguage, status = "IN_PROGRESS", lastUpdated = System.currentTimeMillis())
                    chatRepository.updateSession(updatedSession)
                    _activeSession.value = updatedSession
                }
            } else {
                // Update timestamp on session for sorting
                val updatedSession = session.copy(lastUpdated = System.currentTimeMillis())
                chatRepository.updateSession(updatedSession)
                _activeSession.value = updatedSession
            }

            // Get current message list (including the one just sent)
            val currentMessages = activeMessages.value + studentMessage

            // Coach typing state
            _isCoachTyping.value = true

            // Send to Gemini
            val replyText = withContext(Dispatchers.IO) {
                chatRepository.sendChatToGemini(session.id, currentMessages)
            }

            // Save COACH's reply
            val coachMessage = ChatMessage(
                sessionId = session.id,
                sender = "COACH",
                text = replyText
            )
            chatRepository.insertMessage(coachMessage)

            _isCoachTyping.value = false
        }
    }
}

class ChatViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
