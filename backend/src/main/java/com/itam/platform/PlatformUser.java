package com.itam.platform;

import com.itam.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * 平台用户（平台级，无 tenant_id）。可关联多个租户(tenant_user)。
 * is_platform_admin 标识平台管理员，平台管理员无租户上下文、拥有全部权限。
 */
@Entity
@Table(name = "platform_user")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class PlatformUser extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    private String email;
    private String phone;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE / DISABLED

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "is_platform_admin", nullable = false)
    private boolean platformAdmin = false;
}
