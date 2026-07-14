package com.itam.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 密码编码器测试：
 * 1) strength=10 的 BCrypt 能正确校验；
 * 2) 校验 Python(bcrypt) 生成的种子哈希 $2b$10$... 与 Spring Security BCryptPasswordEncoder 兼容
 *    （证明 V2 种子数据可在本系统登录时通过 matches）。
 */
class PasswordEncoderTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    @Test
    void encode_and_match() {
        String hash = encoder.encode("Platform@123");
        assertThat(hash).startsWith("$2a$10$");
        assertThat(encoder.matches("Platform@123", hash)).isTrue();
        assertThat(encoder.matches("wrong-password", hash)).isFalse();
    }

    @Test
    void seed_hash_from_python_is_compatible() {
        // V2__seed_platform_data.sql 中平台管理员种子哈希
        String platformAdminHash = "$2b$10$71JNMuRN6XF/eFo4p66FfOtLs6bdnxnFvDx6gx46bbNtIEUi3ooDu";
        // V2 中演示租户管理员种子哈希
        String tenantAdminHash = "$2b$10$SDt5rjYPvmg4v.TlXOuhVOc9jUd18BpLGAHIS.0eg/jovuOq0uBSy";

        assertThat(encoder.matches("Platform@123", platformAdminHash)).isTrue();
        assertThat(encoder.matches("Tenant@123", tenantAdminHash)).isTrue();
        assertThat(encoder.matches("NotThePassword", platformAdminHash)).isFalse();
    }
}
