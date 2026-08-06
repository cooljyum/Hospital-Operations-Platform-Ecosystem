package com.hospitalops.web;

import com.hospitalops.admission.Admission;
import com.hospitalops.admission.AdmissionRepository;
import com.hospitalops.appointment.Appointment;
import com.hospitalops.appointment.AppointmentRepository;
import com.hospitalops.examroom.ExamRoomSessionRepository;
import com.hospitalops.insuranceclaim.InsuranceClaim;
import com.hospitalops.insuranceclaim.InsuranceClaimRepository;
import com.hospitalops.laborder.LabOrderRepository;
import com.hospitalops.nursing.NursingRecordRepository;
import com.hospitalops.payment.PaymentRepository;
import com.hospitalops.reception.Reception;
import com.hospitalops.reception.ReceptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTests {

	@Mock
	private ReceptionRepository receptionRepository;
	@Mock
	private AppointmentRepository appointmentRepository;
	@Mock
	private ExamRoomSessionRepository examRoomSessionRepository;
	@Mock
	private NursingRecordRepository nursingRecordRepository;
	@Mock
	private LabOrderRepository labOrderRepository;
	@Mock
	private PaymentRepository paymentRepository;
	@Mock
	private AdmissionRepository admissionRepository;
	@Mock
	private InsuranceClaimRepository insuranceClaimRepository;

	@Test
	void buildsAggregateCountsWithoutExposingIndividualRecords() {
		LocalDateTime now = LocalDateTime.now();
		Reception todayReception = new Reception(1L, now, "", "RECEIVED", "registrar");
		ReflectionTestUtils.setField(todayReception, "createdAt", now.minusHours(1));
		Reception previousReception = new Reception(2L, now.minusDays(1), "", "RECEIVED", "registrar");
		ReflectionTestUtils.setField(previousReception, "createdAt", now.minusHours(25));
		Appointment todayAppointment = new Appointment(1L, now, "내과", "BOOKED", "", "registrar");
		Admission admitted = new Admission(1L, "A", "101", now, null, "", "ADMITTED", "nurse");
		Admission discharged = new Admission(2L, "A", "102", now, now, "", "DISCHARGED", "nurse");
		InsuranceClaim submitted = new InsuranceClaim(1L, "보험사", BigDecimal.ONE, "SUBMITTED", now, null, "registrar");
		InsuranceClaim completed = new InsuranceClaim(2L, "보험사", BigDecimal.ONE, "COMPLETED", now, now, "registrar");

		given(receptionRepository.findAll()).willReturn(List.of(todayReception, previousReception));
		given(appointmentRepository.findAll()).willReturn(List.of(todayAppointment));
		given(examRoomSessionRepository.findAll()).willReturn(List.of());
		given(nursingRecordRepository.findAll()).willReturn(List.of());
		given(labOrderRepository.findAll()).willReturn(List.of());
		given(paymentRepository.findAll()).willReturn(List.of());
		given(admissionRepository.findAll()).willReturn(List.of(admitted, discharged));
		given(insuranceClaimRepository.findAll()).willReturn(List.of(submitted, completed));
		given(receptionRepository.count()).willReturn(2L);

		DashboardStats stats = dashboardService().buildStats();

		assertThat(stats.todayReceptionCount()).isEqualTo(1);
		assertThat(stats.todayAppointmentCount()).isEqualTo(1);
		assertThat(stats.admittedPatientCount()).isEqualTo(1);
		assertThat(stats.pendingClaimCount()).isEqualTo(1);
		assertThat(stats.moduleCounts()).containsExactly(
				new DashboardStats.ModuleCount("접수", 2),
				new DashboardStats.ModuleCount("예약", 0),
				new DashboardStats.ModuleCount("진료실", 0),
				new DashboardStats.ModuleCount("간호", 0),
				new DashboardStats.ModuleCount("검사", 0),
				new DashboardStats.ModuleCount("수납", 0),
				new DashboardStats.ModuleCount("입원", 0),
				new DashboardStats.ModuleCount("보험청구", 0));
		assertThat(stats.recentActivity()).containsExactly(new DashboardStats.ModuleCount("접수", 1));
	}

	private DashboardService dashboardService() {
		return new DashboardService(receptionRepository, appointmentRepository, examRoomSessionRepository,
				nursingRecordRepository, labOrderRepository, paymentRepository, admissionRepository,
				insuranceClaimRepository);
	}
}
