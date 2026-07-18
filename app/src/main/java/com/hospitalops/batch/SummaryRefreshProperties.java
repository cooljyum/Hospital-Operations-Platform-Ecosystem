package com.hospitalops.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase 6 Step 6.1: PATIENT_VISIT_SUMMARY 물리 요약 테이블을 전체 재계산하는
 * summaryRefreshJob 실행 여부 설정.
 *
 * <p>{@code sync.job.enabled}(SyncJobProperties)와 같은 이유로 기본값은 비활성이다 —
 * 평소 앱 기동/테스트에서 이 배치가 자동 실행되지 않게 한다. 실제 재계산은
 * {@code --summary.refresh.job.enabled=true}(또는 SUMMARY_REFRESH_JOB_ENABLED
 * 환경변수)로 명시적으로 켠다.</p>
 */
@Component
@ConfigurationProperties(prefix = "summary.refresh.job")
public class SummaryRefreshProperties {

	private boolean enabled = false;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
