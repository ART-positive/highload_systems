package ru.itmo.courses.common.api;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.util.List;

public final class PageResponses {
    private PageResponses() {
    }

    public static <T> ResponseEntity<List<T>> of(Page<T> page) {
        return ResponseEntity.ok()
                .header("X-Total-Count", Long.toString(page.getTotalElements()))
                .body(page.getContent());
    }
}
