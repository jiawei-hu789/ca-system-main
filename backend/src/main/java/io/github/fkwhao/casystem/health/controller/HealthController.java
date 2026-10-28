package io.github.fkwhao.casystem.health.controller;

import io.github.fkwhao.casystem.common.ApiResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<HealthStatus> health() {
        return ApiResponse.success("后端服务已启动", new HealthStatus(
                "UP", "ca-system", OffsetDateTime.now(ZoneOffset.UTC).toString()));
    }

    public record HealthStatus(String status, String service, String timestamp) {}
}
