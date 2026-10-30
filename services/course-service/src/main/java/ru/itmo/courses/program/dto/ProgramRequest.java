package ru.itmo.courses.program.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record ProgramRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Z0-9_-]+") String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull @Size(max = 4000) String description,
        @NotNull Boolean archived) {
}
