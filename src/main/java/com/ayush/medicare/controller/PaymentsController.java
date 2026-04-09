package com.ayush.medicare.controller;

import com.ayush.medicare.service.PaymentSessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentsController {

    private final PaymentSessionService paymentSessionService;

    @Value("${app.payment.provider}")
    private String paymentProvider;

    @Value("${app.payment.upi-id}")
    private String upiId;

    public PaymentsController(PaymentSessionService paymentSessionService) {
        this.paymentSessionService = paymentSessionService;
    }

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
        double amount = toDouble(body.get("amount"));
        String patientEmail = str(body.get("patientEmail"));
        String purpose = str(body.get("purpose"));
        String receipt = str(body.get("receipt"));
        @SuppressWarnings("unchecked")
        Map<String, Object> notes = body.get("notes") instanceof Map ? (Map<String, Object>) body.get("notes") : Map.of();

        if (amount <= 0) {
            return badRequest("Valid amount is required");
        }
        if (patientEmail.isEmpty()) {
            return badRequest("patientEmail is required");
        }
        if (purpose.isEmpty()) {
            return badRequest("purpose is required");
        }

        if (!"manual_upi".equals(paymentProvider)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Spring backend currently supports PAYMENT_PROVIDER=manual_upi"));
        }

        String effectiveReceipt = receipt.isEmpty() ? "receipt_" + System.currentTimeMillis() : receipt;
        String orderId = "upi_" + UUID.randomUUID();
        paymentSessionService.createSession(orderId, amount, "INR", patientEmail, purpose, effectiveReceipt, notes);

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", orderId);
        order.put("amount", Math.round(amount * 100));
        order.put("currency", "INR");
        order.put("receipt", effectiveReceipt);
        order.put("notes", notes);

        String upiLink = "upi://pay?pa=" + encode(upiId)
                + "&pn=" + encode("Ayush Medicare")
                + "&am=" + String.format("%.2f", amount)
                + "&cu=INR&tn=" + encode(effectiveReceipt);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "provider", "manual_upi",
                "order", order,
                "upiId", upiId,
                "upiLink", upiLink
        ));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, Object> body) {
        if (!"manual_upi".equals(paymentProvider)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Spring backend currently supports PAYMENT_PROVIDER=manual_upi"));
        }

        String manualOrderId = str(body.get("manual_order_id"));
        String utr = str(body.get("utr"));
        if (manualOrderId.isEmpty() || utr.isEmpty()) {
            return badRequest("manual_order_id and utr are required");
        }
        if (utr.trim().length() < 8) {
            return badRequest("Please enter a valid UTR/transaction reference");
        }

        PaymentSessionService.PaymentSession session = paymentSessionService.markVerified(
                manualOrderId,
                utr.trim(),
                "manual_upi",
                "upi",
                Instant.now().toString(),
                "manual_upi"
        );

        if (session == null) {
            return badRequest("Payment order is unknown or expired. Please retry checkout.");
        }

        return ResponseEntity.ok(Map.of(
                "provider", "manual_upi",
                "verified", true,
                "paymentReference", utr.trim(),
                "paymentMethod", "upi",
                "paidAt", session.payment.paidAt,
                "verificationId", session.verificationId,
                "purpose", session.purpose,
                "amount", session.amount
        ));
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

    private String encode(String value) {
        return value.replace("@", "%40").replace(" ", "%20");
    }
}
