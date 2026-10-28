package io.github.fkwhao.casystem.common.service.impl;

import io.github.fkwhao.casystem.common.service.AuditService;

import io.github.fkwhao.casystem.common.mapper.AuditMapper;

import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
@lombok.RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {
    private final AuditMapper mapper;
public void record(long actorId, String operation, String targetType, String targetId) {
        mapper.insert(actorId, operation, targetType, targetId, Instant.now().toString());
    }
}
