package ru.itmo.courses.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
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

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User")
@RequiredArgsConstructor
public class UserController {
    private final UserService service;

    @GetMapping
    @Operation(summary = "Список с пагинацией; размер страницы от 1 до 50")
    @ApiResponse(responseCode = "200",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = UserResponse.class))),
            headers = @Header(name = "X-Total-Count", description = "Общее количество записей"))
    public Mono<ResponseEntity<?>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size).map(PageResponses::of);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить по идентификатору")
    @ApiResponse(responseCode = "200",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = UserResponse.class)))
    public Mono<ResponseEntity<?>> getUser(@PathVariable @Positive long id) {
        return service.get(id).map(ResponseEntity::ok);
    }

    @PostMapping
    @Operation(summary = "Создать")
    @ApiResponse(responseCode = "201",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = UserResponse.class)),
            description = "Создано; Location содержит адрес записи")
    public Mono<ResponseEntity<?>> createUser(@Valid @RequestBody UserRequest request) {
        return service.create(request).map(response ->
                ResponseEntity.created(URI.create("/api/v1/users/" + response.id())).body(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить; active=false деактивирует пользователя")
    @ApiResponse(responseCode = "200",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = UserResponse.class)))
    public Mono<ResponseEntity<?>> updateUser(@PathVariable @Positive long id, @Valid @RequestBody UserRequest request) {
        return service.update(id, request).map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить запись без связанных курсов и зачислений")
    @ApiResponse(responseCode = "204",
            content = @Content, description = "Удалено")
    public Mono<ResponseEntity<?>> deleteUser(@PathVariable @Positive long id) {
        return service.delete(id).thenReturn(ResponseEntity.noContent().build());
    }
}
