package com.hospitalops.admission;

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
public class AdmissionController {

	private static final Set<String> VALID_STATUSES = Set.of("ADMITTED", "DISCHARGED", "TRANSFERRED");

	private final AdmissionRepository admissionRepository;
	private final AdmissionPatientRepository admissionPatientRepository;

	public AdmissionController(AdmissionRepository admissionRepository,
			AdmissionPatientRepository admissionPatientRepository) {
		this.admissionRepository = admissionRepository;
		this.admissionPatientRepository = admissionPatientRepository;
	}

	@GetMapping("/admission")
	public String list(Model model) {
		List<Admission> admissions = admissionRepository.findAll(Sort.by(Sort.Direction.DESC, "admittedAt"));
		Map<Long, AdmissionPatient> patientsById = admissionPatientRepository.findByPatientIds(
				admissions.stream().map(Admission::getPatientId).toList());
		List<AdmissionListRow> rows = admissions.stream()
				.map(admission -> toListRow(admission, patientsById.get(admission.getPatientId())))
				.toList();
		model.addAttribute("rows", rows);
		return "admission/list";
	}

	@GetMapping("/admission/new")
	public String newForm(Model model) {
		return renderForm(model, false, null, null, null, null, null, null, null, null, null);
	}

	@PostMapping("/admission")
	public String create(
			@RequestParam String syntheticPatientNo,
			@RequestParam String ward,
			@RequestParam String bedNo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime admittedAt,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dischargedAt,
			@RequestParam(required = false) String reason,
			@RequestParam String status,
			Authentication authentication,
			Model model) {

		AdmissionPatient patient = validatePatientAndStatus(syntheticPatientNo, status, model,
				false, null, ward, bedNo, admittedAt, dischargedAt, reason);
		if (patient == null) {
			return "admission/form";
		}
		admissionRepository.save(new Admission(patient.patientId(), ward, bedNo, admittedAt, dischargedAt,
				reason, status, authentication.getName()));
		return "redirect:/admission";
	}

	@GetMapping("/admission/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		Admission admission = findAdmission(id);
		String syntheticPatientNo = admissionPatientRepository.findByPatientId(admission.getPatientId())
				.map(AdmissionPatient::syntheticPatientNo)
				.orElse("");
		return renderForm(model, true, admission.getAdmissionId(), syntheticPatientNo,
				admission.getWard(), admission.getBedNo(), admission.getAdmittedAt(), admission.getDischargedAt(),
				admission.getReason(), admission.getStatus(), null);
	}

	@PostMapping("/admission/{id}")
	public String update(
			@PathVariable Long id,
			@RequestParam String syntheticPatientNo,
			@RequestParam String ward,
			@RequestParam String bedNo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime admittedAt,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dischargedAt,
			@RequestParam(required = false) String reason,
			@RequestParam String status,
			Model model) {

		Admission admission = findAdmission(id);
		AdmissionPatient patient = validatePatientAndStatus(syntheticPatientNo, status, model,
				true, id, ward, bedNo, admittedAt, dischargedAt, reason);
		if (patient == null) {
			return "admission/form";
		}
		admission.update(patient.patientId(), ward, bedNo, admittedAt, dischargedAt, reason, status);
		admissionRepository.save(admission);
		return "redirect:/admission";
	}

	@PostMapping("/admission/{id}/delete")
	public String delete(@PathVariable Long id) {
		admissionRepository.delete(findAdmission(id));
		return "redirect:/admission";
	}

	private Admission findAdmission(Long id) {
		return admissionRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "입원 정보를 찾을 수 없습니다."));
	}

	private AdmissionPatient validatePatientAndStatus(String syntheticPatientNo, String status, Model model,
			boolean isEdit, Long admissionId, String ward, String bedNo, LocalDateTime admittedAt,
			LocalDateTime dischargedAt, String reason) {
		if (!VALID_STATUSES.contains(status)) {
			renderForm(model, isEdit, admissionId, syntheticPatientNo, ward, bedNo, admittedAt, dischargedAt, reason,
					status, "상태는 ADMITTED, DISCHARGED, TRANSFERRED 중 하나여야 합니다.");
			return null;
		}
		return admissionPatientRepository.findBySyntheticPatientNo(syntheticPatientNo)
				.orElseGet(() -> {
					renderForm(model, isEdit, admissionId, syntheticPatientNo, ward, bedNo, admittedAt, dischargedAt,
							reason, status, "입력한 환자번호에 해당하는 환자를 찾을 수 없습니다.");
					return null;
				});
	}

	private String renderForm(Model model, boolean isEdit, Long admissionId, String syntheticPatientNo,
			String ward, String bedNo, LocalDateTime admittedAt, LocalDateTime dischargedAt, String reason,
			String status, String errorMessage) {
		model.addAttribute("isEdit", isEdit);
		model.addAttribute("admissionId", admissionId);
		model.addAttribute("syntheticPatientNo", syntheticPatientNo);
		model.addAttribute("ward", ward);
		model.addAttribute("bedNo", bedNo);
		model.addAttribute("admittedAt", admittedAt);
		model.addAttribute("dischargedAt", dischargedAt);
		model.addAttribute("reason", reason);
		model.addAttribute("status", status);
		model.addAttribute("errorMessage", errorMessage);
		return "admission/form";
	}

	private AdmissionListRow toListRow(Admission admission, AdmissionPatient patient) {
		return new AdmissionListRow(admission,
				patient != null ? patient.syntheticPatientNo() : "-",
				patient != null && !patient.patientName().isBlank() ? patient.patientName() : "-");
	}
}
