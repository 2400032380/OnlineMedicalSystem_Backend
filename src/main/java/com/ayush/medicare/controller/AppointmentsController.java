package com.ayush.medicare.controller;

import com.ayush.medicare.entity.AppointmentEntity;
import com.ayush.medicare.repository.AppointmentRepository;
import com.ayush.medicare.service.PaymentSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentsController {

    private final AppointmentRepository appointmentRepository;
    private final PaymentSessionService paymentSessionService;

    public AppointmentsController(AppointmentRepository appointmentRepository, PaymentSessionService paymentSessionService) {
        this.appointmentRepository = appointmentRepository;
        this.paymentSessionService = paymentSessionService;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String patientEmail,
                                          @RequestParam(required = false) String doctorEmail,
                                          @RequestParam(required = false) String includeUnassigned) {
        List<AppointmentEntity> items = appointmentRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (AppointmentEntity item : items) {
            if (patientEmail != null && !patientEmail.equals(item.getPatientEmail())) {
                continue;
            }

            if (doctorEmail != null) {
                boolean sameDoctor = doctorEmail.equals(item.getDoctorEmail());
                boolean allowUnassigned = "true".equals(includeUnassigned) && isBlank(item.getDoctorEmail());
                if (!sameDoctor && !allowUnassigned) {
                    continue;
                }
            }

            result.add(toResponse(item));
        }

        return result;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String patientName = str(body.get("patientName"));
        String patientEmail = str(body.get("patientEmail"));
        String doctorName = str(body.get("doctorName"));
        String date = str(body.get("date"));
        String time = str(body.get("time"));
        String paymentVerificationId = str(body.get("paymentVerificationId"));
        double consultationFee = toDouble(body.get("consultationFee"));

        if (patientName.isEmpty() || patientEmail.isEmpty() || doctorName.isEmpty() || date.isEmpty() || time.isEmpty()) {
            return badRequest("Missing required fields");
        }

        if (consultationFee <= 0) {
            return badRequest("Valid consultation fee is required");
        }

        if (paymentVerificationId.isEmpty()) {
            return badRequest("Verified payment reference is required before booking appointment");
        }

        PaymentSessionService.ConsumeResult verificationResult = paymentSessionService.consumeVerification(
                paymentVerificationId,
                consultationFee,
                patientEmail,
                "appointment"
        );

        if (!verificationResult.ok) {
            return badRequest(verificationResult.message);
        }

        PaymentSessionService.Payment payment = verificationResult.session.payment;

        AppointmentEntity appointment = new AppointmentEntity();
        appointment.setId(UUID.randomUUID().toString());
        appointment.setStatus("pending");
        appointment.setPatientName(patientName);
        appointment.setPatientEmail(patientEmail);
        appointment.setDoctorName(doctorName);
        appointment.setDoctorEmail(str(body.get("doctorEmail")));
        appointment.setSpecialty(String.valueOf(valueOrDefault(body.get("specialty"), "General")));
        appointment.setDate(date);
        appointment.setTime(time);
        appointment.setFee(consultationFee);
        appointment.setSymptoms(String.valueOf(valueOrDefault(body.get("symptoms"), "")));
        appointment.setReason(String.valueOf(valueOrDefault(body.get("symptoms"), "General Checkup")));
        appointment.setPatientPhone(String.valueOf(valueOrDefault(body.get("patientPhone"), "")));

        appointment.setPaymentStatus("paid");
        appointment.setPaymentMethod(payment.method);
        appointment.setPaymentReference(payment.paymentId);
        appointment.setPaymentAmount(consultationFee);
        appointment.setPaymentPaidAt(payment.paidAt);
        appointment.setPaymentVerificationId(paymentVerificationId);
        appointment.setPaymentProvider(payment.provider);

        AppointmentEntity saved = appointmentRepository.save(appointment);
        return ResponseEntity.status(201).body(toResponse(saved));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String status = str(body.get("status"));
        if (status.isEmpty()) {
            return badRequest("status is required");
        }

        Optional<AppointmentEntity> existing = appointmentRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Appointment not found"));
        }

        AppointmentEntity updated = existing.get();
        updated.setStatus(status);
        if (!str(body.get("doctorEmail")).isEmpty()) {
            updated.setDoctorEmail(str(body.get("doctorEmail")));
        }
        if (!str(body.get("doctorName")).isEmpty()) {
            updated.setDoctorName(str(body.get("doctorName")));
        }

        AppointmentEntity saved = appointmentRepository.save(updated);

        return ResponseEntity.ok(toResponse(saved));
    }

    private Map<String, Object> toResponse(AppointmentEntity item) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", item.getId());
        response.put("status", item.getStatus());
        response.put("patientName", item.getPatientName());
        response.put("patientEmail", item.getPatientEmail());
        response.put("doctorName", item.getDoctorName());
        response.put("doctorEmail", valueOrDefault(item.getDoctorEmail(), ""));
        response.put("specialty", valueOrDefault(item.getSpecialty(), "General"));
        response.put("date", item.getDate());
        response.put("time", item.getTime());
        response.put("fee", item.getFee());
        response.put("symptoms", valueOrDefault(item.getSymptoms(), ""));
        response.put("reason", valueOrDefault(item.getReason(), "General Checkup"));
        response.put("patientPhone", valueOrDefault(item.getPatientPhone(), ""));

        Map<String, Object> paymentMap = new LinkedHashMap<>();
        paymentMap.put("status", item.getPaymentStatus());
        paymentMap.put("method", item.getPaymentMethod());
        paymentMap.put("reference", item.getPaymentReference());
        paymentMap.put("amount", item.getPaymentAmount());
        paymentMap.put("paidAt", item.getPaymentPaidAt());
        paymentMap.put("verificationId", item.getPaymentVerificationId());
        paymentMap.put("provider", item.getPaymentProvider());
        response.put("payment", paymentMap);

        response.put("createdAt", item.getCreatedAt() == null ? null : item.getCreatedAt().toString());
        response.put("updatedAt", item.getUpdatedAt() == null ? null : item.getUpdatedAt().toString());
        return response;
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }

    private double toDouble(Object value) {
        if (value == null) return 0;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return 0;
        }
    }

    private Object valueOrDefault(Object value, Object fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof String && ((String) value).trim().isEmpty()) {
            return fallback;
        }
        return value;
    }
}
