package ru.itmo.courses.user.config;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.itmo.courses.user.model.AppUser;

@Component
@RequiredArgsConstructor
public class EntityValidation implements BeforeConvertCallback<AppUser> {
    private final Validator validator;
    @Override
    public Mono<AppUser> onBeforeConvert(AppUser user, SqlIdentifier table) {
        var violations = validator.validate(user);
        return violations.isEmpty() ? Mono.just(user) : Mono.error(new ConstraintViolationException(violations));
    }
}
