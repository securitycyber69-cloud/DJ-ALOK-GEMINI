package com.example.core.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.domain.model.ResponseLanguage
import com.example.domain.model.VoicePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VoiceState {
  data object Idle : VoiceState

  data class Listening(val partialTranscript: String = "") : VoiceState

  data class TranscriptReady(val transcript: String) : VoiceState

  data class Speaking(val messageId: String) : VoiceState

  data class Error(val message: String) : VoiceState
}

interface SpeechToTextGateway {
  fun startListening(
    languageTag: String,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onError: (String) -> Unit,
  )

  fun startListeningWithRms(
    languageTag: String,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onError: (String) -> Unit,
    onRmsNormalized: (Float) -> Unit,
  ) = startListening(languageTag, onPartial, onFinal, onError)

  fun stopListening()

  fun release()
}

interface TextToSpeechGateway {
  fun speak(
    messageId: String,
    text: String,
    preferences: VoicePreferences,
    onStart: (String) -> Unit,
    onDone: () -> Unit,
    onError: (String) -> Unit,
  )

  fun stop()

  fun release()
}

@Singleton
class AndroidSpeechToTextEngine @Inject constructor(
  @ApplicationContext private val context: Context,
) : SpeechToTextGateway {

  private val mainHandler = Handler(Looper.getMainLooper())
  private var recognizer: SpeechRecognizer? = null

  override fun startListening(
    languageTag: String,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    startListeningWithRms(languageTag, onPartial, onFinal, onError) {}
  }

  override fun startListeningWithRms(
    languageTag: String,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onError: (String) -> Unit,
    onRmsNormalized: (Float) -> Unit,
  ) {
    mainHandler.post {
      if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        onError("Speech recognition service is unavailable on this device.")
        return@post
      }

      recognizer?.destroy()
      val sr = SpeechRecognizer.createSpeechRecognizer(context)
      recognizer = sr

      sr.setRecognitionListener(
        object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) = Unit
          override fun onBeginningOfSpeech() = Unit
          override fun onRmsChanged(rmsdB: Float) {
            // Normalize SpeechRecognizer's [-2f..12f] dB range into [0f..1f]
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            onRmsNormalized(normalized)
          }

          override fun onBufferReceived(buffer: ByteArray?) = Unit
          override fun onEndOfSpeech() {
            onRmsNormalized(0f)
          }

          override fun onEvent(eventType: Int, params: Bundle?) = Unit

          override fun onPartialResults(partialResults: Bundle?) {
            val text =
              partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) onPartial(text)
          }

          override fun onResults(results: Bundle?) {
            onRmsNormalized(0f)
            val text =
              results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
              onFinal(text)
            } else {
              onError("No speech detected. Please try again.")
            }
          }

          override fun onError(error: Int) {
            onRmsNormalized(0f)
            val message =
              when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                  "Microphone permission is required for voice input."
                SpeechRecognizer.ERROR_NO_MATCH -> "Could not recognize speech. Try speaking again."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected before timeout."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition."
                else -> "Speech recognition error ($error)."
              }
            onError(message)
          }
        }
      )

      val intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
      sr.startListening(intent)
    }
  }

  override fun stopListening() {
    mainHandler.post {
      recognizer?.stopListening()
    }
  }

  override fun release() {
    mainHandler.post {
      recognizer?.destroy()
      recognizer = null
    }
  }
}

@Singleton
class AndroidTextToSpeechEngine @Inject constructor(
  @ApplicationContext private val context: Context,
) : TextToSpeechGateway {

  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  private var tts: TextToSpeech? = null
  private var isReady: Boolean = false
  private var activeMessageId: String? = null
  private var onStartCallback: ((String) -> Unit)? = null
  private var onDoneCallback: (() -> Unit)? = null
  private var onErrorCallback: ((String) -> Unit)? = null
  private var focusRequest: AudioFocusRequest? = null

  private fun requestAudioFocus() {
    val am = audioManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val req =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_ASSISTANT)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build()
          )
          .build()
      focusRequest = req
      am.requestAudioFocus(req)
    } else {
      @Suppress("DEPRECATION")
      am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    }
  }

  private fun abandonAudioFocus() {
    val am = audioManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      focusRequest?.let { am.abandonAudioFocusRequest(it) }
      focusRequest = null
    } else {
      @Suppress("DEPRECATION")
      am.abandonAudioFocus(null)
    }
  }

  private fun ensureInitialized(onInitialized: () -> Unit) {
    if (tts != null && isReady) {
      onInitialized()
      return
    }
    if (tts == null) {
      tts =
        TextToSpeech(context) { status ->
          isReady = status == TextToSpeech.SUCCESS
          if (isReady) {
            tts?.setOnUtteranceProgressListener(
              object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                  activeMessageId?.let { onStartCallback?.invoke(it) }
                }

                override fun onDone(utteranceId: String?) {
                  abandonAudioFocus()
                  onDoneCallback?.invoke()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                  abandonAudioFocus()
                  onErrorCallback?.invoke("Text-to-speech playback failed.")
                }
              }
            )
            onInitialized()
          } else {
            abandonAudioFocus()
            onErrorCallback?.invoke("Unable to initialize Android Text-to-Speech.")
          }
        }
    }
  }

  override fun speak(
    messageId: String,
    text: String,
    preferences: VoicePreferences,
    onStart: (String) -> Unit,
    onDone: () -> Unit,
    onError: (String) -> Unit,
  ) {
    activeMessageId = messageId
    onStartCallback = onStart
    onDoneCallback = onDone
    onErrorCallback = onError

    val cleanText = stripMarkdownForSpeech(text)
    if (cleanText.isBlank()) {
      onDone()
      return
    }

    ensureInitialized {
      val engine = tts ?: return@ensureInitialized
      requestAudioFocus()
      val locale =
        when (preferences.preferredLanguage) {
          ResponseLanguage.BENGALI -> Locale.forLanguageTag("bn-BD")
          ResponseLanguage.HINDI -> Locale.forLanguageTag("hi-IN")
          ResponseLanguage.ENGLISH,
          ResponseLanguage.AUTO -> Locale.US
        }
      engine.language = locale
      engine.setSpeechRate(preferences.speechRate.coerceIn(0.6f, 1.8f))
      engine.setPitch(preferences.pitch.coerceIn(0.7f, 1.5f))
      engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }
  }

  override fun stop() {
    tts?.stop()
    abandonAudioFocus()
    onDoneCallback?.invoke()
  }

  override fun release() {
    tts?.stop()
    abandonAudioFocus()
    tts?.shutdown()
    tts = null
    isReady = false
  }

  private fun stripMarkdownForSpeech(markdown: String): String {
    return markdown
      .replace(Regex("```[\\s\\S]*?```"), " Code block omitted. ")
      .replace(Regex("[*_`#>-]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }
}

@Singleton
class VoiceController @Inject constructor(
  private val sttGateway: SpeechToTextGateway,
  private val ttsGateway: TextToSpeechGateway,
) {
  private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
  val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

  private val _audioRmsNormalized = MutableStateFlow(0f)
  val audioRmsNormalized: StateFlow<Float> = _audioRmsNormalized.asStateFlow()

  fun startListening(language: ResponseLanguage) {
    // Always interrupt TTS immediately when user initiates listening
    ttsGateway.stop()
    _audioRmsNormalized.value = 0f
    _voiceState.value = VoiceState.Listening()
    sttGateway.startListeningWithRms(
      languageTag = language.localeTag,
      onPartial = { partial -> _voiceState.value = VoiceState.Listening(partial) },
      onFinal = { finalTranscript ->
        _audioRmsNormalized.value = 0f
        _voiceState.value = VoiceState.TranscriptReady(finalTranscript)
      },
      onError = { err ->
        _audioRmsNormalized.value = 0f
        _voiceState.value = VoiceState.Error(err)
      },
      onRmsNormalized = { rms -> _audioRmsNormalized.value = rms.coerceIn(0f, 1f) },
    )
  }

  fun interruptSpeakingAndListen(language: ResponseLanguage) {
    if (_voiceState.value is VoiceState.Speaking) {
      stopSpeaking()
    }
    startListening(language)
  }

  fun stopListening() {
    sttGateway.stopListening()
    _audioRmsNormalized.value = 0f
    if (_voiceState.value is VoiceState.Listening) {
      _voiceState.value = VoiceState.Idle
    }
  }

  fun speakResponse(messageId: String, text: String, preferences: VoicePreferences) {
    _voiceState.value = VoiceState.Speaking(messageId)
    ttsGateway.speak(
      messageId = messageId,
      text = text,
      preferences = preferences,
      onStart = { id -> _voiceState.value = VoiceState.Speaking(id) },
      onDone = { _voiceState.value = VoiceState.Idle },
      onError = { err -> _voiceState.value = VoiceState.Error(err) },
    )
  }

  fun stopSpeaking() {
    ttsGateway.stop()
    _voiceState.value = VoiceState.Idle
  }

  fun resetState() {
    _audioRmsNormalized.value = 0f
    _voiceState.value = VoiceState.Idle
  }

  fun release() {
    sttGateway.release()
    ttsGateway.release()
  }
}
