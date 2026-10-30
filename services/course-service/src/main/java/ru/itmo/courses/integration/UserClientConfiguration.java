package ru.itmo.courses.integration;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import ru.itmo.courses.common.exception.ConflictException;
import ru.itmo.courses.common.exception.NotFoundException;

public class UserClientConfiguration {
    @Bean
    ErrorDecoder userErrorDecoder() {
        return (method, response) -> switch (response.status()) {
            case 404 -> new NotFoundException("Пользователь не найден");
            case 409 -> new ConflictException("Пользователь неактивен или его роль не подходит для операции");
            default -> new ErrorDecoder.Default().decode(method, response);
        };
    }
}
