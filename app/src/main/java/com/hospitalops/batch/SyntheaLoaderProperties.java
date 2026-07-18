package com.hospitalops.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Synthea CSV 적재 배치 설정. 절대경로를 코드에 하드코딩하지 않기 위해 환경마다
 * 달라질 수 있는 CSV 디렉터리 경로와 실행 여부를 프로퍼티로 노출한다.
 *
 * 기본값은 "비활성 + 경로 미지정"이다 — 앱을 평소처럼 기동/테스트할 때 이 로더가
 * 의도치 않게 실행되지 않도록 하기 위함. 실제 적재는
 * {@code --synthea.loader.enabled=true --synthea.loader.csv-dir=<경로>}
 * (또는 SYNTHEA_LOADER_ENABLED/SYNTHEA_LOADER_CSV_DIR 환경변수)로 명시적으로 켠다.
 */
@Component
@ConfigurationProperties(prefix = "synthea.loader")
public class SyntheaLoaderProperties {

	private boolean enabled = false;
	private String csvDir;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getCsvDir() {
		return csvDir;
	}

	public void setCsvDir(String csvDir) {
		this.csvDir = csvDir;
	}
}
