package com.hospitalops.examroom;

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
public class ExamRoomController {

	private static final Set<String> VALID_STATUSES = Set.of("WAITING", "IN_PROGRESS", "DONE");

	private final ExamRoomSessionRepository examRoomSessionRepository;
	private final ExamRoomPatientRepository examRoomPatientRepository;

	public ExamRoomController(ExamRoomSessionRepository examRoomSessionRepository,
			ExamRoomPatientRepository examRoomPatientRepository) {
		this.examRoomSessionRepository = examRoomSessionRepository;
		this.examRoomPatientRepository = examRoomPatientRepository;
	}

	@GetMapping("/exam-room")
	public String list(Model model) {
		List<ExamRoomSession> sessions = examRoomSessionRepository.findAll(Sort.by(Sort.Direction.DESC, "startedAt"));
		Map<Long, ExamRoomPatient> patientsById = examRoomPatientRepository.findByPatientIds(
				sessions.stream().map(ExamRoomSession::getPatientId).toList());
		List<ExamRoomSessionListRow> rows = sessions.stream()
				.map(session -> toListRow(session, patientsById.get(session.getPatientId())))
				.toList();
		model.addAttribute("rows", rows);
		return "exam-room/list";
	}

	@GetMapping("/exam-room/new")
	public String newForm(Model model) {
		return renderForm(model, false, null, null, null, null, null, null, null, null);
	}

	@PostMapping("/exam-room")
	public String create(
			@RequestParam String syntheticPatientNo,
			@RequestParam String roomNo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startedAt,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endedAt,
			@RequestParam(required = false) String notes,
			@RequestParam String status,
			Authentication authentication,
			Model model) {

		ExamRoomPatient patient = validatePatientAndStatus(syntheticPatientNo, status, model,
				false, null, roomNo, startedAt, endedAt, notes);
		if (patient == null) {
			return "exam-room/form";
		}
		examRoomSessionRepository.save(new ExamRoomSession(patient.patientId(), roomNo, authentication.getName(), startedAt,
				endedAt, notes, status));
		return "redirect:/exam-room";
	}

	@GetMapping("/exam-room/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		ExamRoomSession session = findExamRoomSession(id);
		String syntheticPatientNo = examRoomPatientRepository.findByPatientId(session.getPatientId())
				.map(ExamRoomPatient::syntheticPatientNo)
				.orElse("");
		return renderForm(model, true, session.getExamRoomSessionId(), syntheticPatientNo, session.getRoomNo(),
				session.getStartedAt(), session.getEndedAt(), session.getNotes(), session.getStatus(), null);
	}

	@PostMapping("/exam-room/{id}")
	public String update(
			@PathVariable Long id,
			@RequestParam String syntheticPatientNo,
			@RequestParam String roomNo,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startedAt,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endedAt,
			@RequestParam(required = false) String notes,
			@RequestParam String status,
			Model model) {

		ExamRoomSession session = findExamRoomSession(id);
		ExamRoomPatient patient = validatePatientAndStatus(syntheticPatientNo, status, model,
				true, id, roomNo, startedAt, endedAt, notes);
		if (patient == null) {
			return "exam-room/form";
		}
		session.update(patient.patientId(), roomNo, startedAt, endedAt, notes, status);
		examRoomSessionRepository.save(session);
		return "redirect:/exam-room";
	}

	@PostMapping("/exam-room/{id}/delete")
	public String delete(@PathVariable Long id) {
		examRoomSessionRepository.delete(findExamRoomSession(id));
		return "redirect:/exam-room";
	}

	private ExamRoomSession findExamRoomSession(Long id) {
		return examRoomSessionRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "진료실 정보를 찾을 수 없습니다."));
	}

	private ExamRoomPatient validatePatientAndStatus(String syntheticPatientNo, String status, Model model,
			boolean isEdit, Long examRoomSessionId, String roomNo, LocalDateTime startedAt, LocalDateTime endedAt,
			String notes) {
		if (!VALID_STATUSES.contains(status)) {
			renderForm(model, isEdit, examRoomSessionId, syntheticPatientNo, roomNo, startedAt, endedAt, notes, status,
					"상태는 WAITING, IN_PROGRESS, DONE 중 하나여야 합니다.");
			return null;
		}
		return examRoomPatientRepository.findBySyntheticPatientNo(syntheticPatientNo)
				.orElseGet(() -> {
					renderForm(model, isEdit, examRoomSessionId, syntheticPatientNo, roomNo, startedAt, endedAt, notes, status,
							"입력한 환자번호에 해당하는 환자를 찾을 수 없습니다.");
					return null;
				});
	}

	private String renderForm(Model model, boolean isEdit, Long examRoomSessionId, String syntheticPatientNo,
			String roomNo, LocalDateTime startedAt, LocalDateTime endedAt, String notes, String status, String errorMessage) {
		model.addAttribute("isEdit", isEdit);
		model.addAttribute("examRoomSessionId", examRoomSessionId);
		model.addAttribute("syntheticPatientNo", syntheticPatientNo);
		model.addAttribute("roomNo", roomNo);
		model.addAttribute("startedAt", startedAt);
		model.addAttribute("endedAt", endedAt);
		model.addAttribute("notes", notes);
		model.addAttribute("status", status);
		model.addAttribute("errorMessage", errorMessage);
		return "exam-room/form";
	}

	private ExamRoomSessionListRow toListRow(ExamRoomSession session, ExamRoomPatient patient) {
		return new ExamRoomSessionListRow(session,
				patient != null ? patient.syntheticPatientNo() : "-",
				patient != null && !patient.patientName().isBlank() ? patient.patientName() : "-");
	}
}
