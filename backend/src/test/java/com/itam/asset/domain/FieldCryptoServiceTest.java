package com.itam.asset.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0-5 字段加密服务单元测试：验证加密非明文、可解密往返、fail-closed 不泄露明文。
 */
class FieldCryptoServiceTest {

    private final FieldCryptoService svc = new FieldCryptoService("secret-a", "jwt-a");
    private final FieldCryptoService other = new FieldCryptoService("secret-b", "jwt-b");

    @Test
    void encrypt_returns_prefixed_non_plaintext() {
        String enc = svc.encrypt("LIC-12345");
        assertThat(enc).startsWith("enc:");
        assertThat(enc).isNotEqualTo("LIC-12345");
        assertThat(enc).doesNotContain("LIC-12345");
    }

    @Test
    void decrypt_roundtrip() {
        String enc = svc.encrypt("hello-world");
        assertThat(svc.decrypt(enc)).isEqualTo("hello-world");
    }

    @Test
    void decrypt_non_prefixed_returns_as_is() {
        assertThat(svc.decrypt("plain-text-value")).isEqualTo("plain-text-value");
    }

    @Test
    void wrong_key_does_not_reveal_plaintext() {
        String enc = svc.encrypt("secret-value");
        // 使用不同密钥解密：GCM tag 校验失败，返回密文本身（不泄露明文）。
        String dec = other.decrypt(enc);
        assertThat(dec).isNotEqualTo("secret-value");
        assertThat(dec).startsWith("enc:");
    }

    @Test
    void null_plaintext_returns_null() {
        assertThat(svc.encrypt(null)).isNull();
    }
}
