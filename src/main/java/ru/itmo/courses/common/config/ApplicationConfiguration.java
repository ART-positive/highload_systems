package ru.itmo.courses.common.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import ru.itmo.courses.common.api.CursorResponse;
import ru.itmo.courses.course.dto.CourseResponse;

import java.time.Clock;

@Configuration
public class ApplicationConfiguration {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public OpenAPI openAPI() {
        OpenAPI api = new OpenAPI().info(new Info()
                .title("University Course Management API")
                .version("1.0")
                .description("Лабораторная №1. Монолит без авторизации. Все списки ограничены 50 записями. "
                        + "Страничные ответы содержат X-Total-Count; каталог /api/v1/courses/scroll — курсор без общего количества. "
                        + "Роли являются бизнес-атрибутами пользователей, идентичность вызывающего не проверяется."));
        // Wildcard в контроллере скрывает параметр типа, поэтому регистрируем схему курсора явно.
        ResolvedSchema cursorSchema = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(
                new ParameterizedTypeReference<CursorResponse<CourseResponse>>() { }.getType()).resolveAsRef(true));
        cursorSchema.referencedSchemas.forEach(api::schema);
        return api;
    }
}
