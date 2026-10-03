package ru.itmo.courses.course.controller;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.courses.common.api.PageResponses;
import ru.itmo.courses.course.dto.CourseRequest;
import ru.itmo.courses.course.dto.CourseResponse;
import ru.itmo.courses.course.dto.CourseStatusRequest;
import ru.itmo.courses.course.model.CourseStatus;
import ru.itmo.courses.course.service.CourseService;
import ru.itmo.courses.program.dto.ProgramResponse;
import ru.itmo.courses.program.service.ProgramService;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/courses")
@Tag(name = "Course")
@RequiredArgsConstructor
public class CourseController {
    private final CourseService service;
    private final ProgramService programs;

    @GetMapping
    @Operation(summary = "Каталог с фильтрами и общим количеством в заголовке")
    @ApiResponse(responseCode = "200",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = CourseResponse.class))),
            headers = @Header(name = "X-Total-Count", description = "Количество курсов с учётом фильтров"))
    public ResponseEntity<?> getCourses(
            @RequestParam(required = false) CourseStatus status,
            @RequestParam(required = false) @Positive Long programId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponses.of(service.list(status, programId, page, size));
    }

    @GetMapping("/scroll")
    @Operation(summary = "Бесконечная прокрутка без COUNT: передайте nextCursor в afterId",
            description = "size от 1 до 50. Сортировка по id ASC. nextCursor=null означает конец. Сохраняйте фильтры между запросами.")
    @ApiResponse(responseCode = "200",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(ref = "#/components/schemas/CursorResponseCourseResponse")))
    public ResponseEntity<?> scrollCourses(
            @RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) CourseStatus status,
            @RequestParam(required = false) @Positive Long programId) {
        return ResponseEntity.ok(service.scroll(afterId, status, programId, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить курс")
    @ApiResponse(responseCode = "200",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CourseResponse.class)))
    public ResponseEntity<?> getCourse(@PathVariable @Positive long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "Создать черновик курса с будущей датой начала")
    @ApiResponse(responseCode = "201",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CourseResponse.class)),
            description = "Курс создан")
    public ResponseEntity<?> createCourse(@Valid @RequestBody CourseRequest request) {
        CourseResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/courses/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить черновик курса")
    @ApiResponse(responseCode = "200",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CourseResponse.class)))
    public ResponseEntity<?> updateCourse(@PathVariable @Positive long id, @Valid @RequestBody CourseRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить черновик; для опубликованного курса используйте CANCELLED")
    @ApiResponse(responseCode = "204", content = @Content, description = "Черновик удалён")
    public ResponseEntity<?> deleteCourse(@PathVariable @Positive long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Изменить состояние курса",
            description = "DRAFT -> ENROLLMENT_OPEN -> ENROLLMENT_CLOSED -> IN_PROGRESS -> COMPLETED. "
                    + "Отмена через CANCELLED доступна до завершения. Завершение требует всех оценок.")
    @ApiResponse(responseCode = "200",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CourseResponse.class)))
    public ResponseEntity<?> changeCourseStatus(@PathVariable @Positive long id,
            @Valid @RequestBody CourseStatusRequest request) {
        return ResponseEntity.ok(service.changeStatus(id, request.status()));
    }

    @GetMapping("/{id}/programs")
    @Operation(summary = "Программы курса с пагинацией")
    @ApiResponse(responseCode = "200",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = ProgramResponse.class))),
            headers = @Header(name = "X-Total-Count", description = "Количество программ курса"))
    public ResponseEntity<?> getCoursePrograms(@PathVariable @Positive long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponses.of(programs.listForCourse(id, page, size));
    }

    @PutMapping("/{id}/programs/{programId}")
    @Operation(summary = "Добавить курс в программу; повторный запрос безопасен")
    @ApiResponse(responseCode = "204",
            content = @Content, description = "Связь установлена")
    public ResponseEntity<?> attachProgram(@PathVariable @Positive long id, @PathVariable @Positive long programId) {
        service.attachProgram(id, programId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/programs/{programId}")
    @Operation(summary = "Исключить курс из программы; повторный запрос безопасен")
    @ApiResponse(responseCode = "204",
            content = @Content, description = "Связь отсутствует")
    public ResponseEntity<?> detachProgram(@PathVariable @Positive long id, @PathVariable @Positive long programId) {
        service.detachProgram(id, programId);
        return ResponseEntity.noContent().build();
    }
}
