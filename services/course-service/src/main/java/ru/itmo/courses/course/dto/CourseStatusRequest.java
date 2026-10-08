package ru.itmo.courses.course.dto;

import jakarta.validation.constraints.NotNull;
import ru.itmo.courses.course.model.CourseStatus;

public record CourseStatusRequest(@NotNull CourseStatus status) {
}
