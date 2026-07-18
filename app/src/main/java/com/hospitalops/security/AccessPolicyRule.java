package com.hospitalops.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Phase 3 Step 3.2: ACCESS_POLICY_RULES 테이블 매핑.
 *
 * <p>"누가(role_name) 어떤 화면(url_pattern)을 볼 수 있나(allowed)"를 데이터로 관리한다
 * (deliverable.md §3.3). {@link SecurityConfig}가 앱 기동 시 이 테이블 전체를 읽어
 * {@code authorizeHttpRequests}를 구성한다 — 하드코딩된 {@code hasRole(...)} 나열 대신
 * 이 테이블의 row 추가/수정만으로 정책을 바꿀 수 있게 하는 것이 설계 의도다.</p>
 */
@Entity
@Table(name = "ACCESS_POLICY_RULES")
public class AccessPolicyRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "rule_id")
	private Long ruleId;

	@Column(name = "role_name", nullable = false, length = 50)
	private String roleName;

	@Column(name = "url_pattern", nullable = false, length = 200)
	private String urlPattern;

	@Column(name = "allowed", nullable = false)
	private boolean allowed;

	@Column(name = "description", length = 255)
	private String description;

	protected AccessPolicyRule() {
		// JPA
	}

	public Long getRuleId() {
		return ruleId;
	}

	public String getRoleName() {
		return roleName;
	}

	public String getUrlPattern() {
		return urlPattern;
	}

	public boolean isAllowed() {
		return allowed;
	}

	public String getDescription() {
		return description;
	}
}
