package com.hospitalops.appointment;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class AppointmentController {

	private static final Set<String> VALID_STATUSES = Set.of("SCHEDULED", "CANCELLED", "COMPLETED");

	private final AppointmentRepository appointmentRepository;
	private final AppointmentPatientRepository appointmentPatientRepository;

	public AppointmentController(AppointmentRepository appointmentRepository,
			AppointmentPatientRepository appointmentPatientRepository) {
		this.appointmentRepository = appointmentRepository;
		this.appointmentPatientRepository = appointmentPatientRepository;
	}

	@GetMapping("/appointment")
	public String list(Model model) {
		List<Appointment> appointments = appointmentRepository.findAll(Sort.by(Sort.Direction.DESC, "scheduledAt"));
		Map<Long, AppointmentPatient> patientsById = appointmentPatientRepository.findByPatientIds(
				appointments.stream().map(Appointment::getPatientId).toList());
		List<AppointmentListRow> rows = appointments.stream()
				.map(appointment -> toListRow(appointment, patientsById.get(appointment.getPatientId())))
				.toList();
		model.addAttribute("rows", rows);
		return "appointment/list";
	}

	@GetMapping("/appointment/new")
	public String newForm(Model model) {
		return renderForm(model, false, null, null, null, null, null, null, null);
	}

	@PostMapping("/appointment")
	public String create(
			@RequestParam String syntheticPatientNo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledAt,
			@RequestParam(required = false) String department,
			@RequestParam String status,
			@RequestParam(required = false) String memo,
			Authentication authentication,
			Model model) {

		AppointmentPatient patient = validatePatientAndStatus(syntheticPatientNo, status, model,
				false, null, scheduledAt, department, memo);
		if (patient == null) {
			return "appointment/form";
		}
		appointmentRepository.save(new Appointment(patient.patientId(), scheduledAt, department, status, memo,
				authentication.getName()));
		return "redirect:/appointment";
	}

	@GetMapping("/appointment/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		Appointment appointment = findAppointment(id);
		String syntheticPatientNo = appointmentPatientRepository.findByPatientId(appointment.getPatientId())
				.map(AppointmentPatient::syntheticPatientNo)
				.orElse("");
		return renderForm(model, true, appointment.getAppointmentId(), syntheticPatientNo,
				appointment.getScheduledAt(), appointment.getDepartment(), appointment.getStatus(), appointment.getMemo(), null);
	}

	@PostMapping("/appointment/{id}")
	public String update(
			@PathVariable Long id,
			@RequestParam String syntheticPatientNo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledAt,
			@RequestParam(required = false) String department,
			@RequestParam String status,
			@RequestParam(required = false) String memo,
			Model model) {

		Appointment appointment = findAppointment(id);
		AppointmentPatient patient = validatePatientAndStatus(syntheticPatientNo, status, model,
				true, id, scheduledAt, department, memo);
		if (patient == null) {
			return "appointment/form";
		}
		appointment.update(patient.patientId(), scheduledAt, department, status, memo);
		appointmentRepository.save(appointment);
		return "redirect:/appointment";
	}

	@PostMapping("/appointment/{id}/delete")
	public String delete(@PathVariable Long id) {
		appointmentRepository.delete(findAppointment(id));
		return "redirect:/appointment";
	}

	private Appointment findAppointment(Long id) {
		return appointmentRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "예약 정보를 찾을 수 없습니다."));
	}

	private AppointmentPatient validatePatientAndStatus(String syntheticPatientNo, String status, Model model,
			boolean isEdit, Long appointmentId, LocalDateTime scheduledAt, String department, String memo) {
		if (!VALID_STATUSES.contains(status)) {
			renderForm(model, isEdit, appointmentId, syntheticPatientNo, scheduledAt, department, status, memo,
					"상태는 SCHEDULED, CANCELLED, COMPLETED 중 하나여야 합니다.");
			return null;
		}
		return appointmentPatientRepository.findBySyntheticPatientNo(syntheticPatientNo)
				.orElseGet(() -> {
					renderForm(model, isEdit, appointmentId, syntheticPatientNo, scheduledAt, department, status, memo,
							"입력한 환자번호에 해당하는 환자를 찾을 수 없습니다.");
					return null;
				});
	}

	private String renderForm(Model model, boolean isEdit, Long appointmentId, String syntheticPatientNo,
			LocalDateTime scheduledAt, String department, String status, String memo, String errorMessage) {
		model.addAttribute("isEdit", isEdit);
		model.addAttribute("appointmentId", appointmentId);
		model.addAttribute("syntheticPatientNo", syntheticPatientNo);
		model.addAttribute("scheduledAt", scheduledAt);
		model.addAttribute("department", department);
		model.addAttribute("status", status);
		model.addAttribute("memo", memo);
		model.addAttribute("errorMessage", errorMessage);
		return "appointment/form";
	}

	private AppointmentListRow toListRow(Appointment appointment, AppointmentPatient patient) {
		return new AppointmentListRow(appointment,
				patient != null ? patient.syntheticPatientNo() : "-",
				patient != null && !patient.patientName().isBlank() ? patient.patientName() : "-");
	}
}
