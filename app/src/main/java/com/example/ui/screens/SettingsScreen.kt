package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.ConnectionMode
import com.example.domain.model.ResponseLanguage
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.model.VoicePreferences
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.LocalMousomiColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  geminiApiKey: String,
  groqApiKey: String,
  themeMode: AppThemeMode,
  connectionMode: ConnectionMode = ConnectionMode.DIRECT_DEV_KEYS,
  proxyBaseUrl: String = "",
  hydrogenBackingModelId: String = VerifiedModelCatalog.DEFAULT_HYDROGEN_BACKING_MODEL_ID,
  groqReasoningModelId: String = VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID,
  externalSearchUrl: String = "",
  externalSearchApiKey: String = "",
  analyticsSandboxEnabled: Boolean = false,
  voicePreferences: VoicePreferences = VoicePreferences(),
  settingsErrorMessage: String? = null,
  onGeminiApiKeyChange: (String) -> Unit,
  onGroqApiKeyChange: (String) -> Unit,
  onThemeModeChange: (AppThemeMode) -> Unit,
  onConnectionModeChange: (ConnectionMode) -> Unit = {},
  onProxyBaseUrlChange: (String) -> Unit = {},
  onHydrogenBackingModelIdChange: (String) -> Unit = {},
  onGroqReasoningModelIdChange: (String) -> Unit = {},
  onExternalSearchUrlChange: (String) -> Unit = {},
  onExternalSearchApiKeyChange: (String) -> Unit = {},
  onAnalyticsSandboxEnabledChange: (Boolean) -> Unit = {},
  onClearTempMediaCache: () -> Unit = {},
  onPreferredLanguageChange: (ResponseLanguage) -> Unit = {},
  onAutoSpeakChange: (Boolean) -> Unit = {},
  onSpeechRateChange: (Float) -> Unit = {},
  onSaveSettings: () -> Boolean = { true },
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val colors = LocalMousomiColors.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var showGeminiKey by remember { mutableStateOf(false) }
  var showGroqKey by remember { mutableStateOf(false) }
  var showSearchKey by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = {
      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.navigationBarsPadding().imePadding(),
      )
    },
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.settings_title),
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Medium,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
          ),
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .widthIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Text(
          text = stringResource(R.string.settings_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = colors.mutedText,
        )

        // Adaptive Theme Mode Section
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = stringResource(R.string.settings_theme_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            AppThemeMode.entries.forEach { mode ->
              val selected = mode == themeMode
              FilterChip(
                selected = selected,
                onClick = { onThemeModeChange(mode) },
                label = { Text(mode.label) },
                leadingIcon =
                  if (selected) {
                    {
                      Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                    }
                  } else null,
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.activeChipContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onBackground,
                  ),
                modifier = Modifier.weight(1f).testTag("theme_chip_${mode.name}"),
              )
            }
          }
        }

        // Connection Mode & Secure Backend Proxy Section
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 2.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Text(
              text = "AI Transport & Keys",
              fontFamily = DisplayFontFamily,
              fontWeight = FontWeight.Medium,
              fontSize = 18.sp,
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              ConnectionMode.entries.forEach { mode ->
                val selected = mode == connectionMode
                FilterChip(
                  selected = selected,
                  onClick = { onConnectionModeChange(mode) },
                  label = { Text(mode.label) },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = colors.activeChipContainer,
                      selectedLabelColor = MaterialTheme.colorScheme.onBackground,
                    ),
                  modifier = Modifier.weight(1f),
                )
              }
            }

            if (connectionMode == ConnectionMode.BACKEND_PROXY) {
              OutlinedTextField(
                value = proxyBaseUrl,
                onValueChange = onProxyBaseUrlChange,
                label = { Text("HTTPS Backend Proxy URL") },
                placeholder = { Text("https://api.your-proxy.com/") },
                leadingIcon = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().testTag("proxy_url_input"),
              )
            }

            OutlinedTextField(
              value = geminiApiKey,
              onValueChange = onGeminiApiKeyChange,
              label = { Text(stringResource(R.string.settings_gemini_key_label)) },
              placeholder = { Text(stringResource(R.string.settings_gemini_key_placeholder)) },
              leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
              trailingIcon = {
                IconButton(
                  onClick = { showGeminiKey = !showGeminiKey },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(
                    imageVector =
                      if (showGeminiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(R.string.cd_toggle_visibility),
                  )
                }
              },
              visualTransformation =
                if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = MaterialTheme.colorScheme.primary,
                  unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
              modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input"),
            )

            OutlinedTextField(
              value = groqApiKey,
              onValueChange = onGroqApiKeyChange,
              label = { Text(stringResource(R.string.settings_groq_key_label)) },
              placeholder = { Text(stringResource(R.string.settings_groq_key_placeholder)) },
              leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
              trailingIcon = {
                IconButton(
                  onClick = { showGroqKey = !showGroqKey },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(
                    imageVector =
                      if (showGroqKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(R.string.cd_toggle_visibility),
                  )
                }
              },
              visualTransformation =
                if (showGroqKey) VisualTransformation.None else PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = MaterialTheme.colorScheme.primary,
                  unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
              modifier = Modifier.fillMaxWidth().testTag("groq_api_key_input"),
            )
          }
        }

        // Model Mapping, Live Search & Analytics Sandbox
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 2.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = "Model Engine, Search & Analytics",
              fontFamily = DisplayFontFamily,
              fontWeight = FontWeight.Medium,
              fontSize = 18.sp,
            )

            OutlinedTextField(
              value = hydrogenBackingModelId,
              onValueChange = onHydrogenBackingModelIdChange,
              label = { Text("Gemini 4 Hydrogen Backing Model ID") },
              placeholder = { Text(VerifiedModelCatalog.DEFAULT_HYDROGEN_BACKING_MODEL_ID) },
              leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("hydrogen_model_id_input"),
            )

            OutlinedTextField(
              value = groqReasoningModelId,
              onValueChange = onGroqReasoningModelIdChange,
              label = { Text("Groq Reasoning Model ID") },
              placeholder = { Text(VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID) },
              leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("groq_model_id_input"),
            )

            OutlinedTextField(
              value = externalSearchUrl,
              onValueChange = onExternalSearchUrlChange,
              label = { Text("External Search API HTTPS Endpoint (Optional)") },
              placeholder = { Text("https://google.serper.dev/search") },
              leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
              modifier = Modifier.fillMaxWidth().testTag("external_search_url_input"),
            )

            if (externalSearchUrl.isNotBlank()) {
              OutlinedTextField(
                value = externalSearchApiKey,
                onValueChange = onExternalSearchApiKeyChange,
                label = { Text("External Search API Key") },
                trailingIcon = {
                  IconButton(
                    onClick = { showSearchKey = !showSearchKey },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Icon(
                      imageVector =
                        if (showSearchKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                      contentDescription = stringResource(R.string.cd_toggle_visibility),
                    )
                  }
                },
                visualTransformation =
                  if (showSearchKey) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Backend Python Analytics Sandbox", style = MaterialTheme.typography.bodyLarge)
                Text(
                  text = "Route heavy dataset tasks to HTTPS /v1/analytics/execute when proxy is active",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.mutedText,
                )
              }
              Switch(
                checked = analyticsSandboxEnabled,
                onCheckedChange = onAnalyticsSandboxEnabledChange,
              )
            }

            OutlinedButton(
              onClick = {
                onClearTempMediaCache()
                scope.launch {
                  snackbarHostState.showSnackbar("Temporary processed media cache cleared")
                }
              },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Clear Temporary Media Cache")
            }
          }
        }

        // Multilingual & Voice Preferences
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 2.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = "Language & Voice",
              fontFamily = DisplayFontFamily,
              fontWeight = FontWeight.Medium,
              fontSize = 18.sp,
            )

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              ResponseLanguage.entries.forEach { lang ->
                val selected = lang == voicePreferences.preferredLanguage
                FilterChip(
                  selected = selected,
                  onClick = { onPreferredLanguageChange(lang) },
                  label = { Text(lang.label) },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = colors.activeChipContainer,
                      selectedLabelColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Auto-speak responses",
                  style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                  text = "Read completed AI replies aloud via Android TTS",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.mutedText,
                )
              }
              Switch(
                checked = voicePreferences.autoSpeakResponses,
                onCheckedChange = onAutoSpeakChange,
              )
            }

            Column {
              Text(
                text = "Speech rate: ${"%.1fx".format(voicePreferences.speechRate)}",
                style = MaterialTheme.typography.bodyMedium,
              )
              Slider(
                value = voicePreferences.speechRate,
                onValueChange = onSpeechRateChange,
                valueRange = 0.7f..1.5f,
              )
            }
          }
        }

        if (!settingsErrorMessage.isNullOrBlank()) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF7F1D1D).copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = settingsErrorMessage,
              color = Color(0xFFFCA5A5),
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(14.dp),
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Icon(
              imageVector = Icons.Outlined.Info,
              contentDescription = null,
              tint = colors.mutedText,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text =
                "Security: API keys are never embedded in the APK or logged. Use HTTPS Backend Proxy mode in production so keys remain exclusively on your server.",
              style = MaterialTheme.typography.bodyMedium,
              color = colors.mutedText,
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
          onClick = {
            if (onSaveSettings()) {
              scope.launch {
                snackbarHostState.showSnackbar("Settings saved securely to device DataStore")
              }
            }
          },
          shape = RoundedCornerShape(28.dp),
          colors = ButtonDefaults.buttonColors(containerColor = colors.liveButtonBlue),
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("save_settings_button"),
        ) {
          Text(
            text = stringResource(R.string.settings_save_button),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
          )
        }
      }
    }
  }
}
