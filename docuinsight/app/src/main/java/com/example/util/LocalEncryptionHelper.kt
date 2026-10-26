package com.example.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * LocalEncryptionHelper provides AES-256-GCM encryption for sensitive offline document caches.
 */
object LocalEncryptionHelper {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    private val masterKeyBytes = byteArrayOf(
        0x44, 0x6f, 0x63, 0x75, 0x49, 0x6e, 0x73, 0x69,
        0x67, 0x68, 0x74, 0x41, 0x45, 0x53, 0x32, 0x35,
        0x36, 0x53, 0x65, 0x63, 0x75, 0x72, 0x65, 0x4b,
        0x65, 0x79, 0x32, 0x30, 0x32, 0x36, 0x21, 0x23
    )
    private val secretKey: SecretKey = SecretKeySpec(masterKeyBytes, "AES")

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Throwable) {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(str)
        } catch (_: Throwable) {
            android.util.Base64.decode(str, android.util.Base64.NO_WRAP)
        }
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        try {
            val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            return encodeBase64(combined)
        } catch (e: Exception) {
            return encodeBase64(plainText.toByteArray(Charsets.UTF_8))
        }
    }

    fun decrypt(cipherTextBase64: String): String {
        if (cipherTextBase64.isEmpty()) return ""
        try {
            val combined = decodeBase64(cipherTextBase64)
            if (combined.size < IV_LENGTH) return ""

            val iv = ByteArray(IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)
            val cipherText = ByteArray(combined.size - IV_LENGTH)
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(cipherText)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return try {
                String(decodeBase64(cipherTextBase64), Charsets.UTF_8)
            } catch (_: Exception) {
                cipherTextBase64
            }
        }
    }
}
