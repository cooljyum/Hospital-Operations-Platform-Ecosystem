package com.hospitalops.nursing;

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

@Controller
public class NursingController {

	private final NursingRecordRepository nursingRecordRepository;
	private final NursingPatientRepository nursingPatientRepository;

	public NursingController(NursingRecordRepository nursingRecordRepository,
			NursingPatientRepository nursingPatientRepository) {
		this.nursingRecordRepository = nursingRecordRepository;
		this.nursingPatientRepository = nursingPatientRepository;
	}

	@GetMapping("/nursing")
	public String list(Model model) {
		List<NursingRecord> records = nursingRecordRepository.findAll(Sort.by(Sort.Direction.DESC, "recordedAt"));
		Map<Long, NursingPatient> patientsById = nursingPatientRepository.findByPatientIds(
				records.stream().map(NursingRecord::getPatientId).toList());
		List<NursingRecordListRow> rows = records.stream()
				.map(record -> toListRow(record, patientsById.get(record.getPatientId())))
				.toList();
		model.addAttribute("rows", rows);
		return "nursing/list";
	}

	@GetMapping("/nursing/new")
	public String newForm(Model model) {
		return renderForm(model, false, null, null, null, null, null, null, null, null);
	}

	@PostMapping("/nursing")
	public String create(
			@RequestParam String syntheticPatientNo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime recordedAt,
			@RequestParam(required = false) BigDecimal temperature,
			@RequestParam(required = false) String bloodPressure,
			@RequestParam(required = false) Integer pulse,
			@RequestParam(required = false) String notes,
			Authentication authentication,
			Model model) {

		NursingPatient patient = validatePatient(syntheticPatientNo, model, false, null, recordedAt, temperature,
				bloodPressure, pulse, notes);
		if (patient == null) {
			return "nursing/form";
		}
		nursingRecordRepository.save(new NursingRecord(patient.patientId(), recordedAt, authentication.getName(), temperature,
				bloodPressure, pulse, notes));
		return "redirect:/nursing";
	}

	@GetMapping("/nursing/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		NursingRecord record = findNursingRecord(id);
		String syntheticPatientNo = nursingPatientRepository.findByPatientId(record.getPatientId())
				.map(NursingPatient::syntheticPatientNo)
				.orElse("");
		return renderForm(model, true, record.getNursingRecordId(), syntheticPatientNo, record.getRecordedAt(),
				record.getTemperature(), record.getBloodPressure(), record.getPulse(), record.getNotes(), null);
	}

	@PostMapping("/nursing/{id}")
	public String update(
			@PathVariable Long id,
			@RequestParam String syntheticPatientNo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime recordedAt,
			@RequestParam(required = false) BigDecimal temperature,
			@RequestParam(required = false) String bloodPressure,
			@RequestParam(required = false) Integer pulse,
			@RequestParam(required = false) String notes,
			Model model) {

		NursingRecord record = findNursingRecord(id);
		NursingPatient patient = validatePatient(syntheticPatientNo, model, true, id, recordedAt, temperature,
				bloodPressure, pulse, notes);
		if (patient == null) {
			return "nursing/form";
		}
		record.update(patient.patientId(), recordedAt, temperature, bloodPressure, pulse, notes);
		nursingRecordRepository.save(record);
		return "redirect:/nursing";
	}

	@PostMapping("/nursing/{id}/delete")
	public String delete(@PathVariable Long id) {
		nursingRecordRepository.delete(findNursingRecord(id));
		return "redirect:/nursing";
	}

	private NursingRecord findNursingRecord(Long id) {
		return nursingRecordRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "간호 기록을 찾을 수 없습니다."));
	}

	private NursingPatient validatePatient(String syntheticPatientNo, Model model, boolean isEdit, Long nursingRecordId,
			LocalDateTime recordedAt, BigDecimal temperature, String bloodPressure, Integer pulse, String notes) {
		return nursingPatientRepository.findBySyntheticPatientNo(syntheticPatientNo)
				.orElseGet(() -> {
					renderForm(model, isEdit, nursingRecordId, syntheticPatientNo, recordedAt, temperature, bloodPressure, pulse,
							notes, "입력한 환자번호에 해당하는 환자를 찾을 수 없습니다.");
					return null;
				});
	}

	private String renderForm(Model model, boolean isEdit, Long nursingRecordId, String syntheticPatientNo,
			LocalDateTime recordedAt, BigDecimal temperature, String bloodPressure, Integer pulse, String notes,
			String errorMessage) {
		model.addAttribute("isEdit", isEdit);
		model.addAttribute("nursingRecordId", nursingRecordId);
		model.addAttribute("syntheticPatientNo", syntheticPatientNo);
		model.addAttribute("recordedAt", recordedAt);
		model.addAttribute("temperature", temperature);
		model.addAttribute("bloodPressure", bloodPressure);
		model.addAttribute("pulse", pulse);
		model.addAttribute("notes", notes);
		model.addAttribute("errorMessage", errorMessage);
		return "nursing/form";
	}

	private NursingRecordListRow toListRow(NursingRecord record, NursingPatient patient) {
		return new NursingRecordListRow(record,
				patient != null ? patient.syntheticPatientNo() : "-",
				patient != null && !patient.patientName().isBlank() ? patient.patientName() : "-");
	}
}
