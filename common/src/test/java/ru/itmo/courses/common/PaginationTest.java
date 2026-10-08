package ru.itmo.courses.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.itmo.courses.common.api.Pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PaginationTest {
    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,51", "0,-1"})
    void rejectsInvalidPageBounds(int page, int size) {
        assertThatIllegalArgumentException().isThrownBy(() -> Pagination.page(page, size));
    }

    @Test
    void preservesRequestedPageAndUsesStableSorting() {
        var pageable = Pagination.page(2, 50);
        assertThat(pageable.getOffset()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("id").isAscending()).isTrue();
    }

    @Test
    void cursorRejectsNegativePosition() {
        assertThatIllegalArgumentException().isThrownBy(() -> Pagination.cursor(-1, 20));
    }
}
