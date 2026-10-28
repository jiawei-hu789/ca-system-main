package io.github.fkwhao.casystem.certificate.mapper;

import io.github.fkwhao.casystem.certificate.entity.RevocationRow;
import io.github.fkwhao.casystem.certificate.entity.Row;

import io.github.fkwhao.casystem.certificate.CertificateAuthority;
import io.github.fkwhao.casystem.certificate.service.CertificateService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@lombok.RequiredArgsConstructor
public class CertificateMapper {
    private final JdbcTemplate jdbc;


    private Row readRow(ResultSet rs) throws SQLException {
        return new Row(rs.getString("serial"), rs.getLong("application_id"), rs.getLong("user_id"),
                rs.getBytes("certificate_der"), rs.getString("issued_at"), rs.getString("not_before"),
                rs.getString("not_after"), rs.getString("revoked_at"), rs.getBytes("revocation_reason"));
    }

    public Integer countIssued() {
        return jdbc.queryForObject("SELECT count(*) FROM certificates", Integer.class);
    }

    public void insert(CertificateAuthority.Issued issued, long applicationId, long userId) {
        jdbc.update("INSERT INTO certificates(serial,application_id,user_id,certificate_der,issued_at,not_before,not_after) VALUES(?,?,?,?,?,?,?)",
                issued.serial(), applicationId, userId, issued.der(), issued.issuedAt().toString(),
                issued.notBefore().toString(), issued.notAfter().toString());
    }

    public List<Row> findBySerial(String serial) {
        return jdbc.query("SELECT * FROM certificates WHERE serial=?", (rs, row) -> readRow(rs), serial);
    }

    public List<Row> findByUser(long userId, int limit, int offset) {
        return jdbc.query("SELECT * FROM certificates WHERE user_id=? ORDER BY issued_at DESC LIMIT ? OFFSET ?",
                (rs, row) -> readRow(rs), userId, limit, offset);
    }

    public List<Row> findAll(int limit, int offset) {
        return jdbc.query("SELECT * FROM certificates ORDER BY issued_at DESC LIMIT ? OFFSET ?",
                (rs, row) -> readRow(rs), limit, offset);
    }

    public List<RevocationRow> findRevocationRequests(String serial) {
        return jdbc.query("SELECT id,reason,status,created_at,completed_at FROM revocation_requests WHERE certificate_serial=? ORDER BY id",
                (rs, row) -> new RevocationRow(rs.getLong("id"), rs.getBytes("reason"),
                        rs.getString("status"), rs.getString("created_at"), rs.getString("completed_at")), serial);
    }

    public Integer countPendingRequests(String serial) {
        return jdbc.queryForObject("SELECT count(*) FROM revocation_requests WHERE certificate_serial=? AND status='PENDING'",
                Integer.class, serial);
    }

    public Long insertRevocationRequest(String serial, long userId, byte[] reason, String createdAt) {
        return jdbc.queryForObject("INSERT INTO revocation_requests(certificate_serial,user_id,reason,status,created_at) "
                        + "VALUES(?,?,?,?,?) RETURNING id", Long.class, serial, userId, reason, "PENDING", createdAt);
    }

    public int revokeIfActive(String serial, byte[] reason, String revokedAt) {
        return jdbc.update("UPDATE certificates SET revoked_at=?,revocation_reason=? WHERE serial=? AND revoked_at IS NULL",
                revokedAt, reason, serial);
    }

    public void completeRequests(String serial, String completedAt) {
        jdbc.update("UPDATE revocation_requests SET status='COMPLETED',completed_at=? WHERE certificate_serial=? AND status='PENDING'",
                completedAt, serial);
    }
}
