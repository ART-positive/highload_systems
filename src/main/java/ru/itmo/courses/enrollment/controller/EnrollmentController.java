package ru.itmo.courses.enrollment.controller;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.courses.common.api.PageResponses;
import ru.itmo.courses.enrollment.dto.EnrollmentRequest;
import ru.itmo.courses.enrollment.dto.EnrollmentResponse;
import ru.itmo.courses.enrollment.dto.GradeRequest;
import ru.itmo.courses.enrollment.model.EnrollmentStatus;
import ru.itmo.courses.enrollment.service.EnrollmentService;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/enrollments")
@Tag(name = "Enrollment")
@RequiredArgsConstructor
public class EnrollmentController {
    private final EnrollmentService service;

    @GetMapping
    @Operation(summary = "Записи студентов с фильтрами и пагинацией")
    @ApiResponse(responseCode = "200",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = EnrollmentResponse.class))),
            headers = @Header(name = "X-Total-Count", description = "Общее количество с учётом фильтров"))
    public ResponseEntity<?> getEnrollments(
            @RequestParam(required = false) @Positive Long studentId,
            @RequestParam(required = false) @Positive Long courseId,
            @RequestParam(required = false) EnrollmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponses.of(service.list(studentId, courseId, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить запись и оценку")
    @ApiResponse(responseCode = "200",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = EnrollmentResponse.class)))
    public ResponseEntity<?> getEnrollment(@PathVariable @Positive long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "Записать студента; восстановить запись после отказа")
    @ApiResponse(responseCode = "201",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = EnrollmentResponse.class)),
            description = "Создана новая запись")
    @ApiResponse(responseCode = "200",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = EnrollmentResponse.class)),
            description = "Восстановлена существующая запись")
    public ResponseEntity<?> enrollStudent(@Valid @RequestBody EnrollmentRequest request) {
        EnrollmentService.Registration result = service.enroll(request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/enrollments/" + result.enrollment().id()))
                    .body(result.enrollment());
        }
        return ResponseEntity.ok(result.enrollment());
    }

    @PatchMapping("/{id}/grade")
    @Operation(summary = "Выставить или исправить оценку от 0 до 100 во время обучения")
    @ApiResponse(responseCode = "200",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = EnrollmentResponse.class)))
    public ResponseEntity<?> updateGrade(@PathVariable @Positive long id, @Valid @RequestBody GradeRequest request) {
        return ResponseEntity.ok(service.grade(id, request.grade()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Отказаться до начала курса",
            description = "Логическое удаление: статус DROPPED, история сохраняется и доступна через GET. Повторный отказ возвращает 204.")
    @ApiResponse(responseCode = "204",
            content = @Content, description = "Запись отменена студентом")
    public ResponseEntity<?> dropEnrollment(@PathVariable @Positive long id) {
        service.drop(id);
        return ResponseEntity.noContent().build();
    }
}
