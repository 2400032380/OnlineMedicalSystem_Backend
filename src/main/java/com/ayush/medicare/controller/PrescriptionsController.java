package com.ayush.medicare.controller;

import com.ayush.medicare.service.InMemoryDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/prescriptions")
public class PrescriptionsController {

    private final InMemoryDataService dataService;

    public PrescriptionsController(InMemoryDataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String patientEmail,
                                          @RequestParam(required = false) String doctorEmail,
                                          @RequestParam(required = false) String status) {
        List<Map<String, Object>> items = dataService.getPrescriptions();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> item : items) {
            if (patientEmail != null && !patientEmail.equals(item.get("patientEmail"))) {
                continue;
            }
            if (doctorEmail != null && !doctorEmail.equals(item.get("doctorEmail"))) {
                continue;
            }
            if (status != null && !status.equals(item.get("status"))) {
                continue;
            }
            result.add(item);
        }

        return result;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String patientName = str(body.get("patientName"));
        String patientEmail = str(body.get("patientEmail"));
        String doctorName = str(body.get("doctorName"));
        String doctorEmail = str(body.get("doctorEmail"));
        String diagnosis = str(body.get("diagnosis"));
        Object medicines = body.get("medicines");

        if (patientName.isEmpty() || patientEmail.isEmpty() || doctorName.isEmpty() || doctorEmail.isEmpty() || diagnosis.isEmpty() || medicines == null) {
            return badRequest("Missing required prescription fields");
        }

        Map<String, Object> prescription = new LinkedHashMap<>();
        prescription.put("id", UUID.randomUUID().toString());
        prescription.put("appointmentId", body.get("appointmentId"));
        prescription.put("patientName", patientName);
        prescription.put("patientEmail", patientEmail);
        prescription.put("doctorName", doctorName);
        prescription.put("doctorEmail", doctorEmail);
        prescription.put("diagnosis", diagnosis);
        prescription.put("medicines", medicines);
        prescription.put("instructions", valueOrDefault(body.get("instructions"), ""));
        prescription.put("followUp", valueOrDefault(body.get("followUp"), ""));
        prescription.put("status", "pending");

        return ResponseEntity.status(201).body(dataService.createRecord(dataService.prescriptionsBucket(), prescription));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String status = str(body.get("status"));
        if (status.isEmpty()) {
            return badRequest("status is required");
        }

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("status", status);
        if ("dispensed".equals(status)) {
            patch.put("dispensedAt", Instant.now().toString());
        }

        Map<String, Object> updated = dataService.updateRecordStatus(dataService.prescriptionsBucket(), id, patch);
        if (updated == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Prescription not found"));
        }

        return ResponseEntity.ok(updated);
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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
