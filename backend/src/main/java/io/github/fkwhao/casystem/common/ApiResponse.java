package io.github.fkwhao.casystem.common;

public record ApiResponse<T>(String code, String message, T data) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("SUCCESS", message, data);
    }
}
