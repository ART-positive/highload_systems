package ru.itmo.courses.user.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.itmo.courses.user.model.AppUser;

public interface UserRepository extends ReactiveCrudRepository<AppUser, Long> {
    Flux<AppUser> findAllBy(Pageable pageable);

    @Query("SELECT * FROM app_user WHERE id = :id FOR UPDATE")
    Mono<AppUser> findLockedById(long id);
}
