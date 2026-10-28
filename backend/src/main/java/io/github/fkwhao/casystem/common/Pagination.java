package io.github.fkwhao.casystem.common;

public final class Pagination {
    private Pagination() {}

    public static void check(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) throw ApiException.badRequest("分页参数无效");
    }
}
