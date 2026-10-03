package ru.itmo.courses.common.api;

import java.util.List;

public record CursorResponse<T>(List<T> content, boolean hasNext, Long nextCursor) {
}
