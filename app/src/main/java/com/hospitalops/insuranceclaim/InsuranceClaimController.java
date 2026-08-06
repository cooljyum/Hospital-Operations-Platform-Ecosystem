package com.hospitalops.insuranceclaim;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class InsuranceClaimController {

	private static final Set<String> VALID_STATUSES = Set.of("SUBMITTED", "APPROVED", "REJECTED");

	private final InsuranceClaimRepository insuranceClaimRepository;
	private final InsuranceClaimPatientRepository insuranceClaimPatientRepository;

	public InsuranceClaimController(InsuranceClaimRepository insuranceClaimRepository,
			InsuranceClaimPatientRepository insuranceClaimPatientRepository) {
		this.insuranceClaimRepository = insuranceClaimRepository;
		this.insuranceClaimPatientRepository = insuranceClaimPatientRepository;
	}

	@GetMapping("/insurance-claim")
	public String list(Model model) {
		List<InsuranceClaim> insuranceClaims = insuranceClaimRepository.findAll(
				Sort.by(Sort.Direction.DESC, "submittedAt"));
		Map<Long, InsuranceClaimPatient> patientsById = insuranceClaimPatientRepository.findByPatientIds(
				insuranceClaims.stream().map(InsuranceClaim::getPatientId).toList());
		List<InsuranceClaimListRow> rows = insuranceClaims.stream()
				.map(insuranceClaim -> toListRow(insuranceClaim, patientsById.get(insuranceClaim.getPatientId())))
				.toList();
		model.addAttribute("rows", rows);
		return "insurance-claim/list";
	}

	@GetMapping("/insurance-claim/new")
	public String newForm(Model model) {
		return renderForm(model, false, null, null, null, null, null, null, null, null);
	}

	@PostMapping("/insurance-claim")
	public String create(
			@RequestParam String syntheticPatientNo,
			@RequestParam String insurerName,
			@RequestParam BigDecimal claimAmount,
			@RequestParam String claimStatus,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime submittedAt,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime processedAt,
			Authentication authentication,
			Model model) {

		InsuranceClaimPatient patient = validatePatientAndStatus(syntheticPatientNo, claimStatus, model,
				false, null, insurerName, claimAmount, submittedAt, processedAt);
		if (patient == null) {
			return "insurance-claim/form";
		}
		insuranceClaimRepository.save(new InsuranceClaim(patient.patientId(), insurerName, claimAmount, claimStatus,
				submittedAt, processedAt, authentication.getName()));
		return "redirect:/insurance-claim";
	}

	@GetMapping("/insurance-claim/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		InsuranceClaim insuranceClaim = findInsuranceClaim(id);
		String syntheticPatientNo = insuranceClaimPatientRepository.findByPatientId(insuranceClaim.getPatientId())
				.map(InsuranceClaimPatient::syntheticPatientNo)
				.orElse("");
		return renderForm(model, true, insuranceClaim.getInsuranceClaimId(), syntheticPatientNo,
				insuranceClaim.getInsurerName(), insuranceClaim.getClaimAmount(), insuranceClaim.getSubmittedAt(),
				insuranceClaim.getProcessedAt(), insuranceClaim.getClaimStatus(), null);
	}

	@PostMapping("/insurance-claim/{id}")
	public String update(
			@PathVariable Long id,
			@RequestParam String syntheticPatientNo,
			@RequestParam String insurerName,
			@RequestParam BigDecimal claimAmount,
			@RequestParam String claimStatus,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime submittedAt,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime processedAt,
			Model model) {

		InsuranceClaim insuranceClaim = findInsuranceClaim(id);
		InsuranceClaimPatient patient = validatePatientAndStatus(syntheticPatientNo, claimStatus, model,
				true, id, insurerName, claimAmount, submittedAt, processedAt);
		if (patient == null) {
			return "insurance-claim/form";
		}
		insuranceClaim.update(patient.patientId(), insurerName, claimAmount, claimStatus, submittedAt, processedAt);
		insuranceClaimRepository.save(insuranceClaim);
		return "redirect:/insurance-claim";
	}

	@PostMapping("/insurance-claim/{id}/delete")
	public String delete(@PathVariable Long id) {
		insuranceClaimRepository.delete(findInsuranceClaim(id));
		return "redirect:/insurance-claim";
	}

	private InsuranceClaim findInsuranceClaim(Long id) {
		return insuranceClaimRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "보험청구 정보를 찾을 수 없습니다."));
	}

	private InsuranceClaimPatient validatePatientAndStatus(String syntheticPatientNo, String claimStatus, Model model,
			boolean isEdit, Long insuranceClaimId, String insurerName, BigDecimal claimAmount, LocalDateTime submittedAt,
			LocalDateTime processedAt) {
		if (!VALID_STATUSES.contains(claimStatus)) {
			renderForm(model, isEdit, insuranceClaimId, syntheticPatientNo, insurerName, claimAmount, submittedAt,
					processedAt, claimStatus, "청구상태는 SUBMITTED, APPROVED, REJECTED 중 하나여야 합니다.");
			return null;
		}
		return insuranceClaimPatientRepository.findBySyntheticPatientNo(syntheticPatientNo)
				.orElseGet(() -> {
					renderForm(model, isEdit, insuranceClaimId, syntheticPatientNo, insurerName, claimAmount, submittedAt,
							processedAt, claimStatus, "입력한 환자번호에 해당하는 환자를 찾을 수 없습니다.");
					return null;
				});
	}

	private String renderForm(Model model, boolean isEdit, Long insuranceClaimId, String syntheticPatientNo,
			String insurerName, BigDecimal claimAmount, LocalDateTime submittedAt, LocalDateTime processedAt,
			String claimStatus, String errorMessage) {
		model.addAttribute("isEdit", isEdit);
		model.addAttribute("insuranceClaimId", insuranceClaimId);
		model.addAttribute("syntheticPatientNo", syntheticPatientNo);
		model.addAttribute("insurerName", insurerName);
		model.addAttribute("claimAmount", claimAmount);
		model.addAttribute("submittedAt", submittedAt);
		model.addAttribute("processedAt", processedAt);
		model.addAttribute("claimStatus", claimStatus);
		model.addAttribute("errorMessage", errorMessage);
		return "insurance-claim/form";
	}

	private InsuranceClaimListRow toListRow(InsuranceClaim insuranceClaim, InsuranceClaimPatient patient) {
		return new InsuranceClaimListRow(insuranceClaim,
				patient != null ? patient.syntheticPatientNo() : "-",
				patient != null && !patient.patientName().isBlank() ? patient.patientName() : "-");
	}
}
