package ru.itmo.courses.course.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.courses.common.api.CursorResponse;
import ru.itmo.courses.common.api.Pagination;
import ru.itmo.courses.common.exception.ConflictException;
import ru.itmo.courses.common.exception.NotFoundException;
import ru.itmo.courses.course.dto.CourseRequest;
import ru.itmo.courses.course.dto.CourseResponse;
import ru.itmo.courses.course.model.Course;
import ru.itmo.courses.course.model.CourseStatus;
import ru.itmo.courses.course.repository.CourseRepository;
import ru.itmo.courses.enrollment.model.EnrollmentStatus;
import ru.itmo.courses.enrollment.repository.EnrollmentRepository;
import ru.itmo.courses.program.model.StudyProgram;
import ru.itmo.courses.program.repository.ProgramRepository;
import ru.itmo.courses.user.model.AppUser;
import ru.itmo.courses.user.model.UserRole;
import ru.itmo.courses.user.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CourseService {
    private final CourseRepository courses;
    private final UserRepository users;
    private final ProgramRepository programs;
    private final EnrollmentRepository enrollments;
    private final Clock clock;

    public Page<CourseResponse> list(CourseStatus status, Long programId, int page, int size) {
        return courses.search(status, programId, Pagination.page(page, size)).map(this::toResponse);
    }

    public CursorResponse<CourseResponse> scroll(long afterId, CourseStatus status, Long programId, int size) {
        Slice<Course> slice = courses.scroll(afterId, status, programId, Pagination.cursor(afterId, size));
        List<CourseResponse> content = slice.getContent().stream().map(this::toResponse).toList();
        Long nextCursor = slice.hasNext() ? content.getLast().id() : null;
        return new CursorResponse<>(content, slice.hasNext(), nextCursor);
    }

    public CourseResponse get(long id) {
        return toResponse(courses.findById(id).orElseThrow(() -> missing(id)));
    }

    @Transactional
    public CourseResponse create(CourseRequest request) {
        requireFutureStart(request.startDate());
        AppUser professor = activeProfessor(request.professorId());
        return toResponse(courses.save(new Course(professor, request.title().strip(), request.description(),
                request.capacity(), request.startDate(), request.endDate(), CourseStatus.DRAFT)));
    }

    @Transactional
    public CourseResponse update(long id, CourseRequest request) {
        Course course = locked(id);
        requireState(course, CourseStatus.DRAFT);
        requireFutureStart(request.startDate());
        course.setProfessor(activeProfessor(request.professorId()));
        course.setTitle(request.title().strip());
        course.setDescription(request.description());
        course.setCapacity(request.capacity());
        course.setStartDate(request.startDate());
        course.setEndDate(request.endDate());
        return toResponse(course);
    }

    @Transactional
    public void delete(long id) {
        Course course = locked(id);
        requireState(course, CourseStatus.DRAFT);
        courses.delete(course);
    }

    @Transactional
    public void attachProgram(long courseId, long programId) {
        Course course = locked(courseId);
        StudyProgram program = programs.findLockedById(programId)
                .orElseThrow(() -> new NotFoundException("Образовательная программа " + programId + " не найдена"));
        if (program.isArchived()) {
            throw new ConflictException("Нельзя добавлять курс в архивную программу");
        }
        course.getPrograms().add(program);
    }

    @Transactional
    public void detachProgram(long courseId, long programId) {
        Course course = locked(courseId);
        course.getPrograms().removeIf(program -> program.getId().equals(programId));
    }

    /** Изменения курса и записей студентов сначала блокируют одну и ту же строку курса. */
    @Transactional
    public CourseResponse changeStatus(long id, CourseStatus target) {
        Course course = locked(id);
        switch (target) {
            case ENROLLMENT_OPEN -> {
                requireState(course, CourseStatus.DRAFT);
                requireFutureStart(course.getStartDate());
                activeProfessor(course.getProfessor().getId());
            }
            case ENROLLMENT_CLOSED -> requireState(course, CourseStatus.ENROLLMENT_OPEN);
            case IN_PROGRESS -> {
                requireState(course, CourseStatus.ENROLLMENT_CLOSED);
                if (LocalDate.now(clock).isBefore(course.getStartDate())) {
                    throw new ConflictException("Дата начала курса ещё не наступила");
                }
            }
            case COMPLETED -> completeEnrollments(course);
            case CANCELLED -> cancelEnrollments(course);
            case DRAFT -> throw new ConflictException("Возврат курса в черновик не предусмотрен");
        }
        course.setStatus(target);
        return toResponse(course);
    }

    private void completeEnrollments(Course course) {
        requireState(course, CourseStatus.IN_PROGRESS);
        if (enrollments.existsByCourseIdAndStatusAndGradeIsNull(course.getId(), EnrollmentStatus.ENROLLED)) {
            throw new ConflictException("Для завершения курса выставьте оценки всем участникам");
        }
        enrollments.changeStatus(course.getId(), EnrollmentStatus.ENROLLED, EnrollmentStatus.COMPLETED);
    }

    private void cancelEnrollments(Course course) {
        if (course.getStatus() == CourseStatus.COMPLETED || course.getStatus() == CourseStatus.CANCELLED) {
            throw new ConflictException("Завершённый или уже отменённый курс нельзя отменить");
        }
        // Статусы записей и курса сохраняются или откатываются вместе.
        enrollments.changeStatus(course.getId(), EnrollmentStatus.ENROLLED, EnrollmentStatus.CANCELLED);
    }

    private Course locked(long id) {
        return courses.findLockedById(id).orElseThrow(() -> missing(id));
    }

    private AppUser activeProfessor(long id) {
        AppUser professor = users.findLockedById(id)
                .orElseThrow(() -> new NotFoundException("Преподаватель " + id + " не найден"));
        if (professor.getRole() != UserRole.PROFESSOR || !professor.isActive()) {
            throw new ConflictException("Курс должен вести активный пользователь с ролью PROFESSOR");
        }
        return professor;
    }

    private void requireFutureStart(LocalDate startDate) {
        if (!startDate.isAfter(LocalDate.now(clock))) {
            throw new ConflictException("Дата начала должна быть позже текущей даты UTC");
        }
    }

    private void requireState(Course course, CourseStatus expected) {
        if (course.getStatus() != expected) {
            throw new ConflictException("Операция требует состояния " + expected + ", текущее: " + course.getStatus());
        }
    }

    private NotFoundException missing(long id) {
        return new NotFoundException("Курс " + id + " не найден");
    }

    private CourseResponse toResponse(Course course) {
        return new CourseResponse(course.getId(), course.getTitle(), course.getDescription(),
                course.getProfessor().getId(), course.getCapacity(), course.getStartDate(),
                course.getEndDate(), course.getStatus());
    }
}
