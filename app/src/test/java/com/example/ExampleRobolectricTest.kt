package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.core.image.ImageProcessor
import com.example.core.security.SecurityValidator
import com.example.core.voice.SpeechToTextGateway
import com.example.core.voice.TextToSpeechGateway
import com.example.core.voice.VoiceController
import com.example.domain.model.AiResponseState
import com.example.domain.model.AppSettings
import com.example.domain.model.DomainConversation
import com.example.domain.model.DomainMessage
import com.example.domain.model.RoutingDecision
import com.example.domain.model.TaskCategory
import com.example.domain.model.TokenUsage
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.model.VoicePreferences
import com.example.domain.orchestrator.AiOrchestrator
import com.example.domain.orchestrator.OrchestrationEvent
import com.example.domain.orchestrator.OrchestrationRequest
import com.example.domain.repository.ChatRepository
import com.example.domain.repository.SettingsRepository
import com.example.ui.screens.MainChatScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MousomiTheme
import com.example.ui.viewmodel.MousomiViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeRule = createComposeRule()

  private fun createTestViewModel(context: Context): MousomiViewModel {
    val chatRepo =
      object : ChatRepository {
        private val convs = MutableStateFlow<List<DomainConversation>>(emptyList())
        private val msgs = MutableStateFlow<List<DomainMessage>>(emptyList())

        override fun observeConversations(): Flow<List<DomainConversation>> = convs

        override fun observeMessages(conversationId: String): Flow<List<DomainMessage>> =
          msgs.map { list -> list.filter { it.conversationId == conversationId } }

        override suspend fun getMessagesSnapshot(conversationId: String): List<DomainMessage> =
          msgs.value.filter { it.conversationId == conversationId }

        override suspend fun ensureConversation(
          conversationId: String,
          initialTitle: String,
        ): DomainConversation {
          val conv = DomainConversation(conversationId, initialTitle, 0L, 0L)
          if (convs.value.none { it.id == conversationId }) {
            convs.value = convs.value + conv
          }
          return conv
        }

        override suspend fun updateConversationTitle(conversationId: String, title: String) {
          convs.value =
            convs.value.map { if (it.id == conversationId) it.copy(title = title) else it }
        }

        override suspend fun deleteConversation(conversationId: String) {
          convs.value = convs.value.filterNot { it.id == conversationId }
          msgs.value = msgs.value.filterNot { it.conversationId == conversationId }
        }

        override suspend fun upsertMessage(message: DomainMessage) {
          ensureConversation(message.conversationId, "Test Chat")
          msgs.value = msgs.value.filterNot { it.id == message.id } + message
        }

        override suspend fun deleteMessage(messageId: String) {
          msgs.value = msgs.value.filterNot { it.id == messageId }
        }

        override suspend fun deleteMessagesFrom(conversationId: String, fromTimestampMs: Long) {
          msgs.value =
            msgs.value.filterNot {
              it.conversationId == conversationId && it.timestampMs >= fromTimestampMs
            }
        }
      }

    val settingsRepo =
      object : SettingsRepository {
        private val flow = MutableStateFlow(AppSettings(geminiApiKey = "AIzaDummyTestKey123456"))

        override fun observeSettings(): Flow<AppSettings> = flow

        override suspend fun getSettingsSnapshot(): AppSettings = flow.value

        override suspend fun saveSettings(settings: AppSettings) {
          flow.value = settings
        }
      }

    val orchestrator =
      object : AiOrchestrator {
        override fun processMessage(request: OrchestrationRequest): Flow<OrchestrationEvent> = flow {
          emit(
            OrchestrationEvent.Routed(
              RoutingDecision(
                selectedModel = VerifiedModelCatalog.geminiFlash,
                category = TaskCategory.CASUAL_CONVERSATION,
                reason = "Test route",
                useProxy = false,
              )
            )
          )
          emit(
            OrchestrationEvent.StateChanged(
              AiResponseState.Completed(
                fullText = "Here is a **Kotlin** snippet:\n```kotlin\nval greeting = \"Hello\"\n```",
                modelName = "Gemini 2.5 Flash",
                tokenUsage = TokenUsage(10, 12, 22),
              )
            )
          )
        }

        override fun deriveConversationTitle(firstPrompt: String): String = firstPrompt.take(24)
      }

    val stt =
      object : SpeechToTextGateway {
        override fun startListening(
          languageTag: String,
          onPartial: (String) -> Unit,
          onFinal: (String) -> Unit,
          onError: (String) -> Unit,
        ) = Unit

        override fun stopListening() = Unit

        override fun release() = Unit
      }

    val tts =
      object : TextToSpeechGateway {
        override fun speak(
          messageId: String,
          text: String,
          preferences: VoicePreferences,
          onStart: (String) -> Unit,
          onDone: () -> Unit,
          onError: (String) -> Unit,
        ) = Unit

        override fun stop() = Unit

        override fun release() = Unit
      }

    return MousomiViewModel(
      aiOrchestrator = orchestrator,
      chatRepository = chatRepo,
      settingsRepository = settingsRepo,
      imageProcessor = ImageProcessor(context),
      voiceController = VoiceController(stt, tts),
      securityValidator = SecurityValidator(),
    )
  }

  @Test
  fun `read app name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Mousomi AI", appName)
  }

  @Test
  fun `welcome screen renders greeting suggestions and streams response with attachment`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vm = createTestViewModel(context)

    composeRule.setContent {
      val state = vm.uiState.collectAsStateWithLifecycle().value
      MousomiTheme(themeMode = state.themeMode) {
        MainChatScreen(
          uiState = state,
          suggestions = vm.defaultSuggestions,
          viewModel = vm,
          onNavigateToSettings = {},
        )
      }
    }

    composeRule.onNodeWithText("The mic is yours,").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("HACKER").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Spark a story idea from my experiences").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Create engaging video captions").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Make a cutout phone wallpaper").performScrollTo().assertIsDisplayed()

    vm.onAttachSampleImage()
    composeRule.onNodeWithTag("suggestion_row_wallpaper").performClick()
    composeRule.onNodeWithTag("send_message_button").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithText("Gemini 2.5 Flash • 22 tok").fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag("chat_messages_list").performScrollToIndex(0)
    composeRule.onNodeWithTag("inline_image_preview_bubble").assertIsDisplayed()
    composeRule.onNodeWithTag("chat_messages_list").performScrollToIndex(1)
    composeRule.onNodeWithText("Gemini 2.5 Flash • 22 tok").assertIsDisplayed()
  }

  @Test
  fun `settings screen renders Gemini and Groq API key fields and save button`() {
    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        SettingsScreen(
          geminiApiKey = "",
          groqApiKey = "",
          themeMode = AppThemeMode.DARK,
          onGeminiApiKeyChange = {},
          onGroqApiKeyChange = {},
          onThemeModeChange = {},
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("gemini_api_key_input").performScrollTo().performTextInput("AIzaTest")
    composeRule.onNodeWithTag("groq_api_key_input").performScrollTo().performTextInput("gskTest")
    composeRule.onNodeWithTag("save_settings_button").performScrollTo().assertIsDisplayed().performClick()
  }
}
