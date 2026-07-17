package com.waimaicps.common;

public record PageQuery(int page, int pageSize) {
    public PageQuery {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 100);
    }

    public int offset() {
        return (page - 1) * pageSize;
    }
}
