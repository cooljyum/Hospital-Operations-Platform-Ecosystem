package com.hospitalops.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Phase 3 Step 3.1: APP_USER 테이블 매핑. 로그인 계정 + APP_USER_ROLE 조인 테이블을 통한
 * 역할 매핑을 함께 담는다({@link AppRole}). password_hash는 항상 BCrypt로 인코딩된 값만
 * 저장한다 — 평문은 어디에도 들어오지 않는다({@link SecurityDataSeeder} 참고).
 */
@Entity
@Table(name = "APP_USER", uniqueConstraints = {
		@UniqueConstraint(name = "uq_app_user_username", columnNames = {"username"})
})
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "username", nullable = false, length = 50)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(name = "enabled", nullable = false)
	private boolean enabled = true;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(
			name = "APP_USER_ROLE",
			joinColumns = @JoinColumn(name = "user_id"),
			inverseJoinColumns = @JoinColumn(name = "role_id")
	)
	private Set<AppRole> roles = new HashSet<>();

	protected AppUser() {
		// JPA
	}

	public AppUser(String username, String passwordHash, boolean enabled) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.enabled = enabled;
	}

	public void addRole(AppRole role) {
		this.roles.add(role);
	}

	public Long getUserId() {
		return userId;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Set<AppRole> getRoles() {
		return roles;
	}
}
