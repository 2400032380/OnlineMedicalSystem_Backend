package com.ayush.medicare.controller;

import com.ayush.medicare.service.InMemoryDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/calls")
public class CallsController {

    private final InMemoryDataService dataService;

    public CallsController(InMemoryDataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String patientEmail,
                                          @RequestParam(required = false) String doctorEmail) {
        List<Map<String, Object>> items = dataService.getCalls();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> item : items) {
            if (patientEmail != null && !patientEmail.equals(item.get("patientEmail"))) {
                continue;
            }
            if (doctorEmail != null && !doctorEmail.equals(item.get("doctorEmail"))) {
                continue;
            }
            result.add(item);
        }

        return result;
    }

    @PostMapping("/ring")
    public ResponseEntity<?> ring(@RequestBody Map<String, Object> body) {
        String appointmentId = str(body.get("appointmentId"));
        String roomName = str(body.get("roomName"));
        String patientEmail = str(body.get("patientEmail"));
        String doctorEmail = str(body.get("doctorEmail"));

        if (appointmentId.isEmpty() || roomName.isEmpty() || patientEmail.isEmpty() || doctorEmail.isEmpty()) {
            return badRequest("Missing required call fields");
        }

        Map<String, Object> call = new LinkedHashMap<>();
        call.put("id", UUID.randomUUID().toString());
        call.put("status", "ringing");
        call.put("appointmentId", appointmentId);
        call.put("roomName", roomName);
        call.put("patientEmail", patientEmail);
        call.put("patientName", valueOrDefault(body.get("patientName"), ""));
        call.put("doctorEmail", doctorEmail);
        call.put("doctorName", valueOrDefault(body.get("doctorName"), ""));
        call.put("date", body.get("date"));
        call.put("time", body.get("time"));
        call.put("specialty", valueOrDefault(body.get("specialty"), ""));

        return ResponseEntity.status(201).body(dataService.createRecord(dataService.callsBucket(), call));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String status = str(body.get("status"));
        if (status.isEmpty()) {
            return badRequest("status is required");
        }

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("status", status);
        if (body.get("suggestions") instanceof String suggestions && !suggestions.trim().isEmpty()) {
            patch.put("suggestions", suggestions);
        }

        Map<String, Object> updated = dataService.updateRecordStatus(dataService.callsBucket(), id, patch);
        if (updated == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Call not found"));
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
