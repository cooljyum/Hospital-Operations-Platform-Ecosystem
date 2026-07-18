package com.hospitalops.security;

/**
 * Phase 3 Step 3.3: break-glass 세션 플래그의 속성명을 한 곳에서 관리한다.
 *
 * <p>{@code com.hospitalops.breakglass.BreakGlassController}(요청 측)와
 * {@code SecurityConfig}(검사 측)가 같은 문자열 상수를 참조해야 하는데, breakglass
 * 패키지가 security 패키지에 의존하는 방향(자연스러운 계층)만 유지하기 위해 이 상수를
 * security 패키지에 둔다.</p>
 */
public final class BreakGlassSessionAttributes {

	/** 세션에 이 속성이 TRUE로 설정돼 있으면 /emergency/** 접근이 한시적으로 허용된다. */
	public static final String GRANTED = "breakGlassGranted";

	private BreakGlassSessionAttributes() {
	}
}
