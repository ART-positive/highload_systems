package ru.itmo.courses.program.dto;

public record ProgramResponse(Long id, String code, String name, String description, boolean archived) {
}
