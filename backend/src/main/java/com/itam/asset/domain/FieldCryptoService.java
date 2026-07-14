package com.itam.asset.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 字段加密服务（AES-256-GCM）。用于存储 storage_type=encrypted 的敏感字段（如授权密钥）。
 * 密文格式：前缀 "enc:" + Base64(IV[12] || ciphertext)。
 * 密钥由配置派生（缺省复用 JWT secret）。
 *
 * P0-5 安全约束：加密失败必须 fail-closed（抛异常），绝不回退为明文落库；
 * 解密失败返回密文（不泄露明文），由上层脱敏/隐藏处理。
 */
@Service
public class FieldCryptoService {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_LEN = 128;
    private static final String PREFIX = "enc:";

    private static final Logger log = LoggerFactory.getLogger(FieldCryptoService.class);
    private final SecretKeySpec key;

    public FieldCryptoService(@Value("${itam.field-encryption.secret:}") String secret,
                              @Value("${itam.jwt.secret}") String jwtSecret) {
        String seed = (secret == null || secret.isBlank()) ? jwtSecret : secret;
        this.key = new SecretKeySpec(sha256(seed), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LEN, iv));
            byte[] enc = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            // P0-5：fail-closed，禁止明文落库。
            throw new IllegalStateException("字段加密失败，拒绝以明文写入: " + e.getMessage(), e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
            return ciphertext;
        }
        try {
            byte[] out = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(out, 0, IV_LEN);
            byte[] enc = Arrays.copyOfRange(out, IV_LEN, out.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LEN, iv));
            byte[] dec = cipher.doFinal(enc);
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("字段解密失败，返回密文（不泄露明文）: {}", e.getMessage());
            return ciphertext;
        }
    }

    private byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
