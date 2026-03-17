package com.example.familyshield.utils

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {

    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    // Hash password using SHA-256
    fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // Generate secure random token
    fun generateSecureToken(length: Int = 32): String {
        val random = SecureRandom()
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    // Encrypt data
    fun encrypt(data: String, key: String): String? {
        return try {
            val secretKey = generateKey(key)
            val cipher = Cipher.getInstance(AES_MODE)

            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)

            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            val combined = iv + encryptedData

            Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    // Decrypt data
    fun decrypt(encryptedData: String, key: String): String? {
        return try {
            val secretKey = generateKey(key)
            val cipher = Cipher.getInstance(AES_MODE)

            val combined = Base64.decode(encryptedData, Base64.DEFAULT)
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val data = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun generateKey(key: String): SecretKey {
        val sha = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha.digest(key.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    // Generate checksum
    fun generateChecksum(data: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(data.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}