package ru.itmo.courses.integration;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import ru.itmo.courses.common.exception.ConflictException;
import ru.itmo.courses.common.exception.DependencyUnavailableException;
import ru.itmo.courses.common.exception.NotFoundException;

@Component
@Slf4j
public class UserDirectoryFallback implements FallbackFactory<UserDirectory> {
    @Override
    public UserDirectory create(Throwable cause) {
        log.debug("Feign-вызов сервиса пользователей завершился ошибкой", cause);
        return (id, role) -> {
            if (cause instanceof NotFoundException missing) {
                throw missing;
            }
            if (cause instanceof ConflictException conflict) {
                throw conflict;
            }
            throw new DependencyUnavailableException("Сервис пользователей временно недоступен. Повторите запрос позже", cause);
        };
    }
}
