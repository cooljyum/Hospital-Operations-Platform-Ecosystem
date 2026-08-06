package com.hospitalops.web;

import java.util.List;

/**
 * 대시보드에 표시할 집계 수치만 담는다. 개별 환자 식별 정보는 포함하지 않는다.
 */
public record DashboardStats(
		long todayReceptionCount,
		long todayAppointmentCount,
		long admittedPatientCount,
		long pendingClaimCount,
		List<ModuleCount> moduleCounts,
		List<ModuleCount> recentActivity) {

	public record ModuleCount(String label, long count) {
	}
}
