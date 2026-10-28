package io.github.fkwhao.casystem.common.mapper;



import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@lombok.RequiredArgsConstructor
public class AuditMapper {
    private final JdbcTemplate jdbc;


    public void insert(long actorId, String operation, String targetType, String targetId, String createdAt) {
        jdbc.update("INSERT INTO operation_logs(actor_id,operation,target_type,target_id,created_at) VALUES(?,?,?,?,?)",
                actorId, operation, targetType, targetId, createdAt);
    }
}
