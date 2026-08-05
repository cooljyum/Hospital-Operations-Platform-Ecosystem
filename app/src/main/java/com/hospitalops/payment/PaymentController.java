package com.hospitalops.payment;

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
public class PaymentController {

	private static final Set<String> VALID_STATUSES = Set.of("PAID", "REFUNDED", "PENDING");

	private final PaymentRepository paymentRepository;
	private final PaymentPatientRepository paymentPatientRepository;

	public PaymentController(PaymentRepository paymentRepository, PaymentPatientRepository paymentPatientRepository) {
		this.paymentRepository = paymentRepository;
		this.paymentPatientRepository = paymentPatientRepository;
	}

	@GetMapping("/payment")
	public String list(Model model) {
		List<Payment> payments = paymentRepository.findAll(Sort.by(Sort.Direction.DESC, "paidAt"));
		Map<Long, PaymentPatient> patientsById = paymentPatientRepository.findByPatientIds(
				payments.stream().map(Payment::getPatientId).toList());
		List<PaymentListRow> rows = payments.stream()
				.map(payment -> toListRow(payment, patientsById.get(payment.getPatientId())))
				.toList();
		model.addAttribute("rows", rows);
		return "payment/list";
	}

	@GetMapping("/payment/new")
	public String newForm(Model model) {
		return renderForm(model, false, null, null, null, null, null, null, null);
	}

	@PostMapping("/payment")
	public String create(
			@RequestParam String syntheticPatientNo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime paidAt,
			@RequestParam BigDecimal amount,
			@RequestParam String paymentMethod,
			@RequestParam String status,
			Authentication authentication,
			Model model) {

		PaymentPatient patient = validatePatientAndStatus(syntheticPatientNo, status, model,
				false, null, paidAt, amount, paymentMethod);
		if (patient == null) {
			return "payment/form";
		}
		paymentRepository.save(new Payment(patient.patientId(), paidAt, amount, paymentMethod, status,
				authentication.getName()));
		return "redirect:/payment";
	}

	@GetMapping("/payment/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		Payment payment = findPayment(id);
		String syntheticPatientNo = paymentPatientRepository.findByPatientId(payment.getPatientId())
				.map(PaymentPatient::syntheticPatientNo)
				.orElse("");
		return renderForm(model, true, payment.getPaymentId(), syntheticPatientNo, payment.getPaidAt(),
				payment.getAmount(), payment.getPaymentMethod(), payment.getStatus(), null);
	}

	@PostMapping("/payment/{id}")
	public String update(
			@PathVariable Long id,
			@RequestParam String syntheticPatientNo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime paidAt,
			@RequestParam BigDecimal amount,
			@RequestParam String paymentMethod,
			@RequestParam String status,
			Model model) {

		Payment payment = findPayment(id);
		PaymentPatient patient = validatePatientAndStatus(syntheticPatientNo, status, model,
				true, id, paidAt, amount, paymentMethod);
		if (patient == null) {
			return "payment/form";
		}
		payment.update(patient.patientId(), paidAt, amount, paymentMethod, status);
		paymentRepository.save(payment);
		return "redirect:/payment";
	}

	@PostMapping("/payment/{id}/delete")
	public String delete(@PathVariable Long id) {
		paymentRepository.delete(findPayment(id));
		return "redirect:/payment";
	}

	private Payment findPayment(Long id) {
		return paymentRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수납 정보를 찾을 수 없습니다."));
	}

	private PaymentPatient validatePatientAndStatus(String syntheticPatientNo, String status, Model model,
			boolean isEdit, Long paymentId, LocalDateTime paidAt, BigDecimal amount, String paymentMethod) {
		if (!VALID_STATUSES.contains(status)) {
			renderForm(model, isEdit, paymentId, syntheticPatientNo, paidAt, amount, paymentMethod, status,
					"상태는 PAID, REFUNDED, PENDING 중 하나여야 합니다.");
			return null;
		}
		return paymentPatientRepository.findBySyntheticPatientNo(syntheticPatientNo)
				.orElseGet(() -> {
					renderForm(model, isEdit, paymentId, syntheticPatientNo, paidAt, amount, paymentMethod, status,
							"입력한 환자번호에 해당하는 환자를 찾을 수 없습니다.");
					return null;
				});
	}

	private String renderForm(Model model, boolean isEdit, Long paymentId, String syntheticPatientNo,
			LocalDateTime paidAt, BigDecimal amount, String paymentMethod, String status, String errorMessage) {
		model.addAttribute("isEdit", isEdit);
		model.addAttribute("paymentId", paymentId);
		model.addAttribute("syntheticPatientNo", syntheticPatientNo);
		model.addAttribute("paidAt", paidAt);
		model.addAttribute("amount", amount);
		model.addAttribute("paymentMethod", paymentMethod);
		model.addAttribute("status", status);
		model.addAttribute("errorMessage", errorMessage);
		return "payment/form";
	}

	private PaymentListRow toListRow(Payment payment, PaymentPatient patient) {
		return new PaymentListRow(payment,
				patient != null ? patient.syntheticPatientNo() : "-",
				patient != null && !patient.patientName().isBlank() ? patient.patientName() : "-");
	}
}
