package com.ayush.medicare.controller;

import com.ayush.medicare.service.InMemoryDataService;
import com.ayush.medicare.service.PaymentSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/lab-bookings")
public class LabBookingsController {

    private final InMemoryDataService dataService;
    private final PaymentSessionService paymentSessionService;

    public LabBookingsController(InMemoryDataService dataService, PaymentSessionService paymentSessionService) {
        this.dataService = dataService;
        this.paymentSessionService = paymentSessionService;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String patientEmail,
                                          @RequestParam(required = false) String status) {
        List<Map<String, Object>> items = dataService.getLabBookings();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> item : items) {
            if (patientEmail != null && !patientEmail.equals(item.get("patientEmail"))) {
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
        Object testsObj = body.get("tests");
        String patientName = str(body.get("patientName"));
        String patientEmail = str(body.get("patientEmail"));
        String paymentVerificationId = str(body.get("paymentVerificationId"));
        double totalAmount = toDouble(body.get("totalAmount"));

        if (!(testsObj instanceof List<?> tests) || tests.isEmpty() || patientName.isEmpty() || patientEmail.isEmpty()) {
            return badRequest("Missing required lab booking fields");
        }
        if (paymentVerificationId.isEmpty()) {
            return badRequest("Verified payment reference is required before booking tests");
        }
        if (totalAmount <= 0) {
            return badRequest("Valid total amount is required");
        }

        PaymentSessionService.ConsumeResult verificationResult = paymentSessionService.consumeVerification(
                paymentVerificationId,
                totalAmount,
                patientEmail,
                "lab-booking"
        );

        if (!verificationResult.ok) {
            return badRequest(verificationResult.message);
        }

        PaymentSessionService.Payment payment = verificationResult.session.payment;

        Map<String, Object> booking = new LinkedHashMap<>();
        booking.put("id", UUID.randomUUID().toString());
        booking.put("tests", tests);
        booking.put("totalAmount", totalAmount);
        booking.put("patientName", patientName);
        booking.put("patientEmail", patientEmail);
        booking.put("status", "pending");

        Map<String, Object> paymentMap = new LinkedHashMap<>();
        paymentMap.put("status", "paid");
        paymentMap.put("method", payment.method);
        paymentMap.put("reference", payment.paymentId);
        paymentMap.put("amount", totalAmount);
        paymentMap.put("paidAt", payment.paidAt);
        paymentMap.put("verificationId", paymentVerificationId);
        paymentMap.put("provider", payment.provider);
        booking.put("payment", paymentMap);

        return ResponseEntity.status(201).body(dataService.createRecord(dataService.labBookingsBucket(), booking));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String status = str(body.get("status"));
        if (status.isEmpty()) {
            return badRequest("status is required");
        }

        Map<String, Object> updated = dataService.updateRecordStatus(dataService.labBookingsBucket(), id, Map.of("status", status));
        if (updated == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Lab booking not found"));
        }

        return ResponseEntity.ok(updated);
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private double toDouble(Object value) {
        if (value == null) return 0;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return 0;
        }
    }
}
