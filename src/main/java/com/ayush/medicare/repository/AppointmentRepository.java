package com.ayush.medicare.repository;

import com.ayush.medicare.entity.AppointmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<AppointmentEntity, String> {
}
