package ru.itmo.courses.user.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.itmo.courses.user.dto.UserResponse;
import ru.itmo.courses.user.model.UserRole;
import ru.itmo.courses.user.service.UserService;

@Hidden
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserReferenceController {
    private final UserService users;

    @PutMapping("/{id}/reference")
    public Mono<UserResponse> reference(@PathVariable @Positive long id, @RequestParam UserRole role) {
        return users.reference(id, role);
    }
}
