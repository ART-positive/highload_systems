package ru.itmo.courses.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import java.util.concurrent.RejectedExecutionException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail invalidBody(WebExchangeBindException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Проверьте поля запроса");
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class,
            IllegalArgumentException.class})
    public ProblemDetail invalidParameters(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Некорректные параметры запроса: " + exception.getMessage());
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ProblemDetail invalidJson() {
        return problem(HttpStatus.BAD_REQUEST,
                "Некорректный JSON: проверьте типы полей, названия статусов и формат дат YYYY-MM-DD");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integrityViolation() {
        return problem(HttpStatus.CONFLICT,
                "Операция нарушает ограничения данных: запись уже существует или используется другими объектами");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail concurrentUpdate() {
        return problem(HttpStatus.CONFLICT, "Данные изменяются другим запросом. Повторите операцию");
    }

    @ExceptionHandler({DependencyUnavailableException.class, RejectedExecutionException.class})
    public ProblemDetail unavailable(Exception exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Зависимый сервис недоступен или сервер перегружен. Повторите запрос позже");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception exception) {
        if (exception instanceof ErrorResponse response) {
            return response.getBody();
        }
        LOGGER.error("Unhandled request error", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера");
    }

    private ProblemDetail problem(HttpStatus status, String message) {
        return ProblemDetail.forStatusAndDetail(status, message);
    }
}
