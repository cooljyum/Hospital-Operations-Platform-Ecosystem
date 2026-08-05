package com.hospitalops.reception;

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
public class ReceptionController {

	private static final Set<String> VALID_STATUSES = Set.of("WAITING", "IN_PROGRESS", "DONE");

	private final ReceptionRepository receptionRepository;
	private final ReceptionPatientRepository receptionPatientRepository;

	public ReceptionController(ReceptionRepository receptionRepository,
			ReceptionPatientRepository receptionPatientRepository) {
		this.receptionRepository = receptionRepository;
		this.receptionPatientRepository = receptionPatientRepository;
	}

	@GetMapping("/reception")
	public String list(Model model) {
		List<Reception> receptions = receptionRepository.findAll(Sort.by(Sort.Direction.DESC, "receivedAt"));
		Map<Long, ReceptionPatient> patientsById = receptionPatientRepository.findByPatientIds(
				receptions.stream().map(Reception::getPatientId).toList());
		List<ReceptionListRow> rows = receptions.stream()
				.map(reception -> toListRow(reception, patientsById.get(reception.getPatientId())))
				.toList();
		model.addAttribute("rows", rows);
		return "reception/list";
	}

	@GetMapping("/reception/new")
	public String newForm(Model model) {
		return renderForm(model, false, null, null, null, null, null, null);
	}

	@PostMapping("/reception")
	public String create(
			@RequestParam String syntheticPatientNo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime receivedAt,
			@RequestParam(required = false) String chiefComplaint,
			@RequestParam String status,
			Authentication authentication,
			Model model) {

		ReceptionPatient patient = validatePatientAndStatus(syntheticPatientNo, status, model,
				false, null, receivedAt, chiefComplaint);
		if (patient == null) {
			return "reception/form";
		}
		receptionRepository.save(new Reception(patient.patientId(), receivedAt, chiefComplaint, status, authentication.getName()));
		return "redirect:/reception";
	}

	@GetMapping("/reception/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		Reception reception = findReception(id);
		String syntheticPatientNo = receptionPatientRepository.findByPatientId(reception.getPatientId())
				.map(ReceptionPatient::syntheticPatientNo)
				.orElse("");
		return renderForm(model, true, reception.getReceptionId(), syntheticPatientNo,
				reception.getReceivedAt(), reception.getChiefComplaint(), reception.getStatus(), null);
	}

	@PostMapping("/reception/{id}")
	public String update(
			@PathVariable Long id,
			@RequestParam String syntheticPatientNo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime receivedAt,
			@RequestParam(required = false) String chiefComplaint,
			@RequestParam String status,
			Model model) {

		Reception reception = findReception(id);
		ReceptionPatient patient = validatePatientAndStatus(syntheticPatientNo, status, model,
				true, id, receivedAt, chiefComplaint);
		if (patient == null) {
			return "reception/form";
		}
		reception.update(patient.patientId(), receivedAt, chiefComplaint, status);
		receptionRepository.save(reception);
		return "redirect:/reception";
	}

	@PostMapping("/reception/{id}/delete")
	public String delete(@PathVariable Long id) {
		receptionRepository.delete(findReception(id));
		return "redirect:/reception";
	}

	private Reception findReception(Long id) {
		return receptionRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "접수 정보를 찾을 수 없습니다."));
	}

	private ReceptionPatient validatePatientAndStatus(String syntheticPatientNo, String status, Model model,
			boolean isEdit, Long receptionId, LocalDateTime receivedAt, String chiefComplaint) {
		if (!VALID_STATUSES.contains(status)) {
			renderForm(model, isEdit, receptionId, syntheticPatientNo, receivedAt, chiefComplaint, status,
					"상태는 WAITING, IN_PROGRESS, DONE 중 하나여야 합니다.");
			return null;
		}
		return receptionPatientRepository.findBySyntheticPatientNo(syntheticPatientNo)
				.orElseGet(() -> {
					renderForm(model, isEdit, receptionId, syntheticPatientNo, receivedAt, chiefComplaint, status,
							"입력한 환자번호에 해당하는 환자를 찾을 수 없습니다.");
					return null;
				});
	}

	private String renderForm(Model model, boolean isEdit, Long receptionId, String syntheticPatientNo,
			LocalDateTime receivedAt, String chiefComplaint, String status, String errorMessage) {
		model.addAttribute("isEdit", isEdit);
		model.addAttribute("receptionId", receptionId);
		model.addAttribute("syntheticPatientNo", syntheticPatientNo);
		model.addAttribute("receivedAt", receivedAt);
		model.addAttribute("chiefComplaint", chiefComplaint);
		model.addAttribute("status", status);
		model.addAttribute("errorMessage", errorMessage);
		return "reception/form";
	}

	private ReceptionListRow toListRow(Reception reception, ReceptionPatient patient) {
		return new ReceptionListRow(reception,
				patient != null ? patient.syntheticPatientNo() : "-",
				patient != null && !patient.patientName().isBlank() ? patient.patientName() : "-");
	}
}
