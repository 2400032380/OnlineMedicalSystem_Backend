package com.ayush.medicare.controller;

import com.ayush.medicare.entity.OrderEntity;
import com.ayush.medicare.repository.OrderRepository;
import com.ayush.medicare.service.PaymentSessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/orders")
public class OrdersController {

    private final OrderRepository orderRepository;
    private final PaymentSessionService paymentSessionService;
    private final ObjectMapper objectMapper;

    public OrdersController(OrderRepository orderRepository,
                            PaymentSessionService paymentSessionService,
                            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.paymentSessionService = paymentSessionService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String patientEmail,
                                          @RequestParam(required = false) String status) {
        List<OrderEntity> items = orderRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (OrderEntity item : items) {
            if (patientEmail != null && !patientEmail.equals(item.getPatientEmail())) {
                continue;
            }
            if (status != null && !status.equals(item.getStatus())) {
                continue;
            }
            result.add(toResponse(item));
        }

        return result;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        Object itemsObj = body.get("items");
        String patientName = str(body.get("patientName"));
        String patientEmail = str(body.get("patientEmail"));
        String paymentVerificationId = str(body.get("paymentVerificationId"));
        double totalAmount = toDouble(body.get("totalAmount"));

        if (!(itemsObj instanceof List<?> items) || items.isEmpty() || patientName.isEmpty() || patientEmail.isEmpty()) {
            return badRequest("Missing required order fields");
        }
        if (paymentVerificationId.isEmpty()) {
            return badRequest("Verified payment reference is required before placing order");
        }
        if (totalAmount <= 0) {
            return badRequest("Valid total amount is required");
        }

        PaymentSessionService.ConsumeResult verificationResult = paymentSessionService.consumeVerification(
                paymentVerificationId,
                totalAmount,
                patientEmail,
                "medicine-order"
        );

        if (!verificationResult.ok) {
            return badRequest(verificationResult.message);
        }

        PaymentSessionService.Payment payment = verificationResult.session.payment;

        OrderEntity order = new OrderEntity();
        order.setId(UUID.randomUUID().toString());
        order.setItemsJson(writeJson(items));
        order.setTotalAmount(totalAmount);
        order.setPatientName(patientName);
        order.setPatientEmail(patientEmail);
        order.setStatus("pending");

        order.setPaymentStatus("paid");
        order.setPaymentMethod(payment.method);
        order.setPaymentReference(payment.paymentId);
        order.setPaymentAmount(totalAmount);
        order.setPaymentPaidAt(payment.paidAt);
        order.setPaymentVerificationId(paymentVerificationId);
        order.setPaymentProvider(payment.provider);

        OrderEntity saved = orderRepository.save(order);
        return ResponseEntity.status(201).body(toResponse(saved));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String status = str(body.get("status"));
        if (status.isEmpty()) {
            return badRequest("status is required");
        }

        Optional<OrderEntity> existing = orderRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Order not found"));
        }

        OrderEntity updated = existing.get();
        updated.setStatus(status);
        OrderEntity saved = orderRepository.save(updated);

        return ResponseEntity.ok(toResponse(saved));
    }

    private Map<String, Object> toResponse(OrderEntity item) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", item.getId());
        response.put("items", readItems(item.getItemsJson()));
        response.put("totalAmount", item.getTotalAmount());
        response.put("patientName", item.getPatientName());
        response.put("patientEmail", item.getPatientEmail());
        response.put("status", item.getStatus());

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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to serialize order items", ex);
        }
    }

    private List<Map<String, Object>> readItems(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
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
