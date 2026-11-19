package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.BuildConfig
import com.example.ai.provider.AiProvider
import com.example.ai.provider.GeminiAiProvider
import com.example.ai.provider.GroqAiProvider
import com.example.ai.provider.ProxyAiProvider
import com.example.ai.provider.api.GeminiApiService
import com.example.ai.provider.api.GroqApiService
import com.example.ai.provider.api.ProxyApiService
import com.example.core.voice.AndroidSpeechToTextEngine
import com.example.core.voice.AndroidTextToSpeechEngine
import com.example.core.voice.SpeechToTextGateway
import com.example.core.voice.TextToSpeechGateway
import com.example.data.local.db.ChatDao
import com.example.data.local.db.MemoryDao
import com.example.data.local.db.MousomiDatabase
import com.example.data.local.db.Part4OperationsDao
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.SettingsRepositoryImpl
import com.example.domain.model.ProviderType
import com.example.domain.orchestrator.AiOrchestrator
import com.example.domain.orchestrator.AiTool
import com.example.domain.orchestrator.CodingFormatGuidanceTool
import com.example.domain.orchestrator.DateTimeContextTool
import com.example.domain.orchestrator.DefaultAiOrchestrator
import com.example.domain.repository.ChatRepository
import com.example.domain.repository.SettingsRepository
import com.example.domain.router.DefaultModelRouter
import com.example.domain.router.ModelRouter
import com.example.research.search.CompositeSearchService
import com.example.research.search.SearchService
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GroqRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProxyRetrofit

@MapKey
annotation class ProviderKey(val value: ProviderType)

@Module
@InstallIn(SingletonComponent::class)
object NetworkAndStorageModule {

  @Provides
  @Singleton
  fun provideJson(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "blockType"
  }

  @Provides
  @Singleton
  fun provideOkHttpClient(): OkHttpClient {
    val httpsEnforcementInterceptor = Interceptor { chain ->
      val req = chain.request()
      if (!req.url.isHttps) {
        throw IllegalStateException("Insecure HTTP requests are blocked. HTTPS is required.")
      }
      chain.proceed(req)
    }

    val logging =
      HttpLoggingInterceptor().apply {
        level =
          if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
          else HttpLoggingInterceptor.Level.NONE
        redactHeader("Authorization")
        redactHeader("x-goog-api-key")
        redactHeader("X-API-KEY")
      }

    return OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(45, TimeUnit.SECONDS)
      .readTimeout(90, TimeUnit.SECONDS)
      .callTimeout(105, TimeUnit.SECONDS)
      .addInterceptor(httpsEnforcementInterceptor)
      .addInterceptor(logging)
      .build()
  }

  @Provides
  @Singleton
  @GeminiRetrofit
  fun provideGeminiRetrofit(client: OkHttpClient, json: Json): Retrofit =
    Retrofit.Builder()
      .baseUrl("https://generativelanguage.googleapis.com/")
      .client(client)
      .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
      .build()

  @Provides
  @Singleton
  @GroqRetrofit
  fun provideGroqRetrofit(client: OkHttpClient, json: Json): Retrofit =
    Retrofit.Builder()
      .baseUrl("https://api.groq.com/openai/")
      .client(client)
      .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
      .build()

  @Provides
  @Singleton
  @ProxyRetrofit
  fun provideProxyRetrofit(client: OkHttpClient, json: Json): Retrofit =
    Retrofit.Builder()
      .baseUrl("https://placeholder.mousomi.invalid/")
      .client(client)
      .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
      .build()

  @Provides
  @Singleton
  fun provideGeminiApiService(@GeminiRetrofit retrofit: Retrofit): GeminiApiService =
    retrofit.create(GeminiApiService::class.java)

  @Provides
  @Singleton
  fun provideGroqApiService(@GroqRetrofit retrofit: Retrofit): GroqApiService =
    retrofit.create(GroqApiService::class.java)

  @Provides
  @Singleton
  fun provideProxyApiService(@ProxyRetrofit retrofit: Retrofit): ProxyApiService =
    retrofit.create(ProxyApiService::class.java)

  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext context: Context): MousomiDatabase =
    Room.databaseBuilder(context, MousomiDatabase::class.java, "mousomi_ai.db")
      .addMigrations(MousomiDatabase.MIGRATION_1_2, MousomiDatabase.MIGRATION_2_3)
      .build()

  @Provides
  fun provideChatDao(database: MousomiDatabase): ChatDao = database.chatDao()

  @Provides
  fun provideMemoryDao(database: MousomiDatabase): MemoryDao = database.memoryDao()

  @Provides
  fun providePart4OperationsDao(database: MousomiDatabase): Part4OperationsDao =
    database.part4OperationsDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

  @Binds
  @Singleton
  abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

  @Binds
  @Singleton
  abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

  @Binds
  @Singleton
  abstract fun bindModelRouter(impl: DefaultModelRouter): ModelRouter

  @Binds
  @Singleton
  abstract fun bindAiOrchestrator(impl: DefaultAiOrchestrator): AiOrchestrator

  @Binds
  @Singleton
  abstract fun bindSearchService(impl: CompositeSearchService): SearchService

  @Binds
  @Singleton
  abstract fun bindSttGateway(impl: AndroidSpeechToTextEngine): SpeechToTextGateway

  @Binds
  @Singleton
  abstract fun bindTtsGateway(impl: AndroidTextToSpeechEngine): TextToSpeechGateway

  @Binds
  @IntoMap
  @ProviderKey(ProviderType.GEMINI)
  abstract fun bindGeminiProvider(impl: GeminiAiProvider): AiProvider

  @Binds
  @IntoMap
  @ProviderKey(ProviderType.GROQ)
  abstract fun bindGroqProvider(impl: GroqAiProvider): AiProvider

  @Binds
  @IntoMap
  @ProviderKey(ProviderType.PROXY)
  abstract fun bindProxyProvider(impl: ProxyAiProvider): AiProvider

  @Binds
  @IntoSet
  abstract fun bindDateTimeTool(impl: DateTimeContextTool): AiTool

  @Binds
  @IntoSet
  abstract fun bindCodingTool(impl: CodingFormatGuidanceTool): AiTool
}
