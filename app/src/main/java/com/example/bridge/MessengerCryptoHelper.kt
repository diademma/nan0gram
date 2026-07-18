package com.example

import android.util.Base64
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class MessengerCryptoHelper(private val log: (String) -> Unit) {

    fun encryptRsa(plainText: String, publicKeyB64: String): String {
        return try {
            val keyBytes = Base64.decode(publicKeyB64, Base64.NO_WRAP)
            val spec = X509EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance("RSA")
            val publicKey = kf.generatePublic(spec)

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            log("[Crypto Error] RSA encryption failed: ${e.message}")
            ""
        }
    }

    fun decryptRsa(encryptedB64: String, privateKeyB64: String): String {
        return try {
            val keyBytes = Base64.decode(privateKeyB64, Base64.NO_WRAP)
            val spec = PKCS8EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance("RSA")
            val privateKey = kf.generatePrivate(spec)

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            val encryptedBytes = Base64.decode(encryptedB64, Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            log("[Crypto Error] RSA decryption failed: ${e.message}")
            ""
        }
    }

    fun encryptGcm(plainText: String, keyStr: String): String {
        return try {
            val keyBytes = keyStr.toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256")
            val keyBytes32 = digest.digest(keyBytes)
            val secretKey = SecretKeySpec(keyBytes32, "AES")
            
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            
            val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            log("[Crypto Error] Encryption failed: ${e.message}")
            ""
        }
    }

    fun decryptGcm(combinedBase64: String, keyStr: String): String {
        return try {
            val combined = Base64.decode(combinedBase64, Base64.NO_WRAP)
            if (combined.size < 12) return "[Ошибка дешифрования]"
            
            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, 12)
            
            val ciphertext = ByteArray(combined.size - 12)
            System.arraycopy(combined, 12, ciphertext, 0, ciphertext.size)
            
            val keyBytes = keyStr.toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256")
            val keyBytes32 = digest.digest(keyBytes)
            val secretKey = SecretKeySpec(keyBytes32, "AES")
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decrypted = cipher.doFinal(ciphertext)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            log("[Crypto Error] Decryption failed: ${e.message}")
            "[Ошибка дешифрования]"
        }
    }
}