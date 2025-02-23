package com.example.protegotinyever.util;

import org.bouncycastle.crypto.engines.ChaChaEngine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import android.util.Log;

public class MessageEncryptor {
    private static final int NONCE_LENGTH = 8; // ChaCha20 with Bouncy Castle uses 8-byte nonce
    private static final int IV_LENGTH = 16; // AES IV
    private static final int KEY_LENGTH = 32; // 256-bit keys in bytes

    public static class EncryptionResult {
        public byte[] combinedData;

        public EncryptionResult(byte[] combinedData) {
            this.combinedData = combinedData;
        }
    }

    public static EncryptionResult encryptMessage(String message, String senderEmail, String senderPhone) throws Exception {
        byte[] nonce = generateNonceFromUserInput(senderEmail, senderPhone);
        byte[] iv = generateIV();
        byte[] chachaKey = generateKey();
        byte[] aesKey = generateKey();
        byte[] messageBytes = message.getBytes("UTF-8");

        Log.d("MessageEncryptor", "Encrypting message: " + message);

        // ChaCha20 encryption (Bouncy Castle)
        ChaChaEngine chachaEngine = new ChaChaEngine();
        ParametersWithIV chachaParams = new ParametersWithIV(new KeyParameter(chachaKey), nonce);
        chachaEngine.init(true, chachaParams);
        byte[] chachaEncrypted = new byte[messageBytes.length];
        chachaEngine.processBytes(messageBytes, 0, messageBytes.length, chachaEncrypted, 0);

        // AES-CTR encryption
        Cipher aesCipher = Cipher.getInstance("AES/CTR/NoPadding");
        SecretKeySpec aesKeySpec = new SecretKeySpec(aesKey, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKeySpec, ivSpec);
        byte[] doubleEncrypted = aesCipher.doFinal(chachaEncrypted);

        // Combine keys and encrypted data
        ByteBuffer buffer = ByteBuffer.allocate(KEY_LENGTH + KEY_LENGTH + NONCE_LENGTH + IV_LENGTH + doubleEncrypted.length);
        buffer.put(chachaKey);
        buffer.put(aesKey);
        buffer.put(nonce);
        buffer.put(iv);
        buffer.put(doubleEncrypted);
        byte[] combinedData = buffer.array();

        Log.d("MessageEncryptor", "Encryption complete, combined length: " + combinedData.length);
        return new EncryptionResult(combinedData);
    }

    public static String decryptMessage(byte[] combinedData) throws Exception {
        if (combinedData.length < KEY_LENGTH + KEY_LENGTH + NONCE_LENGTH + IV_LENGTH) {
            Log.e("MessageEncryptor", "Invalid combined data length: " + combinedData.length);
            throw new IllegalArgumentException("Invalid combined data length");
        }

        Log.d("MessageEncryptor", "Decrypting combined data, length: " + combinedData.length);

        byte[] chachaKey = new byte[KEY_LENGTH];
        byte[] aesKey = new byte[KEY_LENGTH];
        byte[] nonce = new byte[NONCE_LENGTH];
        byte[] iv = new byte[IV_LENGTH];
        byte[] encryptedData = new byte[combinedData.length - (KEY_LENGTH + KEY_LENGTH + NONCE_LENGTH + IV_LENGTH)];

        System.arraycopy(combinedData, 0, chachaKey, 0, KEY_LENGTH);
        System.arraycopy(combinedData, KEY_LENGTH, aesKey, 0, KEY_LENGTH);
        System.arraycopy(combinedData, KEY_LENGTH * 2, nonce, 0, NONCE_LENGTH);
        System.arraycopy(combinedData, KEY_LENGTH * 2 + NONCE_LENGTH, iv, 0, IV_LENGTH);
        System.arraycopy(combinedData, KEY_LENGTH * 2 + NONCE_LENGTH + IV_LENGTH, encryptedData, 0, encryptedData.length);

        // AES-CTR decryption
        Cipher aesCipher = Cipher.getInstance("AES/CTR/NoPadding");
        SecretKeySpec aesKeySpec = new SecretKeySpec(aesKey, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        aesCipher.init(Cipher.DECRYPT_MODE, aesKeySpec, ivSpec);
        byte[] chachaEncrypted = aesCipher.doFinal(encryptedData);

        // ChaCha20 decryption (Bouncy Castle)
        ChaChaEngine chachaEngine = new ChaChaEngine();
        ParametersWithIV chachaParams = new ParametersWithIV(new KeyParameter(chachaKey), nonce);
        chachaEngine.init(false, chachaParams);
        byte[] decryptedBytes = new byte[chachaEncrypted.length];
        chachaEngine.processBytes(chachaEncrypted, 0, chachaEncrypted.length, decryptedBytes, 0);

        String decryptedMessage = new String(decryptedBytes, "UTF-8");
        Log.d("MessageEncryptor", "Decrypted message: " + decryptedMessage);
        return decryptedMessage;
    }

    private static byte[] generateKey() {
        byte[] key = new byte[KEY_LENGTH];
        new SecureRandom().nextBytes(key);
        return key;
    }

    private static byte[] generateNonceFromUserInput(String email, String phoneNumber) {
        byte[] nonce = new byte[NONCE_LENGTH];
        String emailPart = email.substring(0, Math.min(4, email.length()));
        String phonePart = phoneNumber.substring(Math.max(0, phoneNumber.length() - 4));
        byte[] emailBytes = emailPart.getBytes();
        byte[] phoneBytes = phonePart.getBytes();
        System.arraycopy(emailBytes, 0, nonce, 0, Math.min(emailBytes.length, 4));
        System.arraycopy(phoneBytes, 0, nonce, 4, Math.min(phoneBytes.length, 4));
        return nonce; // 8 bytes from email (4) + phone (4)
    }

    private static byte[] generateIV() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}