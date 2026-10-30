package ru.itmo.courses.user.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().addServersItem(new Server().url("/"))
                .info(new Info().title("User Service API").version("1.0")
                .description("Лабораторная №2. Пользователи: WebFlux, Reactor и R2DBC. "
                        + "Роли пока являются атрибутами данных; авторизация относится к следующей лабораторной."));
    }
}
