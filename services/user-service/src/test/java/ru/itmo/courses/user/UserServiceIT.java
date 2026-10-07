package ru.itmo.courses.user;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.itmo.courses.user.dto.*;
import ru.itmo.courses.user.model.*;
import ru.itmo.courses.user.repository.UserRepository;
import ru.itmo.courses.user.service.UserService;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=file:../../config-repo/application.yml,file:../../config-repo/user-service.yml",
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false", "server.port=0"})
@AutoConfigureWebTestClient
@Testcontainers
class UserServiceIT {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.9-alpine");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + DB.getHost() + ":" + DB.getFirstMappedPort() + "/" + DB.getDatabaseName());
        registry.add("spring.r2dbc.username", DB::getUsername);
        registry.add("spring.r2dbc.password", DB::getPassword);
        registry.add("spring.flyway.url", DB::getJdbcUrl);
        registry.add("spring.flyway.user", DB::getUsername);
        registry.add("spring.flyway.password", DB::getPassword);
    }
    @Autowired WebTestClient web;
    @Autowired DatabaseClient database;
    @Autowired UserRepository repository;
    @Autowired UserService service;

    @BeforeEach
    void clear() {
        database.sql("TRUNCATE app_user RESTART IDENTITY").fetch().rowsUpdated().block(Duration.ofSeconds(5));
    }

    @Test
    void crudPaginationAndNormalization() {
        UserResponse user = create("USER@Example.org");
        assertThat(user.email()).isEqualTo("user@example.org");
        web.get().uri("/api/v1/users/" + user.id()).exchange().expectStatus().isOk().expectBody().jsonPath("$.role").isEqualTo("STUDENT");
        web.get().uri("/api/v1/users?size=1").exchange().expectStatus().isOk().expectHeader().valueEquals("X-Total-Count", "1")
                .expectBody().jsonPath("$.length()").isEqualTo(1);
        web.put().uri("/api/v1/users/" + user.id()).bodyValue(body("updated@example.org", UserRole.ADMIN, false))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.active").isEqualTo(false);
        web.delete().uri("/api/v1/users/" + user.id()).exchange().expectStatus().isNoContent().expectBody().isEmpty();
        web.get().uri("/api/v1/users/" + user.id()).exchange().expectStatus().isNotFound();
        web.put().uri("/api/v1/users/999").bodyValue(body("x@example.org", UserRole.STUDENT, true)).exchange().expectStatus().isNotFound();
        web.delete().uri("/api/v1/users/999").exchange().expectStatus().isNotFound();
    }

    @Test
    void validatesDtoEntityDatabaseAndPageSize() {
        create("s@example.org");
        web.post().uri("/api/v1/users").bodyValue(body("s@example.org", UserRole.STUDENT, true)).exchange().expectStatus().isEqualTo(409);
        web.post().uri("/api/v1/users").bodyValue(Map.of("fullName", "", "email", "bad", "role", "STUDENT", "active", true))
                .exchange().expectStatus().isBadRequest().expectBody().jsonPath("$.errors.email").exists();
        for (int size : List.of(0, 51, -1)) web.get().uri("/api/v1/users?size=" + size).exchange().expectStatus().isBadRequest();
        web.get().uri("/api/v1/users?page=-1").exchange().expectStatus().isBadRequest();
        web.get().uri("/api/v1/users/0").exchange().expectStatus().isBadRequest();
        web.post().uri("/api/v1/users").bodyValue(Map.of("role", "UNKNOWN")).exchange().expectStatus().isBadRequest();
        StepVerifier.create(repository.save(new AppUser(" ", "bad", null, true)))
                .expectError(ConstraintViolationException.class).verify(Duration.ofSeconds(5));
        String role = database.sql("SELECT role FROM app_user LIMIT 1").map(row -> row.get("role", String.class)).one().block();
        assertThat(role).isEqualTo("STUDENT");
    }

    @Test
    void referenceIsIdempotentAndGuardsDeleteAndRoleButAllowsDeactivation() {
        UserResponse user = create("s@example.org");
        for (int i = 0; i < 2; i++) web.put().uri("/internal/users/" + user.id() + "/reference?role=STUDENT")
                .exchange().expectStatus().isOk();
        web.delete().uri("/api/v1/users/" + user.id()).exchange().expectStatus().isEqualTo(409);
        web.put().uri("/api/v1/users/" + user.id()).bodyValue(body("s@example.org", UserRole.PROFESSOR, true))
                .exchange().expectStatus().isEqualTo(409);
        web.put().uri("/api/v1/users/" + user.id()).bodyValue(body("s@example.org", UserRole.STUDENT, false))
                .exchange().expectStatus().isOk();
        web.put().uri("/internal/users/" + user.id() + "/reference?role=STUDENT").exchange().expectStatus().isEqualTo(409);
        web.put().uri("/internal/users/999/reference?role=STUDENT").exchange().expectStatus().isNotFound();
    }

    @Test
    void failedReferenceRollsBackAndDoesNotProtectUser() {
        UserResponse user = create("s@example.org");
        web.put().uri("/internal/users/" + user.id() + "/reference?role=PROFESSOR").exchange().expectStatus().isEqualTo(409);
        assertThat(repository.findById(user.id()).block().isReferenced()).isFalse();
        web.delete().uri("/api/v1/users/" + user.id()).exchange().expectStatus().isNoContent();
    }

    @Test
    void concurrentReferenceAndDeleteCannotLeaveADeletedReferencedUser() {
        long id = create("s@example.org").id();
        var results = Mono.zip(
                service.reference(id, UserRole.STUDENT).map(value -> true).onErrorReturn(false),
                service.delete(id).thenReturn(true).onErrorReturn(false)).block(Duration.ofSeconds(10));
        assertThat(results.getT1() && results.getT2()).isFalse();
        if (results.getT1()) assertThat(repository.findById(id).block().isReferenced()).isTrue();
    }

    @Test
    void servesOpenApiAndHealthWithoutExposingInternalApi() {
        web.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.servers[0].url").isEqualTo("/")
                .jsonPath("$.paths['/api/v1/users']").exists().jsonPath("$.paths['/internal/users/{id}/reference']").doesNotExist()
                .jsonPath("$.paths['/api/v1/users/{id}'].delete.responses['204'].content").doesNotExist();
        web.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    private UserResponse create(String email) {
        return web.post().uri("/api/v1/users").bodyValue(body(email, UserRole.STUDENT, true)).exchange()
                .expectStatus().isCreated().expectHeader().exists("Location").expectBody(UserResponse.class).returnResult().getResponseBody();
    }

    private UserRequest body(String email, UserRole role, boolean active) {
        return new UserRequest(" Student ", email, role, active);
    }
}
