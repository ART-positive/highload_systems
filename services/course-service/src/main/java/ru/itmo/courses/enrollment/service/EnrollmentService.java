package ru.itmo.courses.enrollment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.courses.common.api.Pagination;
import ru.itmo.courses.common.exception.ConflictException;
import ru.itmo.courses.common.exception.NotFoundException;
import ru.itmo.courses.course.model.Course;
import ru.itmo.courses.course.model.CourseStatus;
import ru.itmo.courses.course.repository.CourseRepository;
import ru.itmo.courses.enrollment.dto.EnrollmentRequest;
import ru.itmo.courses.enrollment.dto.EnrollmentResponse;
import ru.itmo.courses.enrollment.model.Enrollment;
import ru.itmo.courses.enrollment.model.EnrollmentStatus;
import ru.itmo.courses.enrollment.repository.EnrollmentRepository;
import ru.itmo.courses.user.model.UserRole;
import ru.itmo.courses.integration.UserDirectory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EnrollmentService {
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final UserDirectory users;
    private final Clock clock;

    public Page<EnrollmentResponse> list(Long studentId, Long courseId, EnrollmentStatus status, int page, int size) {
        return enrollments.search(studentId, courseId, status, Pagination.page(page, size)).map(this::toResponse);
    }

    public EnrollmentResponse get(long id) {
        return toResponse(find(id));
    }

    /** Блокировка курса перед проверкой мест защищает от одновременного зачисления сверх лимита. */
    @Transactional
    public Registration enroll(EnrollmentRequest request) {
        Course course = courses.findLockedById(request.courseId())
                .orElseThrow(() -> new NotFoundException("Курс " + request.courseId() + " не найден"));
        if (course.getStatus() != CourseStatus.ENROLLMENT_OPEN
                || !LocalDate.now(clock).isBefore(course.getStartDate())) {
            throw new ConflictException("Запись доступна только при открытом наборе до даты начала курса");
        }
        long studentId = users.requireActive(request.studentId(), UserRole.STUDENT).id();
        Enrollment existing = enrollments.findByStudentIdAndCourseId(studentId, course.getId()).orElse(null);
        if (existing != null && existing.getStatus() != EnrollmentStatus.DROPPED) {
            throw new ConflictException("Студент уже записан на этот курс");
        }
        if (enrollments.countByCourseIdAndStatus(course.getId(), EnrollmentStatus.ENROLLED) >= course.getCapacity()) {
            throw new ConflictException("На курсе нет свободных мест");
        }
        if (existing != null) {
            existing.reactivate(Instant.now(clock));
            return new Registration(toResponse(existing), false);
        }
        return new Registration(toResponse(enrollments.save(new Enrollment(studentId, course, EnrollmentStatus.ENROLLED,
                null, Instant.now(clock)))), true);
    }

    @Transactional
    public EnrollmentResponse grade(long id, int grade) {
        Enrollment enrollment = lockedEnrollment(id);
        if (enrollment.getCourse().getStatus() != CourseStatus.IN_PROGRESS
                || enrollment.getStatus() != EnrollmentStatus.ENROLLED) {
            throw new ConflictException("Оценка выставляется участнику только во время обучения");
        }
        if (grade < 0 || grade > 100) {
            throw new IllegalArgumentException("Оценка должна быть от 0 до 100");
        }
        enrollment.setGrade(grade);
        return toResponse(enrollment);
    }

    /** Логический отказ сохраняет историю; повторный вызов не меняет результат. */
    @Transactional
    public void drop(long id) {
        Enrollment enrollment = lockedEnrollment(id);
        if (enrollment.getStatus() == EnrollmentStatus.DROPPED) {
            return;
        }
        Course course = enrollment.getCourse();
        if (enrollment.getStatus() != EnrollmentStatus.ENROLLED
                || (course.getStatus() != CourseStatus.ENROLLMENT_OPEN
                    && course.getStatus() != CourseStatus.ENROLLMENT_CLOSED)
                || !LocalDate.now(clock).isBefore(course.getStartDate())) {
            throw new ConflictException("Отказ от курса невозможен только до начала обучения");
        }
        enrollment.setStatus(EnrollmentStatus.DROPPED);
    }

    private Enrollment lockedEnrollment(long id) {
        // До блокировки читаем только ID курса, чтобы не загрузить устаревшую запись студента.
        long courseId = enrollments.findCourseIdById(id).orElseThrow(() -> missing(id));
        courses.findLockedById(courseId).orElseThrow(() -> new NotFoundException("Курс не найден"));
        return find(id);
    }

    private Enrollment find(long id) {
        return enrollments.findById(id).orElseThrow(() -> missing(id));
    }

    private NotFoundException missing(long id) {
        return new NotFoundException("Запись на курс " + id + " не найдена");
    }

    private EnrollmentResponse toResponse(Enrollment enrollment) {
        return new EnrollmentResponse(enrollment.getId(), enrollment.getStudentId(),
                enrollment.getCourse().getId(), enrollment.getStatus(), enrollment.getGrade(), enrollment.getEnrolledAt());
    }

    public record Registration(EnrollmentResponse enrollment, boolean created) {
    }
}
