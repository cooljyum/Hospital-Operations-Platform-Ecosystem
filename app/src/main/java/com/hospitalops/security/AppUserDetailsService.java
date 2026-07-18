package com.hospitalops.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Phase 3 Step 3.1: APP_USER/APP_USER_ROLE 조회 기반 DB 백엔드 UserDetailsService.
 *
 * <p>각 {@link AppRole#getRoleName()}을 그대로 {@link GrantedAuthority} 문자열로 매핑한다
 * (예: "ROLE_SYSTEM_ADMIN"). Step 3.2의 {@code hasAnyAuthority(...)} 기반 인가 규칙이
 * 이 authority 문자열과 정확히 일치해야 하므로, 역할명 네이밍(ROLE_ 접두어 포함)을 여기와
 * APP_ROLE 시드 데이터 양쪽에서 동일하게 유지한다.</p>
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

	private final AppUserRepository appUserRepository;

	public AppUserDetailsService(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		AppUser user = appUserRepository.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

		List<GrantedAuthority> authorities = user.getRoles().stream()
				.map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.getRoleName()))
				.toList();

		return User.withUsername(user.getUsername())
				.password(user.getPasswordHash())
				.disabled(!user.isEnabled())
				.authorities(authorities)
				.build();
	}
}
