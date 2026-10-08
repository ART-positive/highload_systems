package ru.itmo.courses.course.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CourseRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull @Size(max = 4000) String description,
        @NotNull @Positive Long professorId,
        @NotNull @Positive Integer capacity,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {

    @AssertTrue(message = "Дата окончания не может быть раньше даты начала")
    @JsonIgnore
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
