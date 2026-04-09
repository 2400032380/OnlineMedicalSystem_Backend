package com.ayush.medicare.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class InMemoryDataService {
    private final List<Map<String, Object>> appointments = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> calls = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> orders = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> labBookings = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> prescriptions = new CopyOnWriteArrayList<>();

    public List<Map<String, Object>> getAppointments() {
        return new ArrayList<>(appointments);
    }

    public List<Map<String, Object>> appointmentsBucket() {
        return appointments;
    }

    public List<Map<String, Object>> getCalls() {
        return new ArrayList<>(calls);
    }

    public List<Map<String, Object>> callsBucket() {
        return calls;
    }

    public List<Map<String, Object>> getOrders() {
        return new ArrayList<>(orders);
    }

    public List<Map<String, Object>> ordersBucket() {
        return orders;
    }

    public List<Map<String, Object>> getLabBookings() {
        return new ArrayList<>(labBookings);
    }

    public List<Map<String, Object>> labBookingsBucket() {
        return labBookings;
    }

    public List<Map<String, Object>> getPrescriptions() {
        return new ArrayList<>(prescriptions);
    }

    public List<Map<String, Object>> prescriptionsBucket() {
        return prescriptions;
    }

    public Map<String, Object> createRecord(List<Map<String, Object>> bucket, Map<String, Object> record) {
        String now = Instant.now().toString();
        record.put("createdAt", now);
        record.put("updatedAt", now);
        bucket.add(record);
        return record;
    }

    public Map<String, Object> updateRecordStatus(List<Map<String, Object>> bucket, String id, Map<String, Object> patch) {
        for (int i = 0; i < bucket.size(); i++) {
            Map<String, Object> item = bucket.get(i);
            if (id.equals(item.get("id"))) {
                item.putAll(patch);
                item.put("updatedAt", Instant.now().toString());
                return item;
            }
        }
        return null;
    }
}
