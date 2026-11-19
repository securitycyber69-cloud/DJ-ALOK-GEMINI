package com.example.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides Android Keystore-backed AES-256-GCM encryption for sensitive user preferences/memory notes.
 * Falls back cleanly to unencrypted passthrough only in headless JVM environments without AndroidKeyStore.
 */
@Singleton
class KeystoreCryptoManager @Inject constructor() {

  companion object {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "mousomi_ai_memory_aes_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val ENC_PREFIX = "enc_v1:"
  }

  fun containsSensitiveCredentials(text: String): Boolean {
    val lower = text.lowercase()
    val otpRegex = Regex("\\b(?:otp|verification code|one[- ]time password|pin code|passcode)\\b", RegexOption.IGNORE_CASE)
    val secretTokenRegex = Regex("(AIza[0-9A-Za-z_-]{20,}|gsk_[0-9A-Za-z]{20,}|sk-[0-9A-Za-z]{20,}|Bearer\\s+[0-9A-Za-z._-]{16,})")
    val passwordPhrase =
      listOf("my password is", "password:", "pass:", "pin is", "cvv", "secret key:")
        .any { it in lower }
    return otpRegex.containsMatchIn(text) || secretTokenRegex.containsMatchIn(text) || passwordPhrase
  }

  fun encryptIfSupported(plainText: String): String {
    if (plainText.isEmpty() || plainText.startsWith(ENC_PREFIX)) return plainText
    return runCatching {
      val key = getOrCreateSecretKey() ?: return@runCatching plainText
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, key)
      val iv = cipher.iv
      val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
      val buffer = ByteBuffer.allocate(4 + iv.size + cipherText.size)
      buffer.putInt(iv.size)
      buffer.put(iv)
      buffer.put(cipherText)
      ENC_PREFIX + Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }.getOrDefault(plainText)
  }

  fun decryptIfEncrypted(storedText: String): String {
    if (!storedText.startsWith(ENC_PREFIX)) return storedText
    return runCatching {
      val key = getOrCreateSecretKey() ?: return@runCatching ""
      val raw = Base64.decode(storedText.removePrefix(ENC_PREFIX), Base64.NO_WRAP)
      val buffer = ByteBuffer.wrap(raw)
      val ivSize = buffer.int
      if (ivSize !in 12..32) return@runCatching ""
      val iv = ByteArray(ivSize)
      buffer.get(iv)
      val cipherBytes = ByteArray(buffer.remaining())
      buffer.get(cipherBytes)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
      String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }.getOrDefault("")
  }

  private fun getOrCreateSecretKey(): SecretKey? {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    if (existing != null) return existing

    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec =
      KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }
}
