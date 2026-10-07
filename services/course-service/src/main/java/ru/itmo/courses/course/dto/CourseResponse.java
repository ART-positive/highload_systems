package ru.itmo.courses.course.dto;

import java.time.LocalDate;
import ru.itmo.courses.course.model.CourseStatus;

public record CourseResponse(Long id, String title, String description, Long professorId,
        int capacity, LocalDate startDate, LocalDate endDate, CourseStatus status) {
}
