package com.hospitalops.stats;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Phase 4 Step 4.4: {@link PatientStatsRepository}의 원값 집계에 k=5 small-cell
 * suppression을 적용해 응답/화면에 노출 가능한 형태로 변환하는 유일한 경로.
 *
 * <p>컨트롤러는 이 서비스만 호출한다 — {@link PatientStatsRepository}를 직접 참조하지
 * 않는다. 그래서 억제 로직을 우회해 원값이 그대로 응답에 나가는 경로가 없다.</p>
 */
@Service
public class PatientStatsService {

	private final PatientStatsRepository repository;

	public PatientStatsService(PatientStatsRepository repository) {
		this.repository = repository;
	}

	public List<PatientGenderAgeBandCount> genderByAgeBandCounts() {
		return repository.rawCountsByGenderAndAgeBand().stream()
				.map(raw -> new PatientGenderAgeBandCount(
						raw.gender(), raw.ageBandStart(), SuppressedCell.of(raw.rawCount())))
				.toList();
	}
}
