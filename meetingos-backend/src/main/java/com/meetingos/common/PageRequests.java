package com.meetingos.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PageRequests {

    private static final int MAX_PAGE_SIZE = 200;

    private PageRequests() {
    }

    public static PageRequest of(int page, int size) {
        return PageRequest.of(normalizePage(page), normalizeSize(size));
    }

    public static PageRequest of(int page, int size, Sort sort) {
        return PageRequest.of(normalizePage(page), normalizeSize(size), sort);
    }

    private static int normalizePage(int page) {
        if (page < 0) {
            throw BusinessException.badRequest("page 不能小于 0");
        }
        return page;
    }

    private static int normalizeSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw BusinessException.badRequest("size 必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
        return size;
    }
}
