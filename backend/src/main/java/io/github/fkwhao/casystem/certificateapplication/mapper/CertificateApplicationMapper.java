package io.github.fkwhao.casystem.certificateapplication.mapper;

import io.github.fkwhao.casystem.certificateapplication.entity.ApplicationRow;
import io.github.fkwhao.casystem.certificateapplication.entity.EvidenceMetaRow;
import io.github.fkwhao.casystem.certificateapplication.entity.EvidenceRow;

import io.github.fkwhao.casystem.certificateapplication.service.CertificateApplicationService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@lombok.RequiredArgsConstructor
public class CertificateApplicationMapper {
    private final JdbcTemplate jdbc;


    private ApplicationRow readRow(ResultSet rs) throws SQLException {
        return new ApplicationRow(rs.getLong("id"), rs.getLong("user_id"),
                rs.getBytes("applicant_name"), rs.getBytes("email"), rs.getString("public_key_pem"),
                rs.getString("status"), rs.getString("created_at"), rs.getString("reviewed_at"),
                rs.getBytes("review_comment"));
    }

    public List<ApplicationRow> findById(long id) {
        return jdbc.query("SELECT * FROM applications WHERE id=?", (rs, row) -> readRow(rs), id);
    }

    public List<ApplicationRow> findByUser(long userId, int limit, int offset) {
        return jdbc.query("SELECT * FROM applications WHERE user_id=? ORDER BY id DESC LIMIT ? OFFSET ?",
                (rs, row) -> readRow(rs), userId, limit, offset);
    }

    public List<ApplicationRow> findAll(String status, int limit, int offset) {
        return status == null
                ? jdbc.query("SELECT * FROM applications ORDER BY id DESC LIMIT ? OFFSET ?",
                        (rs, row) -> readRow(rs), limit, offset)
                : jdbc.query("SELECT * FROM applications WHERE status=? ORDER BY id DESC LIMIT ? OFFSET ?",
                        (rs, row) -> readRow(rs), status, limit, offset);
    }

    public Long insert(long userId, byte[] name, byte[] email, String publicKey, String createdAt) {
        return jdbc.queryForObject("INSERT INTO applications(user_id,applicant_name,email,public_key_pem,status,created_at) "
                        + "VALUES(?,?,?,?,?,?) RETURNING id", Long.class,
                userId, name, email, publicKey, "PENDING", createdAt);
    }

    public void insertEvidence(long applicationId, byte[] filename, String contentType, int sizeBytes, byte[] content) {
        jdbc.update("INSERT INTO evidence_files(application_id,original_name,content_type,size_bytes,content) VALUES(?,?,?,?,?)",
                applicationId, filename, contentType, sizeBytes, content);
    }

    public List<EvidenceMetaRow> findEvidenceMetadata(long applicationId) {
        return jdbc.query("SELECT id,original_name,content_type,size_bytes FROM evidence_files WHERE application_id=? ORDER BY id",
                (rs, row) -> new EvidenceMetaRow(rs.getLong("id"), rs.getBytes("original_name"),
                        rs.getString("content_type"), rs.getLong("size_bytes")), applicationId);
    }

    public List<EvidenceRow> findEvidence(long id) {
        return jdbc.query("SELECT e.original_name,e.content_type,e.content,a.id AS application_id "
                        + "FROM evidence_files e JOIN applications a ON a.id=e.application_id WHERE e.id=?",
                (rs, row) -> new EvidenceRow(rs.getLong("application_id"),
                        rs.getBytes("original_name"), rs.getString("content_type"), rs.getBytes("content")), id);
    }

    public int reviewIfPending(long id, String status, String reviewedAt, long reviewedBy, byte[] reviewComment) {
        return jdbc.update("UPDATE applications SET status=?,reviewed_at=?,reviewed_by=?,review_comment=? WHERE id=? AND status='PENDING'",
                status, reviewedAt, reviewedBy, reviewComment, id);
    }
}
