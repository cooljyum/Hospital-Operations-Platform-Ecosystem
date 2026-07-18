package com.hospitalops.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Phase 3 Step 3.1: APP_ROLE 테이블 매핑.
 *
 * <p>Step 3.2에서 RBAC 5역할(ROLE_PHYSICIAN/ROLE_NURSE/ROLE_REGISTRAR/ROLE_SYSTEM_ADMIN/
 * ROLE_AUDITOR)이 이 테이블에 모두 채워진다. role_name 값이 곧 Spring Security
 * GrantedAuthority 문자열로 그대로 쓰인다({@link AppUserDetailsService} 참고).</p>
 */
@Entity
@Table(name = "APP_ROLE", uniqueConstraints = {
		@UniqueConstraint(name = "uq_app_role_name", columnNames = {"role_name"})
})
public class AppRole {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "role_id")
	private Long roleId;

	@Column(name = "role_name", nullable = false, length = 50)
	private String roleName;

	protected AppRole() {
		// JPA
	}

	public AppRole(String roleName) {
		this.roleName = roleName;
	}

	public Long getRoleId() {
		return roleId;
	}

	public String getRoleName() {
		return roleName;
	}
}
