package ru.itmo.courses.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationConfiguration {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("University Course Management API")
                .version("1.0")
                .description("Лабораторная №1. Монолит без авторизации. Все списки ограничены 50 записями. "
                        + "Страничные ответы содержат X-Total-Count; каталог /api/courses/scroll — курсор без общего количества. "
                        + "Роли являются бизнес-атрибутами пользователей, идентичность вызывающего не проверяется."));
    }
}
