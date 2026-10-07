package ru.itmo.courses.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import ru.itmo.courses.common.api.Pagination;
import ru.itmo.courses.common.exception.ConflictException;
import ru.itmo.courses.common.exception.NotFoundException;
import ru.itmo.courses.user.dto.UserRequest;
import ru.itmo.courses.user.dto.UserResponse;
import ru.itmo.courses.user.model.AppUser;
import ru.itmo.courses.user.model.UserRole;
import ru.itmo.courses.user.repository.UserRepository;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository users;

    public Mono<Page<UserResponse>> list(int page, int size) {
        var pageable = Pagination.page(page, size);
        return Mono.zip(users.findAllBy(pageable).map(this::toResponse).collectList(), users.count())
                .map(result -> new PageImpl<>(result.getT1(), pageable, result.getT2()));
    }

    public Mono<UserResponse> get(long id) {
        return users.findById(id).switchIfEmpty(Mono.error(missing(id))).map(this::toResponse);
    }

    public Mono<UserResponse> create(UserRequest request) {
        return users.save(new AppUser(request.fullName().strip(), normalizeEmail(request.email()),
                request.role(), request.active())).map(this::toResponse);
    }

    @Transactional
    public Mono<UserResponse> update(long id, UserRequest request) {
        return locked(id).flatMap(user -> {
            if (user.isReferenced() && user.getRole() != request.role()) {
                return Mono.error(new ConflictException("Нельзя менять роль пользователя, используемого другим сервисом"));
            }
            user.setFullName(request.fullName().strip());
            user.setEmail(normalizeEmail(request.email()));
            user.setRole(request.role());
            user.setActive(request.active());
            return users.save(user);
        }).map(this::toResponse);
    }

    @Transactional
    public Mono<Void> delete(long id) {
        return locked(id).flatMap(user -> {
            if (user.isReferenced()) {
                return Mono.error(new ConflictException("ID пользователя используется другим сервисом. Укажите active=false"));
            }
            return users.delete(user);
        });
    }

    /** Проверка и защита ID от удаления выполняются в одной реактивной транзакции. */
    @Transactional
    public Mono<UserResponse> reference(long id, UserRole expectedRole) {
        return locked(id).flatMap(user -> {
            if (!user.isActive() || user.getRole() != expectedRole) {
                return Mono.error(new ConflictException("Требуется активный пользователь с ролью " + expectedRole));
            }
            user.setReferenced(true);
            return users.save(user);
        }).map(this::toResponse);
    }

    private Mono<AppUser> locked(long id) {
        return users.findLockedById(id).switchIfEmpty(Mono.error(missing(id)));
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private NotFoundException missing(long id) {
        return new NotFoundException("Пользователь " + id + " не найден");
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.isActive());
    }
}
