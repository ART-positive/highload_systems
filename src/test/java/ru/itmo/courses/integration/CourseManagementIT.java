package ru.itmo.courses.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.itmo.courses.common.exception.ConflictException;
import ru.itmo.courses.course.model.CourseStatus;
import ru.itmo.courses.course.service.CourseService;
import ru.itmo.courses.enrollment.dto.EnrollmentRequest;
import ru.itmo.courses.enrollment.service.EnrollmentService;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(CourseManagementIT.TimeConfiguration.class)
class CourseManagementIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.9-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private CourseService courseService;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("TRUNCATE enrollment, course_program, course, study_program, app_user RESTART IDENTITY CASCADE");
        clock.set("2026-01-10T12:00:00Z");
    }

    @Test
    void userCrudNormalizesEmailAndPreservesHistory() throws Exception {
        long user = createUser("STUDENT", "STUDENT@EXAMPLE.ORG");
        mvc.perform(get("/api/v1/users/{id}", user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("student@example.org"));
        request(put("/api/v1/users/{id}", user), userBody("ADMIN", "changed@example.org", false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/users").param("size", "1")).andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(delete("/api/v1/users/{id}", user)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/{id}", user)).andExpect(status().isNotFound());
    }

    @Test
    void duplicateEmailReturnsHumanReadableConflict() throws Exception {
        createUser("STUDENT", "s@example.org");
        request(post("/api/v1/users"), userBody("STUDENT", "S@EXAMPLE.ORG", true))
                .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void programsHaveCrudAndCannotBeDeletedWhileLinked() throws Exception {
        long professor = createUser("PROFESSOR", "p@example.org");
        long course = createCourse(professor, 10);
        long program = createProgram("IVT");
        mvc.perform(get("/api/v1/programs/{id}", program)).andExpect(status().isOk());
        request(put("/api/v1/programs/{id}", program), programBody("IVT-NEW", false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("IVT-NEW"));
        mvc.perform(get("/api/v1/programs")).andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"));
        mvc.perform(put("/api/v1/courses/{id}/programs/{programId}", course, program)).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/courses/{id}/programs/{programId}", course, program)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM course_program", Long.class)).isEqualTo(1);
        mvc.perform(delete("/api/v1/programs/{id}", program)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/courses/{id}/programs", course)).andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"));
        mvc.perform(delete("/api/v1/courses/{id}/programs/{programId}", course, program)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/courses/{id}/programs/{programId}", course, program)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/programs/{id}", program)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/programs/{id}", program)).andExpect(status().isNotFound());
    }

    @Test
    void archivedProgramCannotReceiveNewCourses() throws Exception {
        long program = createProgram("IVT");
        long course = createCourse(createUser("PROFESSOR", "p@example.org"), 10);
        request(put("/api/v1/programs/{id}", program), programBody("IVT", true)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/courses/{id}/programs/{programId}", course, program)).andExpect(status().isConflict());
    }

    @Test
    void draftCourseCrudAndPublishedCourseRestrictions() throws Exception {
        long professor = createUser("PROFESSOR", "p@example.org");
        long course = createCourse(professor, 10);
        request(put("/api/v1/courses/{id}", course), courseBody(professor, 15)).andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(15));
        mvc.perform(get("/api/v1/courses/{id}", course)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/courses/{id}", course)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/courses/{id}", course)).andExpect(status().isNotFound());
        long published = createCourse(professor, 10);
        changeStatus(published, "ENROLLMENT_OPEN").andExpect(status().isOk());
        request(put("/api/v1/courses/{id}", published), courseBody(professor, 15)).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/courses/{id}", published)).andExpect(status().isConflict());
        changeStatus(published, "DRAFT").andExpect(status().isConflict());
    }

    @Test
    void preventsInvalidProfessorAndPastStart() throws Exception {
        long student = createUser("STUDENT", "s@example.org");
        request(post("/api/v1/courses"), courseBody(student, 10)).andExpect(status().isConflict());
        request(post("/api/v1/courses"), courseBody(999, 10)).andExpect(status().isNotFound());
        long professor = createUser("PROFESSOR", "p@example.org");
        request(put("/api/v1/users/{id}", professor), userBody("PROFESSOR", "p@example.org", false)).andExpect(status().isOk());
        request(post("/api/v1/courses"), courseBody(professor, 10)).andExpect(status().isConflict());
        clock.set("2027-01-01T00:00:00Z");
        request(post("/api/v1/courses"), courseBody(professor, 10)).andExpect(status().isConflict());
    }

    @Test
    void courseCatalogUsesRealManyToManyAndCountsFilteredRows() throws Exception {
        long professor = createUser("PROFESSOR", "p@example.org");
        long first = createCourse(professor, 10);
        long second = createCourse(professor, 10);
        long a = createProgram("IVT");
        long b = createProgram("SE");
        mvc.perform(put("/api/v1/courses/{id}/programs/{programId}", first, a)).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/courses/{id}/programs/{programId}", first, b)).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/courses/{id}/programs/{programId}", second, a)).andExpect(status().isNoContent());
        for (int page = 0; page < 2; page++) {
            long expectedId = page == 0 ? a : b;
            mvc.perform(get("/api/v1/courses/{id}/programs", first)
                            .param("size", "1").param("page", Integer.toString(page)))
                    .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "2"))
                    .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(expectedId));
        }
        mvc.perform(get("/api/v1/courses/{id}/programs", second))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].id").value(a));
        mvc.perform(get("/api/v1/courses").param("programId", Long.toString(a)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/v1/courses").param("programId", Long.toString(b)))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1"));
        changeStatus(first, "ENROLLMENT_OPEN").andExpect(status().isOk());
        mvc.perform(get("/api/v1/courses").param("status", "ENROLLMENT_OPEN"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1"));
    }

    @Test
    void cursorDoesNotSkipTheLookaheadRowAndNeverReturnsMoreThanFifty() throws Exception {
        long professor = createUser("PROFESSOR", "p@example.org");
        jdbc.update("""
                INSERT INTO course(professor_id,title,capacity,start_date,end_date,status)
                SELECT ?, 'Course ' || n, 10, DATE '2026-01-11', DATE '2026-02-11', 'DRAFT'
                FROM generate_series(1, 53) n
                """, professor);
        String first = mvc.perform(get("/api/v1/courses/scroll").param("size", "50"))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("X-Total-Count"))
                .andExpect(jsonPath("$.content", hasSize(50))).andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value(50)).andExpect(jsonPath("$.totalElements").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Number cursor = JsonPath.read(first, "$.nextCursor");
        mvc.perform(get("/api/v1/courses/scroll").param("afterId", cursor.toString()).param("size", "50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].id").value(51)).andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        mvc.perform(get("/api/v1/courses/scroll").param("afterId", "999")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users", "/api/v1/programs", "/api/v1/courses", "/api/v1/enrollments", "/api/v1/courses/scroll"})
    void allListsRejectOversizedPages(String endpoint) throws Exception {
        mvc.perform(get(endpoint).param("size", "51")).andExpect(status().isBadRequest());
        mvc.perform(get(endpoint).param("size", "0")).andExpect(status().isBadRequest());
    }

    @Test
    void invalidRequestsReturn400InsteadOf500() throws Exception {
        request(post("/api/v1/users"), Map.of("fullName", " ", "email", "bad", "role", "STUDENT", "active", true))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/courses").param("status", "UNKNOWN")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/courses/scroll").param("afterId", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users").param("page", "-1")).andExpect(status().isBadRequest());
        request(post("/api/v1/programs"), programBody("bad code", false)).andExpect(status().isBadRequest());
        request(post("/api/v1/courses"), Map.of("title", "Java", "description", "", "professorId", 1,
                "capacity", 1, "startDate", "2026-02-01", "endDate", "2026-01-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.dateRangeValid").exists());
    }

    @Test
    void enrollmentCrudIncludesDropReenrollmentAndGrade() throws Exception {
        long professor = createUser("PROFESSOR", "p@example.org");
        long student = createUser("STUDENT", "s@example.org");
        long course = createCourse(professor, 1);
        changeStatus(course, "ENROLLMENT_OPEN").andExpect(status().isOk());
        long enrollment = enroll(student, course);
        mvc.perform(get("/api/v1/enrollments/{id}", enrollment)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));
        request(post("/api/v1/enrollments"), enrollmentBody(student, course)).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/enrollments/{id}", enrollment)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/enrollments/{id}", enrollment)).andExpect(status().isNoContent());
        request(post("/api/v1/enrollments"), enrollmentBody(student, course)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(enrollment));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM enrollment", Long.class)).isEqualTo(1);
        request(patch("/api/v1/enrollments/{id}/grade", enrollment), Map.of("grade", 90)).andExpect(status().isConflict());
        changeStatus(course, "ENROLLMENT_CLOSED").andExpect(status().isOk());
        changeStatus(course, "IN_PROGRESS").andExpect(status().isConflict());
        clock.set("2026-01-11T12:00:00Z");
        changeStatus(course, "IN_PROGRESS").andExpect(status().isOk());
        mvc.perform(delete("/api/v1/enrollments/{id}", enrollment)).andExpect(status().isConflict());
        changeStatus(course, "COMPLETED").andExpect(status().isConflict());
        request(patch("/api/v1/enrollments/{id}/grade", enrollment), Map.of("grade", 101)).andExpect(status().isBadRequest());
        request(patch("/api/v1/enrollments/{id}/grade", enrollment), Map.of("grade", 0)).andExpect(status().isOk());
        request(patch("/api/v1/enrollments/{id}/grade", enrollment), Map.of("grade", 100)).andExpect(status().isOk());
        changeStatus(course, "COMPLETED").andExpect(status().isOk());
        mvc.perform(get("/api/v1/enrollments").param("studentId", Long.toString(student))
                        .param("courseId", Long.toString(course)).param("status", "COMPLETED"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].grade").value(100));
        request(patch("/api/v1/enrollments/{id}/grade", enrollment), Map.of("grade", 50)).andExpect(status().isConflict());
        changeStatus(course, "CANCELLED").andExpect(status().isConflict());
    }

    @Test
    void enrollmentChecksCapacityRoleActivityAndDates() throws Exception {
        long professor = createUser("PROFESSOR", "p@example.org");
        long first = createUser("STUDENT", "s1@example.org");
        long second = createUser("STUDENT", "s2@example.org");
        long course = createCourse(professor, 1);
        request(post("/api/v1/enrollments"), enrollmentBody(first, course)).andExpect(status().isConflict());
        changeStatus(course, "ENROLLMENT_OPEN").andExpect(status().isOk());
        request(post("/api/v1/enrollments"), enrollmentBody(professor, course)).andExpect(status().isConflict());
        request(put("/api/v1/users/{id}", second), userBody("STUDENT", "s2@example.org", false)).andExpect(status().isOk());
        request(post("/api/v1/enrollments"), enrollmentBody(second, course)).andExpect(status().isConflict());
        request(put("/api/v1/users/{id}", second), userBody("STUDENT", "s2@example.org", true)).andExpect(status().isOk());
        enroll(first, course);
        request(post("/api/v1/enrollments"), enrollmentBody(second, course)).andExpect(status().isConflict());
        clock.set("2026-01-11T00:00:00Z");
        request(post("/api/v1/enrollments"), enrollmentBody(second, course)).andExpect(status().isConflict());
    }

    @Test
    void historiesPreventDeletionAndRoleChanges() throws Exception {
        long professor = createUser("PROFESSOR", "p@example.org");
        long student = createUser("STUDENT", "s@example.org");
        long course = createCourse(professor, 1);
        changeStatus(course, "ENROLLMENT_OPEN").andExpect(status().isOk());
        enroll(student, course);
        for (long id : List.of(professor, student)) {
            mvc.perform(delete("/api/v1/users/{id}", id)).andExpect(status().isConflict());
            request(put("/api/v1/users/{id}", id), userBody("ADMIN", "a@example.org", true)).andExpect(status().isConflict());
        }
    }

    @Test
    void cancellationChangesOnlyActiveEnrollmentsAndKeepsHistory() throws Exception {
        long course = createCourse(createUser("PROFESSOR", "p@example.org"), 2);
        changeStatus(course, "ENROLLMENT_OPEN").andExpect(status().isOk());
        long first = enroll(createUser("STUDENT", "s1@example.org"), course);
        long second = enroll(createUser("STUDENT", "s2@example.org"), course);
        mvc.perform(delete("/api/v1/enrollments/{id}", second)).andExpect(status().isNoContent());
        changeStatus(course, "CANCELLED").andExpect(status().isOk());
        mvc.perform(get("/api/v1/enrollments/{id}", first)).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/v1/enrollments/{id}", second)).andExpect(jsonPath("$.status").value("DROPPED"));
        changeStatus(course, "CANCELLED").andExpect(status().isConflict());
    }

    @Test
    void failedCancellationRollsBackAlreadyUpdatedEnrollments() throws Exception {
        long course = createCourse(createUser("PROFESSOR", "p@example.org"), 2);
        changeStatus(course, "ENROLLMENT_OPEN").andExpect(status().isOk());
        enroll(createUser("STUDENT", "s1@example.org"), course);
        enroll(createUser("STUDENT", "s2@example.org"), course);
        jdbc.execute("""
                CREATE FUNCTION reject_course_cancellation() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.status = 'CANCELLED' THEN RAISE EXCEPTION 'Simulated database failure'; END IF;
                    RETURN NEW;
                END $$
                """);
        jdbc.execute("CREATE TRIGGER reject_cancel BEFORE UPDATE ON course FOR EACH ROW EXECUTE FUNCTION reject_course_cancellation()");
        try {
            assertThatThrownBy(() -> courseService.changeStatus(course, CourseStatus.CANCELLED))
                    .isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM course WHERE id = ?", String.class, course))
                    .isEqualTo("ENROLLMENT_OPEN");
            assertThat(jdbc.queryForList("SELECT status FROM enrollment ORDER BY id", String.class))
                    .containsExactly("ENROLLED", "ENROLLED");
        } finally {
            jdbc.execute("DROP TRIGGER reject_cancel ON course");
            jdbc.execute("DROP FUNCTION reject_course_cancellation()");
        }
    }

    @Test
    void onlyOneConcurrentRequestCanTakeLastSeat() throws Exception {
        long course = createCourse(createUser("PROFESSOR", "p@example.org"), 1);
        changeStatus(course, "ENROLLMENT_OPEN").andExpect(status().isOk());
        long first = createUser("STUDENT", "s1@example.org");
        long second = createUser("STUDENT", "s2@example.org");
        List<Boolean> results = concurrently(() -> tryEnroll(first, course), () -> tryEnroll(second, course));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM enrollment WHERE status = 'ENROLLED'", Long.class)).isEqualTo(1);
    }

    @Test
    void duplicateConcurrentRegistrationCreatesOneRow() throws Exception {
        long course = createCourse(createUser("PROFESSOR", "p@example.org"), 10);
        changeStatus(course, "ENROLLMENT_OPEN").andExpect(status().isOk());
        long student = createUser("STUDENT", "s@example.org");
        assertThat(concurrently(() -> tryEnroll(student, course), () -> tryEnroll(student, course)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM enrollment", Long.class)).isEqualTo(1);
    }

    @Test
    void concurrentCancellationCannotLeaveActiveEnrollment() throws Exception {
        long course = createCourse(createUser("PROFESSOR", "p@example.org"), 10);
        changeStatus(course, "ENROLLMENT_OPEN").andExpect(status().isOk());
        long student = createUser("STUDENT", "s@example.org");
        concurrently(() -> tryEnroll(student, course), () -> {
            courseService.changeStatus(course, CourseStatus.CANCELLED);
            return true;
        });
        assertThat(jdbc.queryForObject("SELECT status FROM course WHERE id = ?", String.class, course)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM enrollment WHERE status = 'ENROLLED'", Long.class)).isZero();
    }

    @Test
    void databaseConstraintsAlsoProtectDirectSqlWrites() throws Exception {
        long student = createUser("STUDENT", "s@example.org");
        long course = createCourse(createUser("PROFESSOR", "p@example.org"), 2);
        changeStatus(course, "ENROLLMENT_OPEN").andExpect(status().isOk());
        enroll(student, course);
        assertThatThrownBy(() -> jdbc.update("UPDATE enrollment SET grade = 101"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE course SET capacity = 0"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO enrollment(student_id,course_id,status,enrolled_at) VALUES (?,?,'ENROLLED',now())
                """, student, course)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void exposesOneOpenApiDocumentAndHealthEndpoint() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.0.1"))
                .andExpect(jsonPath("$.paths['/api/v1/users']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/programs']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/courses/scroll']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/enrollments']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/enrollments'].get.responses['200'].content.*.schema.type")
                        .value(hasItem("array")))
                .andExpect(jsonPath("$.paths['/api/v1/enrollments'].get.responses['200'].content.*.schema.items['$ref']")
                        .value(hasItem("#/components/schemas/EnrollmentResponse")))
                .andExpect(jsonPath("$.paths['/api/v1/courses/scroll'].get.responses['200'].content.*.schema['$ref']")
                        .value(hasItem("#/components/schemas/CursorResponseCourseResponse")))
                .andExpect(jsonPath("$.paths['/api/v1/enrollments'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/enrollments'].post.responses['201']").exists());
        for (String resource : List.of("users", "programs", "courses", "enrollments")) {
            mvc.perform(get("/api/" + resource)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void wildcardResponsesKeepConcreteSchemasAndNoContentDocumentation() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var document = json.readTree(result.getResponse().getContentAsString());
        for (String resource : List.of("users", "programs", "courses", "enrollments")) {
            var responses = document.at("/paths/~1api~1v1~1" + resource + "~1{id}/delete/responses");
            assertThat(responses.has("204")).as("DELETE %s documents 204", resource).isTrue();
            assertThat(responses.path("204").has("content")).isFalse();
            assertThat(responses.has("200")).isFalse();
        }
        for (String method : List.of("put", "delete")) {
            var response = document.at("/paths/~1api~1v1~1courses~1{id}~1programs~1{programId}/" + method + "/responses/204");
            assertThat(response.isMissingNode()).isFalse();
            assertThat(response.has("content")).isFalse();
        }
        var schemas = Map.of("users", "UserResponse", "programs", "ProgramResponse",
                "courses", "CourseResponse", "enrollments", "EnrollmentResponse");
        for (var entry : schemas.entrySet()) {
            String basePath = "/paths/~1api~1v1~1" + entry.getKey();
            String schemaPath = "/responses/200/content/application~1json/schema";
            assertThat(document.at(basePath + "/get" + schemaPath + "/items/$ref").asText())
                    .isEqualTo("#/components/schemas/" + entry.getValue());
            assertThat(document.at(basePath + "~1{id}/get" + schemaPath + "/$ref").asText())
                    .isEqualTo("#/components/schemas/" + entry.getValue());
        }
        assertThat(document.at("/components/schemas/CursorResponseCourseResponse/properties/content/items/$ref").asText())
                .isEqualTo("#/components/schemas/CourseResponse");
    }

    @Test
    void missingResourcesReturn404() throws Exception {
        for (String resource : List.of("users", "programs", "courses", "enrollments")) {
            mvc.perform(get("/api/v1/" + resource + "/999")).andExpect(status().isNotFound());
            mvc.perform(delete("/api/v1/" + resource + "/999")).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/courses/999/programs")).andExpect(status().isNotFound());
        request(post("/api/v1/enrollments"), enrollmentBody(999, 999)).andExpect(status().isNotFound());
        request(patch("/api/v1/enrollments/999/grade"), Map.of("grade", 90)).andExpect(status().isNotFound());
    }

    private boolean tryEnroll(long student, long course) {
        try {
            enrollmentService.enroll(new EnrollmentRequest(student, course));
            return true;
        } catch (ConflictException exception) {
            return false;
        }
    }

    private List<Boolean> concurrently(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(first, second).stream().map(task -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent test did not start");
                }
                return task.call();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(tasks.get(0).get(20, TimeUnit.SECONDS), tasks.get(1).get(20, TimeUnit.SECONDS));
        }
    }

    private ResultActions request(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return mvc.perform(builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private long created(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        var result = request(builder, body).andExpect(status().isCreated()).andReturn();
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(result.getRequest().getRequestURI() + "/" + id);
        return id;
    }

    private long createUser(String role, String email) throws Exception {
        return created(post("/api/v1/users"), userBody(role, email, true));
    }

    private Map<String, Object> userBody(String role, String email, boolean active) {
        return Map.of("fullName", "Test User", "email", email, "role", role, "active", active);
    }

    private long createProgram(String code) throws Exception {
        return created(post("/api/v1/programs"), programBody(code, false));
    }

    private Map<String, Object> programBody(String code, boolean archived) {
        return Map.of("code", code, "name", "Информатика", "description", "Программа бакалавриата", "archived", archived);
    }

    private long createCourse(long professor, int capacity) throws Exception {
        return created(post("/api/v1/courses"), courseBody(professor, capacity));
    }

    private Map<String, Object> courseBody(long professor, int capacity) {
        return Map.of("title", "Java Backend", "description", "Spring Boot", "professorId", professor,
                "capacity", capacity, "startDate", "2026-01-11", "endDate", "2026-02-11");
    }

    private ResultActions changeStatus(long course, String status) throws Exception {
        return request(patch("/api/v1/courses/{id}/status", course), Map.of("status", status));
    }

    private Map<String, Object> enrollmentBody(long student, long course) {
        return Map.of("studentId", student, "courseId", course);
    }

    private long enroll(long student, long course) throws Exception {
        return created(post("/api/v1/enrollments"), enrollmentBody(student, course));
    }
}
