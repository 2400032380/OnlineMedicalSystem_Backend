package com.ayush.medicare.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentSessionService {

    public static class PaymentSession {
        public String sessionId;
        public String orderId;
        public double amount;
        public String currency;
        public String patientEmail;
        public String purpose;
        public String receipt;
        public Map<String, Object> notes;
        public boolean verified;
        public String verificationId;
        public String consumedAt;
        public String createdAt;
        public Payment payment;
    }

    public static class Payment {
        public String provider;
        public String paymentId;
        public String signature;
        public String method;
        public String paidAt;
    }

    public static class ConsumeResult {
        public boolean ok;
        public String message;
        public PaymentSession session;
    }

    private final Map<String, PaymentSession> sessionsByOrderId = new ConcurrentHashMap<>();
    private final Map<String, PaymentSession> sessionsByVerificationId = new ConcurrentHashMap<>();

    public PaymentSession createSession(String orderId, double amount, String currency, String patientEmail, String purpose,
                                        String receipt, Map<String, Object> notes) {
        PaymentSession session = new PaymentSession();
        session.sessionId = UUID.randomUUID().toString();
        session.orderId = orderId;
        session.amount = amount;
        session.currency = currency;
        session.patientEmail = normalizeEmail(patientEmail);
        session.purpose = purpose;
        session.receipt = receipt;
        session.notes = notes;
        session.verified = false;
        session.verificationId = null;
        session.consumedAt = null;
        session.createdAt = Instant.now().toString();
        sessionsByOrderId.put(orderId, session);
        return session;
    }

    public PaymentSession markVerified(String orderId, String paymentId, String signature,
                                       String paymentMethod, String paidAt, String provider) {
        PaymentSession session = sessionsByOrderId.get(orderId);
        if (session == null) {
            return null;
        }

        session.verified = true;
        session.verificationId = UUID.randomUUID().toString();

        Payment payment = new Payment();
        payment.provider = provider;
        payment.paymentId = paymentId;
        payment.signature = signature;
        payment.method = paymentMethod;
        payment.paidAt = paidAt;
        session.payment = payment;

        sessionsByVerificationId.put(session.verificationId, session);
        return session;
    }

    public ConsumeResult consumeVerification(String verificationId, double expectedAmount,
                                             String expectedPatientEmail, String expectedPurpose) {
        ConsumeResult result = new ConsumeResult();
        PaymentSession session = sessionsByVerificationId.get(verificationId);

        if (session == null || !session.verified) {
            result.ok = false;
            result.message = "Invalid or unverified payment reference";
            return result;
        }

        if (session.consumedAt != null) {
            result.ok = false;
            result.message = "Payment reference was already used";
            return result;
        }

        if (Double.compare(session.amount, expectedAmount) != 0) {
            result.ok = false;
            result.message = "Payment amount mismatch";
            return result;
        }

        String normalizedExpectedEmail = normalizeEmail(expectedPatientEmail);
        if (normalizedExpectedEmail.isEmpty() || !normalizedExpectedEmail.equals(session.patientEmail)) {
            result.ok = false;
            result.message = "Payment was not made by this patient account";
            return result;
        }

        if (expectedPurpose != null && !expectedPurpose.equals(session.purpose)) {
            result.ok = false;
            result.message = "Payment purpose mismatch";
            return result;
        }

        session.consumedAt = Instant.now().toString();
        result.ok = true;
        result.session = session;
        return result;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
