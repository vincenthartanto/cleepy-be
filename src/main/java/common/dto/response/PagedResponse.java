package common.dto.response;

import java.util.List;

public record PagedResponse<T>(
        List<T> items,
        long totalItems,
        int page,
        int size,
        int totalPages) {

    public static <T> PagedResponse<T> of(List<T> items, long totalItems, int page, int size) {
        int totalPages = (int) Math.ceil((double) totalItems / size);
        return new PagedResponse<>(items, totalItems, page, size, totalPages);
    }
}
