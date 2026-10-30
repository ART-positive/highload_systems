package ru.itmo.courses.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class Pagination {
    public static final int MAX_SIZE = 50;

    private Pagination() {
    }

    public static PageRequest page(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("page >= 0, size от 1 до 50");
        }
        return PageRequest.of(page, size, Sort.by("id"));
    }

    public static PageRequest cursor(long afterId, int size) {
        if (afterId < 0) {
            throw new IllegalArgumentException("afterId не может быть отрицательным");
        }
        return page(0, size);
    }
}
