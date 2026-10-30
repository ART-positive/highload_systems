package ru.itmo.courses.course;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.itmo.courses.common.exception.ConflictException;
import ru.itmo.courses.course.dto.CourseRequest;
import ru.itmo.courses.course.model.Course;
import ru.itmo.courses.course.model.CourseStatus;
import ru.itmo.courses.course.repository.CourseRepository;
import ru.itmo.courses.course.service.CourseService;
import ru.itmo.courses.enrollment.model.EnrollmentStatus;
import ru.itmo.courses.enrollment.repository.EnrollmentRepository;
import ru.itmo.courses.program.repository.ProgramRepository;
import ru.itmo.courses.user.dto.UserResponse;
import ru.itmo.courses.user.model.UserRole;
import ru.itmo.courses.integration.UserDirectory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {
    @Mock private CourseRepository courses;
    @Mock private UserDirectory users;
    @Mock private ProgramRepository programs;
    @Mock private EnrollmentRepository enrollments;
    private CourseService service;

    @BeforeEach
    void setUp() {
        service = new CourseService(courses, users, programs, enrollments,
                Clock.fixed(Instant.parse("2026-01-10T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void doesNotCreateCourseStartingToday() {
        CourseRequest request = new CourseRequest("Java", "", 1L, 20,
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 10));
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(courses, users);
    }

    @Test
    void doesNotFinishCourseUntilEveryStudentHasGrade() {
        Course course = mock(Course.class);
        when(course.getId()).thenReturn(1L);
        when(course.getStatus()).thenReturn(CourseStatus.IN_PROGRESS);
        when(courses.findLockedById(1L)).thenReturn(Optional.of(course));
        when(enrollments.existsByCourseIdAndStatusAndGradeIsNull(1, EnrollmentStatus.ENROLLED)).thenReturn(true);

        assertThatThrownBy(() -> service.changeStatus(1, CourseStatus.COMPLETED))
                .isInstanceOf(ConflictException.class).hasMessageContaining("оценки");
        verify(course, never()).setStatus(any());
        verify(enrollments, never()).changeStatus(anyLong(), any(), any());
    }

    @Test
    void openingEnrollmentDoesNotStartCourse() {
        UserResponse professor = new UserResponse(2L, "Professor", "p@example.org", UserRole.PROFESSOR, true);
        Course course = new Course(2L, "Java", "", 10,
                LocalDate.of(2026, 1, 11), LocalDate.of(2026, 2, 11), CourseStatus.DRAFT);
        when(courses.findLockedById(1L)).thenReturn(Optional.of(course));
        when(users.requireActive(2L, UserRole.PROFESSOR)).thenReturn(professor);

        assertThat(service.changeStatus(1, CourseStatus.ENROLLMENT_OPEN).status())
                .isEqualTo(CourseStatus.ENROLLMENT_OPEN);
        verifyNoInteractions(enrollments);
    }

    @Test
    void cancellationDoesNotTouchAlreadyCompletedCourse() {
        Course course = mock(Course.class);
        when(course.getStatus()).thenReturn(CourseStatus.COMPLETED);
        when(courses.findLockedById(1L)).thenReturn(Optional.of(course));
        assertThatThrownBy(() -> service.changeStatus(1, CourseStatus.CANCELLED)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(enrollments);
    }
}
