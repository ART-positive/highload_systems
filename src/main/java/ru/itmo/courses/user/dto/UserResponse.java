package ru.itmo.courses.user.dto;

import ru.itmo.courses.user.model.UserRole;

public record UserResponse(Long id, String fullName, String email, UserRole role, boolean active) {
}
