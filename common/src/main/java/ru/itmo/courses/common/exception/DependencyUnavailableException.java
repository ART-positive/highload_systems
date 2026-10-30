package ru.itmo.courses.common.exception;
public class DependencyUnavailableException extends RuntimeException {
    public DependencyUnavailableException(String message, Throwable cause) { super(message, cause); }
}
