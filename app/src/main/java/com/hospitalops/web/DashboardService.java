package com.hospitalops.web;

import com.hospitalops.admission.Admission;
import com.hospitalops.admission.AdmissionRepository;
import com.hospitalops.appointment.Appointment;
import com.hospitalops.appointment.AppointmentRepository;
import com.hospitalops.examroom.ExamRoomSession;
import com.hospitalops.examroom.ExamRoomSessionRepository;
import com.hospitalops.insuranceclaim.InsuranceClaim;
import com.hospitalops.insuranceclaim.InsuranceClaimRepository;
import com.hospitalops.laborder.LabOrder;
import com.hospitalops.laborder.LabOrderRepository;
import com.hospitalops.nursing.NursingRecord;
import com.hospitalops.nursing.NursingRecordRepository;
import com.hospitalops.payment.Payment;
import com.hospitalops.payment.PaymentRepository;
import com.hospitalops.reception.Reception;
import com.hospitalops.reception.ReceptionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * 기존 업무 모듈을 읽기 전용으로 조회해 대시보드용 집계 수치를 만든다.
 */
@Service
public class DashboardService {

	private final ReceptionRepository receptionRepository;
	private final AppointmentRepository appointmentRepository;
	private final ExamRoomSessionRepository examRoomSessionRepository;
	private final NursingRecordRepository nursingRecordRepository;
	private final LabOrderRepository labOrderRepository;
	private final PaymentRepository paymentRepository;
	private final AdmissionRepository admissionRepository;
	private final InsuranceClaimRepository insuranceClaimRepository;

	public DashboardService(ReceptionRepository receptionRepository,
			AppointmentRepository appointmentRepository,
			ExamRoomSessionRepository examRoomSessionRepository,
			NursingRecordRepository nursingRecordRepository,
			LabOrderRepository labOrderRepository,
			PaymentRepository paymentRepository,
			AdmissionRepository admissionRepository,
			InsuranceClaimRepository insuranceClaimRepository) {
		this.receptionRepository = receptionRepository;
		this.appointmentRepository = appointmentRepository;
		this.examRoomSessionRepository = examRoomSessionRepository;
		this.nursingRecordRepository = nursingRecordRepository;
		this.labOrderRepository = labOrderRepository;
		this.paymentRepository = paymentRepository;
		this.admissionRepository = admissionRepository;
		this.insuranceClaimRepository = insuranceClaimRepository;
	}

	public DashboardStats buildStats() {
		LocalDate today = LocalDate.now();
		LocalDateTime activitySince = LocalDateTime.now().minusHours(24);

		List<Reception> receptions = receptionRepository.findAll();
		List<Appointment> appointments = appointmentRepository.findAll();
		List<ExamRoomSession> examRoomSessions = examRoomSessionRepository.findAll();
		List<NursingRecord> nursingRecords = nursingRecordRepository.findAll();
		List<LabOrder> labOrders = labOrderRepository.findAll();
		List<Payment> payments = paymentRepository.findAll();
		List<Admission> admissions = admissionRepository.findAll();
		List<InsuranceClaim> insuranceClaims = insuranceClaimRepository.findAll();

		long todayReceptionCount = receptions.stream()
				.filter(reception -> isOnDate(reception.getReceivedAt(), today))
				.count();
		long todayAppointmentCount = appointments.stream()
				.filter(appointment -> isOnDate(appointment.getScheduledAt(), today))
				.count();
		long admittedPatientCount = admissions.stream()
				.filter(admission -> "ADMITTED".equals(admission.getStatus()))
				.count();
		long pendingClaimCount = insuranceClaims.stream()
				.filter(insuranceClaim -> "SUBMITTED".equals(insuranceClaim.getClaimStatus()))
				.count();

		List<DashboardStats.ModuleCount> moduleCounts = List.of(
				new DashboardStats.ModuleCount("접수", receptionRepository.count()),
				new DashboardStats.ModuleCount("예약", appointmentRepository.count()),
				new DashboardStats.ModuleCount("진료실", examRoomSessionRepository.count()),
				new DashboardStats.ModuleCount("간호", nursingRecordRepository.count()),
				new DashboardStats.ModuleCount("검사", labOrderRepository.count()),
				new DashboardStats.ModuleCount("수납", paymentRepository.count()),
				new DashboardStats.ModuleCount("입원", admissionRepository.count()),
				new DashboardStats.ModuleCount("보험청구", insuranceClaimRepository.count()));

		List<DashboardStats.ModuleCount> recentActivity = List.of(
				new DashboardStats.ModuleCount("접수", countRecent(receptions, Reception::getCreatedAt, activitySince)),
				new DashboardStats.ModuleCount("예약", countRecent(appointments, Appointment::getCreatedAt, activitySince)),
				new DashboardStats.ModuleCount("진료실", countRecent(examRoomSessions, ExamRoomSession::getCreatedAt, activitySince)),
				new DashboardStats.ModuleCount("간호", countRecent(nursingRecords, NursingRecord::getCreatedAt, activitySince)),
				new DashboardStats.ModuleCount("검사", countRecent(labOrders, LabOrder::getCreatedAt, activitySince)),
				new DashboardStats.ModuleCount("수납", countRecent(payments, Payment::getCreatedAt, activitySince)),
				new DashboardStats.ModuleCount("입원", countRecent(admissions, Admission::getCreatedAt, activitySince)),
				new DashboardStats.ModuleCount("보험청구", countRecent(insuranceClaims, InsuranceClaim::getCreatedAt, activitySince))).stream()
				.filter(moduleCount -> moduleCount.count() > 0)
				.sorted(Comparator.comparingLong(DashboardStats.ModuleCount::count).reversed())
				.toList();

		return new DashboardStats(todayReceptionCount, todayAppointmentCount, admittedPatientCount,
				pendingClaimCount, moduleCounts, recentActivity);
	}

	private boolean isOnDate(LocalDateTime dateTime, LocalDate date) {
		return dateTime != null && dateTime.toLocalDate().equals(date);
	}

	private <T> long countRecent(List<T> records, Function<T, LocalDateTime> createdAt,
			LocalDateTime activitySince) {
		return records.stream()
				.map(createdAt)
				.filter(dateTime -> dateTime != null && !dateTime.isBefore(activitySince))
				.count();
	}
}
