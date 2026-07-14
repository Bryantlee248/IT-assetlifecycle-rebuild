package com.itam.common.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一分页对象。data 内嵌 { page, size, total, list }。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private int page;
    private int size;
    private long total;
    private List<T> list;

    public static <T> PageResult<T> of(int page, int size, long total, List<T> list) {
        return PageResult.<T>builder()
                .page(page)
                .size(size)
                .total(total)
                .list(list)
                .build();
    }
}
