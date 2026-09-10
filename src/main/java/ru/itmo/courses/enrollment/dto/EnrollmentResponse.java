package ru.itmo.courses.enrollment.dto;

import java.time.Instant;
import ru.itmo.courses.enrollment.model.EnrollmentStatus;

public record EnrollmentResponse(Long id, Long studentId, Long courseId,
        EnrollmentStatus status, Integer grade, Instant enrolledAt) {
}
