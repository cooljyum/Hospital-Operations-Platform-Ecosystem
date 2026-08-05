package com.hospitalops.laborder;

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
public class LabOrderController {

	private static final Set<String> VALID_STATUSES = Set.of("ORDERED", "IN_PROGRESS", "COMPLETED");

	private final LabOrderRepository labOrderRepository;
	private final LabOrderPatientRepository labOrderPatientRepository;

	public LabOrderController(LabOrderRepository labOrderRepository,
			LabOrderPatientRepository labOrderPatientRepository) {
		this.labOrderRepository = labOrderRepository;
		this.labOrderPatientRepository = labOrderPatientRepository;
	}

	@GetMapping("/lab-order")
	public String list(Model model) {
		List<LabOrder> labOrders = labOrderRepository.findAll(Sort.by(Sort.Direction.DESC, "orderedAt"));
		Map<Long, LabOrderPatient> patientsById = labOrderPatientRepository.findByPatientIds(
				labOrders.stream().map(LabOrder::getPatientId).toList());
		List<LabOrderListRow> rows = labOrders.stream()
				.map(labOrder -> toListRow(labOrder, patientsById.get(labOrder.getPatientId())))
				.toList();
		model.addAttribute("rows", rows);
		return "lab-order/list";
	}

	@GetMapping("/lab-order/new")
	public String newForm(Model model) {
		return renderForm(model, false, null, null, null, null, null, null, null);
	}

	@PostMapping("/lab-order")
	public String create(
			@RequestParam String syntheticPatientNo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime orderedAt,
			@RequestParam String testName,
			@RequestParam String status,
			@RequestParam(required = false) String resultSummary,
			Authentication authentication,
			Model model) {

		LabOrderPatient patient = validatePatientAndStatus(syntheticPatientNo, status, model,
				false, null, orderedAt, testName, resultSummary);
		if (patient == null) {
			return "lab-order/form";
		}
		labOrderRepository.save(new LabOrder(patient.patientId(), orderedAt, authentication.getName(), testName, status,
				resultSummary));
		return "redirect:/lab-order";
	}

	@GetMapping("/lab-order/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		LabOrder labOrder = findLabOrder(id);
		String syntheticPatientNo = labOrderPatientRepository.findByPatientId(labOrder.getPatientId())
				.map(LabOrderPatient::syntheticPatientNo)
				.orElse("");
		return renderForm(model, true, labOrder.getLabOrderId(), syntheticPatientNo, labOrder.getOrderedAt(),
				labOrder.getTestName(), labOrder.getStatus(), labOrder.getResultSummary(), null);
	}

	@PostMapping("/lab-order/{id}")
	public String update(
			@PathVariable Long id,
			@RequestParam String syntheticPatientNo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime orderedAt,
			@RequestParam String testName,
			@RequestParam String status,
			@RequestParam(required = false) String resultSummary,
			Model model) {

		LabOrder labOrder = findLabOrder(id);
		LabOrderPatient patient = validatePatientAndStatus(syntheticPatientNo, status, model,
				true, id, orderedAt, testName, resultSummary);
		if (patient == null) {
			return "lab-order/form";
		}
		labOrder.update(patient.patientId(), orderedAt, testName, status, resultSummary);
		labOrderRepository.save(labOrder);
		return "redirect:/lab-order";
	}

	@PostMapping("/lab-order/{id}/delete")
	public String delete(@PathVariable Long id) {
		labOrderRepository.delete(findLabOrder(id));
		return "redirect:/lab-order";
	}

	private LabOrder findLabOrder(Long id) {
		return labOrderRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "검사 오더 정보를 찾을 수 없습니다."));
	}

	private LabOrderPatient validatePatientAndStatus(String syntheticPatientNo, String status, Model model,
			boolean isEdit, Long labOrderId, LocalDateTime orderedAt, String testName, String resultSummary) {
		if (!VALID_STATUSES.contains(status)) {
			renderForm(model, isEdit, labOrderId, syntheticPatientNo, orderedAt, testName, status, resultSummary,
					"상태는 ORDERED, IN_PROGRESS, COMPLETED 중 하나여야 합니다.");
			return null;
		}
		return labOrderPatientRepository.findBySyntheticPatientNo(syntheticPatientNo)
				.orElseGet(() -> {
					renderForm(model, isEdit, labOrderId, syntheticPatientNo, orderedAt, testName, status, resultSummary,
							"입력한 환자번호에 해당하는 환자를 찾을 수 없습니다.");
					return null;
				});
	}

	private String renderForm(Model model, boolean isEdit, Long labOrderId, String syntheticPatientNo,
			LocalDateTime orderedAt, String testName, String status, String resultSummary, String errorMessage) {
		model.addAttribute("isEdit", isEdit);
		model.addAttribute("labOrderId", labOrderId);
		model.addAttribute("syntheticPatientNo", syntheticPatientNo);
		model.addAttribute("orderedAt", orderedAt);
		model.addAttribute("testName", testName);
		model.addAttribute("status", status);
		model.addAttribute("resultSummary", resultSummary);
		model.addAttribute("errorMessage", errorMessage);
		return "lab-order/form";
	}

	private LabOrderListRow toListRow(LabOrder labOrder, LabOrderPatient patient) {
		return new LabOrderListRow(labOrder,
				patient != null ? patient.syntheticPatientNo() : "-",
				patient != null && !patient.patientName().isBlank() ? patient.patientName() : "-");
	}
}
