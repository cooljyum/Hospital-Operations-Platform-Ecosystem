package com.hospitalops.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase 2 Step 2.1: FHIR 동기화 배치(SyncJob) 실행 여부 설정.
 *
 * <p>{@code synthea.loader.enabled}와 같은 이유로 기본값은 비활성이다 — 평소 앱 기동/
 * 테스트에서 이 배치가 자동 실행되지 않게 한다. 실제 동기화는
 * {@code --sync.job.enabled=true}(또는 SYNC_JOB_ENABLED 환경변수)로 명시적으로 켠다.</p>
 */
@Component
@ConfigurationProperties(prefix = "sync.job")
public class SyncJobProperties {

	private boolean enabled = false;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
