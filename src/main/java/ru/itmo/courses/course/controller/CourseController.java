package ru.itmo.courses.course.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.headers.Header;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
import ru.itmo.courses.common.api.CursorResponse;
import ru.itmo.courses.course.dto.CourseRequest;
import ru.itmo.courses.course.dto.CourseResponse;
import ru.itmo.courses.course.dto.CourseStatusRequest;
import ru.itmo.courses.course.model.CourseStatus;
import ru.itmo.courses.course.service.CourseService;
import ru.itmo.courses.program.dto.ProgramResponse;
import ru.itmo.courses.program.service.ProgramService;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/courses")
@Tag(name = "Course")
public class CourseController {
    private final CourseService service;
    private final ProgramService programs;

    public CourseController(CourseService service, ProgramService programs) {
        this.service = service;
        this.programs = programs;
    }

    @GetMapping
    @Operation(summary = "Каталог с фильтрами и общим количеством в заголовке")
    @ApiResponse(responseCode = "200", headers = @Header(name = "X-Total-Count", description = "Количество курсов с учётом фильтров"))
    public ResponseEntity<List<CourseResponse>> list(
            @RequestParam(required = false) CourseStatus status,
            @RequestParam(required = false) @Positive Long programId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponses.of(service.list(status, programId, page, size));
    }

    @GetMapping("/scroll")
    @Operation(summary = "Бесконечная прокрутка без COUNT: передайте nextCursor в afterId",
            description = "size от 1 до 50. Сортировка по id ASC. nextCursor=null означает конец. Сохраняйте фильтры между запросами.")
    public CursorResponse<CourseResponse> scroll(
            @RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) CourseStatus status,
            @RequestParam(required = false) @Positive Long programId) {
        return service.scroll(afterId, status, programId, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить курс")
    public CourseResponse get(@PathVariable @Positive long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Создать черновик курса с будущей датой начала")
    @ApiResponse(responseCode = "201", description = "Курс создан")
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CourseRequest request) {
        CourseResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/courses/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить черновик курса")
    public CourseResponse update(@PathVariable @Positive long id, @Valid @RequestBody CourseRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить черновик; для опубликованного курса используйте CANCELLED")
    @ApiResponse(responseCode = "204", description = "Черновик удалён")
    public ResponseEntity<Void> delete(@PathVariable @Positive long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Изменить состояние курса",
            description = "DRAFT → ENROLLMENT_OPEN → ENROLLMENT_CLOSED → IN_PROGRESS → COMPLETED. "
                    + "Отмена через CANCELLED доступна до завершения. Завершение требует всех оценок.")
    public CourseResponse changeStatus(@PathVariable @Positive long id,
            @Valid @RequestBody CourseStatusRequest request) {
        return service.changeStatus(id, request.status());
    }

    @GetMapping("/{id}/programs")
    @Operation(summary = "Программы курса с пагинацией")
    @ApiResponse(responseCode = "200", headers = @Header(name = "X-Total-Count", description = "Количество программ курса"))
    public ResponseEntity<List<ProgramResponse>> programs(@PathVariable @Positive long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponses.of(programs.listForCourse(id, page, size));
    }

    @PutMapping("/{id}/programs/{programId}")
    @Operation(summary = "Добавить курс в программу; повторный запрос безопасен")
    @ApiResponse(responseCode = "204", description = "Связь установлена")
    public ResponseEntity<Void> attach(@PathVariable @Positive long id, @PathVariable @Positive long programId) {
        service.attachProgram(id, programId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/programs/{programId}")
    @Operation(summary = "Исключить курс из программы; повторный запрос безопасен")
    @ApiResponse(responseCode = "204", description = "Связь отсутствует")
    public ResponseEntity<Void> detach(@PathVariable @Positive long id, @PathVariable @Positive long programId) {
        service.detachProgram(id, programId);
        return ResponseEntity.noContent().build();
    }
}
