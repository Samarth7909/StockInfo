package com.indira.opsconsole.controller.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Generic paginated response envelope. */
@Getter @Builder
public class PageResponse<T> {
    private List<T> items;
    private int     page;
    private int     pageSize;
    private long    totalItems;
    private int     totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
}
