package io.github.fkwhao.casystem.common;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StorageStateMapper {
    private final JdbcTemplate jdbc;
    public StorageStateMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<byte[]> keyCheck() {
        return jdbc.query("SELECT value FROM system_state WHERE name='storage_key_check'",
                (rs, row) -> rs.getBytes(1));
    }

    public Integer countApplications() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM applications", Integer.class);
    }

    public void insertKeyCheck(byte[] value) {
        jdbc.update("INSERT OR IGNORE INTO system_state(name,value) VALUES('storage_key_check',?)", value);
    }
}
