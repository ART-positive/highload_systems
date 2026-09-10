package ru.itmo.courses.common;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import ru.itmo.courses.course.model.Course;
import ru.itmo.courses.course.model.CourseStatus;
import ru.itmo.courses.enrollment.model.Enrollment;
import ru.itmo.courses.enrollment.model.EnrollmentStatus;
import ru.itmo.courses.program.model.StudyProgram;
import ru.itmo.courses.user.model.AppUser;
import ru.itmo.courses.user.model.UserRole;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EntityValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    void rejectsInvalidEntityWithoutController() {
        AppUser user = new AppUser(" ", "not-an-email", null, true);
        assertThat(VALIDATOR.validate(user)).extracting(v -> v.getPropertyPath().toString())
                .contains("fullName", "email", "role");
    }

    @Test
    void validatesCourseDatesAndCapacity() {
        Course course = new Course(null, "Java", "", 0,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), CourseStatus.DRAFT);
        assertThat(VALIDATOR.validate(course)).extracting(v -> v.getPropertyPath().toString())
                .contains("professor", "capacity", "dateRangeValid");
    }

    @Test
    void validatesProgramAndEnrollmentConstraints() {
        assertThat(VALIDATOR.validate(new StudyProgram("invalid code", "Program", "", false)))
                .isNotEmpty();
        AppUser student = new AppUser("Student", "s@example.org", UserRole.STUDENT, true);
        Enrollment enrollment = new Enrollment(student, null, EnrollmentStatus.ENROLLED, 101, Instant.now());
        assertThat(VALIDATOR.validate(enrollment)).extracting(v -> v.getPropertyPath().toString())
                .contains("grade", "course");
    }
}
