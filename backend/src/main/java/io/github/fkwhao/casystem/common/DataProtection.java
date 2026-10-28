package io.github.fkwhao.casystem.common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DataProtection {
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] CHECK_VALUE = "CA system storage key v1".getBytes(StandardCharsets.UTF_8);
    private final String configuredKey;
    private final StorageStateMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public DataProtection(@Value("${ca.storage-key:}") String configuredKey, StorageStateMapper mapper) {
        this.configuredKey = configuredKey;
        this.mapper = mapper;
    }

    private SecretKeySpec key() {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw ApiException.unavailable("请先配置CA_STORAGE_KEY");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(configuredKey);
            if (bytes.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException ex) {
            throw ApiException.unavailable("CA_STORAGE_KEY须为32字节密钥的Base64编码");
        }
    }

    private byte[] encryptRaw(byte[] plaintext, SecretKeySpec secret) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secret, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(1 + nonce.length + encrypted.length)
                    .put((byte) 1).put(nonce).put(encrypted).array();
        } catch (GeneralSecurityException ex) {
            throw ApiException.unavailable("数据加密失败");
        }
    }

    private byte[] decryptRaw(byte[] sealed, SecretKeySpec secret) {
        if (sealed == null || sealed.length < 1 + NONCE_LENGTH + 16 || sealed[0] != 1) {
            throw ApiException.unavailable("加密数据格式错误");
        }
        try {
            byte[] nonce = java.util.Arrays.copyOfRange(sealed, 1, 1 + NONCE_LENGTH);
            byte[] ciphertext = java.util.Arrays.copyOfRange(sealed, 1 + NONCE_LENGTH, sealed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secret, new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException ex) {
            throw ApiException.unavailable("数据无法解密，请检查CA_STORAGE_KEY");
        }
    }

    private SecretKeySpec checkedKey() {
        SecretKeySpec secret = key();
        var records = mapper.keyCheck();
        if (records.isEmpty()) {
            Integer existing = mapper.countApplications();
            if (existing != null && existing > 0) {
                throw ApiException.unavailable("存储密钥校验记录缺失，请恢复原有数据与密钥");
            }
            mapper.insertKeyCheck(encryptRaw(CHECK_VALUE, secret));
            records = mapper.keyCheck();
        }
        if (records.isEmpty() || !java.security.MessageDigest.isEqual(CHECK_VALUE, decryptRaw(records.get(0), secret))) {
            throw ApiException.unavailable("存储密钥校验失败，请检查CA_STORAGE_KEY");
        }
        return secret;
    }

    public byte[] encrypt(byte[] plaintext) { return encryptRaw(plaintext, checkedKey()); }
    public byte[] decrypt(byte[] sealed) { return decryptRaw(sealed, checkedKey()); }

    public byte[] encryptText(String text) { return encrypt(text.getBytes(StandardCharsets.UTF_8)); }
    public String decryptText(byte[] data) { return new String(decrypt(data), StandardCharsets.UTF_8); }
}
