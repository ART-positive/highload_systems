package ru.itmo.courses.integration;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.itmo.courses.common.config.BlockingExecutor;
import ru.itmo.courses.common.exception.*;
import ru.itmo.courses.course.dto.*;
import ru.itmo.courses.course.model.CourseStatus;
import ru.itmo.courses.course.service.CourseService;
import ru.itmo.courses.enrollment.dto.*;
import ru.itmo.courses.enrollment.service.EnrollmentService;
import ru.itmo.courses.user.model.UserRole;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=file:../../config-repo/application.yml,file:../../config-repo/course-service.yml",
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false", "server.port=0",
        "resilience4j.circuitbreaker.instances.userDirectory.wait-duration-in-open-state=1s"})
@AutoConfigureWebTestClient
@Testcontainers
@Import(CourseServiceIT.TimeConfiguration.class)
class CourseServiceIT {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.9-alpine");
    static final AtomicInteger userStatus = new AtomicInteger(200);
    static final AtomicInteger requests = new AtomicInteger();
    static final AtomicLong delayMillis = new AtomicLong();
    static final HttpServer USERS = userServer();

    static HttpServer userServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/users/", exchange -> {
                requests.incrementAndGet();
                try { Thread.sleep(delayMillis.get()); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                long id = Long.parseLong(exchange.getRequestURI().getPath().split("/")[3]);
                String expected = exchange.getRequestURI().getQuery().split("=")[1];
                String role = id == 1 ? "PROFESSOR" : "STUDENT";
                int status = userStatus.get();
                if (status == 200 && id == 999) status = 404;
                if (status == 200 && !role.equals(expected)) status = 409;
                String body = status == 200
                        ? "{\"id\":" + id + ",\"fullName\":\"User\",\"email\":\"user@example.org\",\"role\":\"" + role + "\",\"active\":true}"
                        : "{\"detail\":\"Unavailable or invalid user\"}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                try {
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } finally { exchange.close(); }
            });
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return server;
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    @AfterAll static void stopUserServer() { USERS.stop(0); }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registry.add("spring.cloud.openfeign.client.config.user-service.url",
                () -> "http://127.0.0.1:" + USERS.getAddress().getPort());
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    @Autowired WebTestClient web;
    @Autowired JdbcTemplate jdbc;
    @Autowired CourseService courses;
    @Autowired EnrollmentService enrollments;
    @Autowired UserDirectory users;
    @Autowired MutableClock clock;
    @Autowired BlockingExecutor blocking;
    @Autowired CircuitBreakerRegistry breakers;

    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE enrollment, course_program, course, study_program RESTART IDENTITY CASCADE");
        clock.set("2026-01-10T12:00:00Z");
        userStatus.set(200);
        delayMillis.set(0);
        requests.set(0);
        breakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        web = web.mutate().responseTimeout(Duration.ofSeconds(10)).build();
    }

    @Test void crudProgramsAndCoursesThroughReactiveHttp() {
        long course = createCourse(4);
        var programBody = Map.of("code", "IVT", "name", "Информатика", "description", "", "archived", false);
        var program = web.post().uri("/api/v1/programs").bodyValue(programBody).exchange().expectStatus().isCreated()
                .expectBody(ru.itmo.courses.program.dto.ProgramResponse.class).returnResult().getResponseBody();
        long id = program.id();
        web.get().uri("/api/v1/programs/" + id).exchange().expectStatus().isOk();
        web.put().uri("/api/v1/programs/" + id).bodyValue(programBody).exchange().expectStatus().isOk();
        web.put().uri("/api/v1/courses/" + course).bodyValue(courseBody(5)).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.capacity").isEqualTo(5);
        for (int i = 0; i < 2; i++) web.put().uri("/api/v1/courses/" + course + "/programs/" + id).exchange().expectStatus().isNoContent();
        web.get().uri("/api/v1/courses/" + course + "/programs?size=1").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Total-Count", "1");
        web.get().uri("/api/v1/courses?programId=" + id).exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Total-Count", "1").expectBody().jsonPath("$[0].id").isEqualTo(course);
        web.delete().uri("/api/v1/programs/" + id).exchange().expectStatus().isEqualTo(409);
        web.delete().uri("/api/v1/courses/" + course + "/programs/" + id).exchange().expectStatus().isNoContent();
        web.put().uri("/api/v1/programs/" + id).bodyValue(Map.of("code", "IVT", "name", "P", "description", "", "archived", true))
                .exchange().expectStatus().isOk();
        web.put().uri("/api/v1/courses/" + course + "/programs/" + id).exchange().expectStatus().isEqualTo(409);
        web.delete().uri("/api/v1/programs/" + id).exchange().expectStatus().isNoContent();
        web.delete().uri("/api/v1/courses/" + course).exchange().expectStatus().isNoContent();
        web.get().uri("/api/v1/courses/" + course).exchange().expectStatus().isNotFound();
    }

    @Test void enrollmentLifecyclePreservesHistoryAndGrades() {
        long course = createCourse(2);
        status(course, "ENROLLMENT_OPEN", 200);
        long enrollment = enroll(course, 2, 201);
        web.get().uri("/api/v1/enrollments/" + enrollment).exchange().expectStatus().isOk();
        web.get().uri("/api/v1/enrollments?courseId=" + course + "&studentId=2&status=ENROLLED")
                .exchange().expectStatus().isOk().expectHeader().valueEquals("X-Total-Count", "1");
        enroll(course, 2, 409);
        web.patch().uri("/api/v1/enrollments/" + enrollment + "/grade").bodyValue(Map.of("grade", 90)).exchange().expectStatus().isEqualTo(409);
        web.delete().uri("/api/v1/enrollments/" + enrollment).exchange().expectStatus().isNoContent();
        web.delete().uri("/api/v1/enrollments/" + enrollment).exchange().expectStatus().isNoContent();
        assertThat(enroll(course, 2, 200)).isEqualTo(enrollment);
        status(course, "ENROLLMENT_CLOSED", 200);
        status(course, "IN_PROGRESS", 409);
        clock.set("2026-01-11T12:00:00Z");
        status(course, "IN_PROGRESS", 200);
        web.delete().uri("/api/v1/enrollments/" + enrollment).exchange().expectStatus().isEqualTo(409);
        status(course, "COMPLETED", 409);
        web.patch().uri("/api/v1/enrollments/" + enrollment + "/grade").bodyValue(Map.of("grade", 101)).exchange().expectStatus().isBadRequest();
        web.patch().uri("/api/v1/enrollments/" + enrollment + "/grade").bodyValue(Map.of("grade", 100)).exchange().expectStatus().isOk();
        status(course, "COMPLETED", 200);
        web.get().uri("/api/v1/enrollments/" + enrollment).exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.status").isEqualTo("COMPLETED").jsonPath("$.grade").isEqualTo(100);
        status(course, "CANCELLED", 409);
        status(course, "DRAFT", 409);
    }

    @Test void cancellationAndRollbackRemainLocalTransactions() {
        long course = createCourse(2);
        status(course, "ENROLLMENT_OPEN", 200);
        long enrollment = enroll(course, 2, 201);
        enroll(course, 1, 409);
        web.delete().uri("/api/v1/courses/" + course).exchange().expectStatus().isEqualTo(409);
        jdbc.execute("CREATE FUNCTION reject_cancel() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='CANCELLED' THEN RAISE EXCEPTION 'test rollback'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_cancel BEFORE UPDATE ON course FOR EACH ROW EXECUTE FUNCTION reject_cancel()");
        try {
            assertThatThrownBy(() -> courses.changeStatus(course, CourseStatus.CANCELLED)).isInstanceOf(RuntimeException.class);
            assertThat(enrollments.get(enrollment).status().name()).isEqualTo("ENROLLED");
            assertThat(courses.get(course).status()).isEqualTo(CourseStatus.ENROLLMENT_OPEN);
        } finally {
            jdbc.execute("DROP TRIGGER reject_cancel ON course");
            jdbc.execute("DROP FUNCTION reject_cancel()");
        }
        status(course, "CANCELLED", 200);
        assertThat(enrollments.get(enrollment).status().name()).isEqualTo("CANCELLED");
        status(course, "CANCELLED", 409);
    }

    @Test void paginationCursorAndValidation() {
        for (int i = 0; i < 3; i++) createCourse(2);
        web.get().uri("/api/v1/courses?size=1").exchange().expectStatus().isOk().expectHeader().valueEquals("X-Total-Count", "3");
        web.get().uri("/api/v1/courses/scroll?size=2").exchange().expectStatus().isOk()
                .expectHeader().doesNotExist("X-Total-Count").expectBody().jsonPath("$.hasNext").isEqualTo(true).jsonPath("$.nextCursor").isEqualTo(2);
        web.get().uri("/api/v1/courses/scroll?size=2&afterId=2").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.content[0].id").isEqualTo(3).jsonPath("$.hasNext").isEqualTo(false);
        for (String endpoint : List.of("courses", "courses/scroll", "programs", "enrollments", "courses/1/programs"))
            web.get().uri("/api/v1/" + endpoint + "?size=51").exchange().expectStatus().isBadRequest();
        for (String endpoint : List.of("courses", "programs", "enrollments")) {
            web.get().uri("/api/v1/" + endpoint + "/999").exchange().expectStatus().isNotFound();
            web.delete().uri("/api/v1/" + endpoint + "/999").exchange().expectStatus().isNotFound();
        }
        web.get().uri("/api/v1/courses/999/programs").exchange().expectStatus().isNotFound();
        web.get().uri("/api/v1/courses?status=BAD").exchange().expectStatus().isBadRequest();
        web.post().uri("/api/v1/courses").bodyValue(Map.of("title", "")).exchange().expectStatus().isBadRequest();
        web.post().uri("/api/v1/courses").contentType(MediaType.APPLICATION_JSON).bodyValue("{").exchange().expectStatus().isBadRequest();
        web.post().uri("/api/v1/courses").bodyValue(new CourseRequest("Java", "", 1L, 3, LocalDate.of(2025,1,1), LocalDate.of(2025,2,1))).exchange().expectStatus().isEqualTo(409);
    }

    @Test void concurrentLastSeatAndDuplicateEnrollmentStaySafe() throws Exception {
        long course = createCourse(1);
        status(course, "ENROLLMENT_OPEN", 200);
        assertThat(concurrently(() -> tryEnroll(course,2), () -> tryEnroll(course,3))).containsExactlyInAnyOrder(true,false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM enrollment", Long.class)).isEqualTo(1);
        long duplicateCourse = createCourse(5);
        status(duplicateCourse, "ENROLLMENT_OPEN", 200);
        assertThat(concurrently(() -> tryEnroll(duplicateCourse,4), () -> tryEnroll(duplicateCourse,4))).containsExactlyInAnyOrder(true,false);
    }

    @Test void concurrentCancellationCannotLeaveActiveEnrollment() throws Exception {
        long course = createCourse(2);
        status(course, "ENROLLMENT_OPEN", 200);
        concurrently(() -> tryEnroll(course, 2), () -> { courses.changeStatus(course, CourseStatus.CANCELLED); return true; });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM enrollment WHERE status='ENROLLED'", Long.class)).isZero();
    }

    @Test void feignBusinessErrorsDoNotOpenCircuit() {
        for (int i=0; i<5; i++) {
            assertThatThrownBy(() -> users.requireActive(999,UserRole.STUDENT)).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> users.requireActive(1,UserRole.STUDENT)).isInstanceOf(ConflictException.class);
        }
        assertThat(breakers.circuitBreaker("userDirectory").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(users.requireActive(1, UserRole.PROFESSOR).id()).isEqualTo(1);
    }

    @Test void circuitOpensShortCircuitsAndRecoversWithoutCreatingCourse() throws Exception {
        userStatus.set(503);
        for (int i=0; i<3; i++) web.post().uri("/api/v1/courses").bodyValue(courseBody(1)).exchange().expectStatus().isEqualTo(503);
        CircuitBreaker breaker = breakers.circuitBreaker("userDirectory");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int hits = requests.get();
        web.post().uri("/api/v1/courses").bodyValue(courseBody(1)).exchange().expectStatus().isEqualTo(503);
        assertThat(requests.get()).isEqualTo(hits);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM course", Long.class)).isZero();
        userStatus.set(200);
        Thread.sleep(1200);
        users.requireActive(1,UserRole.PROFESSOR);
        users.requireActive(1,UserRole.PROFESSOR);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test void timeoutFailsClosedAndJpaRunsOutsideReactorEventLoop() {
        delayMillis.set(3000);
        long start = System.nanoTime();
        web.post().uri("/api/v1/courses").bodyValue(courseBody(1)).exchange().expectStatus().isEqualTo(503);
        assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(5));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM course", Long.class)).isZero();
        assertThat(blocking.call(() -> Thread.currentThread().getName()).block()).startsWith("course-blocking-");
        assertThat(blocking.call(() -> courses.list(null,null,0,20).getTotalElements()).block()).isZero();
    }

    @Test void swaggerPreservesWildcardSchemasAndEmpty204() {
        web.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.servers[0].url").isEqualTo("/")
                .jsonPath("$.paths['/api/v1/courses/{id}'].delete.responses['204']").exists()
                .jsonPath("$.paths['/api/v1/courses/{id}'].delete.responses['204'].content").doesNotExist()
                .jsonPath("$.components.schemas.CursorResponseCourseResponse.properties.content.items['$ref']").isEqualTo("#/components/schemas/CourseResponse");
        web.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    private CourseRequest courseBody(int capacity) {
        return new CourseRequest("Java","",1L,capacity,LocalDate.of(2026,1,11),LocalDate.of(2026,2,11));
    }
    private long createCourse(int capacity) {
        var response=web.post().uri("/api/v1/courses").bodyValue(courseBody(capacity)).exchange().expectStatus().isCreated()
                .expectBody(CourseResponse.class).returnResult().getResponseBody();
        return response.id();
    }
    private void status(long course,String status,int expected) {
        web.patch().uri("/api/v1/courses/"+course+"/status").bodyValue(Map.of("status",status)).exchange().expectStatus().isEqualTo(expected);
    }
    private long enroll(long course,long student,int expected) {
        var response=web.post().uri("/api/v1/enrollments").bodyValue(new EnrollmentRequest(student,course)).exchange().expectStatus().isEqualTo(expected);
        return expected<300? response.expectBody(EnrollmentResponse.class).returnResult().getResponseBody().id():-1;
    }
    private boolean tryEnroll(long course,long student) {
        try { enrollments.enroll(new EnrollmentRequest(student,course)); return true; }
        catch(ConflictException ex) { return false; }
    }
    private List<Boolean> concurrently(Callable<Boolean> first,Callable<Boolean> second) throws Exception {
        var ready=new CountDownLatch(2);
        var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var futures=List.of(first,second).stream().map(task->executor.submit(()->{
                ready.countDown();
                if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Не удалось запустить конкурентный тест");
                return task.call();
            })).toList();
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));
        }
    }
}
