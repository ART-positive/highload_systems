package ru.itmo.courses.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.headers.Header;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.courses.common.api.PageResponses;
import ru.itmo.courses.user.dto.UserRequest;
import ru.itmo.courses.user.dto.UserResponse;
import ru.itmo.courses.user.service.UserService;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Список с пагинацией; размер страницы от 1 до 50")
    @ApiResponse(responseCode = "200", headers = @Header(name = "X-Total-Count", description = "Общее количество записей"))
    public ResponseEntity<List<UserResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponses.of(service.list(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить по идентификатору")
    public UserResponse get(@PathVariable @Positive long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Создать")
    @ApiResponse(responseCode = "201", description = "Создано; Location содержит адрес записи")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        UserResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить; active=false деактивирует пользователя")
    public UserResponse update(@PathVariable @Positive long id, @Valid @RequestBody UserRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить запись без связанных курсов и зачислений")
    @ApiResponse(responseCode = "204", description = "Удалено")
    public ResponseEntity<Void> delete(@PathVariable @Positive long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
