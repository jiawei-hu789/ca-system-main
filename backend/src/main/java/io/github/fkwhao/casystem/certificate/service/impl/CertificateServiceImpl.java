package io.github.fkwhao.casystem.certificate.service.impl;

import io.github.fkwhao.casystem.common.Pagination;
import io.github.fkwhao.casystem.auth.entity.Account;
import io.github.fkwhao.casystem.certificate.dto.ReasonRequest;
import io.github.fkwhao.casystem.certificate.entity.RevocationRow;
import io.github.fkwhao.casystem.certificate.entity.Row;
import io.github.fkwhao.casystem.certificate.vo.CertificateView;
import io.github.fkwhao.casystem.certificate.vo.RevocationView;
import io.github.fkwhao.casystem.certificate.vo.VerificationView;

import io.github.fkwhao.casystem.certificate.service.CertificateService;

import io.github.fkwhao.casystem.auth.service.AccountService;
import io.github.fkwhao.casystem.certificate.CertificateAuthority;
import io.github.fkwhao.casystem.certificate.mapper.CertificateMapper;
import io.github.fkwhao.casystem.certificateapplication.service.CertificateApplicationService;
import io.github.fkwhao.casystem.common.ApiException;
import io.github.fkwhao.casystem.common.DataProtection;
import io.github.fkwhao.casystem.common.service.AuditService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@lombok.RequiredArgsConstructor
public class CertificateServiceImpl implements CertificateService {
    private final CertificateMapper mapper;
    private final AccountService accounts;
    private final CertificateAuthority authority;
    private final DataProtection protection;
    private final AuditService audit;




    private String normalizeSerial(String serial) {
        if (serial == null || !serial.matches("(?i)[0-9a-f]{1,40}")) throw ApiException.badRequest("证书序列号格式无效");
        return serial.toLowerCase(Locale.ROOT);
    }

    private Row find(String serial) {
        String normalized = normalizeSerial(serial);
        return mapper.findBySerial(normalized)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("证书不存在"));
    }

    private Account authorize(String username, Row certificate) {
        Account account = accounts.requireAccount(username);
        if (!account.role().equals("ADMIN") && account.id() != certificate.userId()) {
            throw ApiException.notFound("证书不存在");
        }
        return account;
    }

    private String status(Row row) {
        if (row.revokedAt() != null) return "REVOKED";
        Instant now = Instant.now();
        if (now.isBefore(Instant.parse(row.notBefore()))) return "NOT_YET_VALID";
        if (now.isAfter(Instant.parse(row.notAfter()))) return "EXPIRED";
        return "VALID";
    }

    private List<RevocationView> requests(String serial) {
        List<RevocationRow> rows = mapper.findRevocationRequests(serial);
        return rows.stream().map(row -> new RevocationView(Long.toString(row.id()),
                protection.decryptText(row.reason()), row.status(), row.createdAt(), row.completedAt())).toList();
    }

    private CertificateView view(Row row) {
        return new CertificateView(row.serial(), Long.toString(row.applicationId()), Long.toString(row.userId()),
                row.issuedAt(), row.notBefore(), row.notAfter(), status(row), row.revokedAt(),
                row.revocationReason() == null ? null : protection.decryptText(row.revocationReason()),
                requests(row.serial()));
    }

    public List<CertificateView> mine(String username, int limit, int offset) {
        Pagination.check(limit, offset);
        var account = accounts.requireAccount(username);
        return mapper.findByUser(account.id(), limit, offset).stream().map(this::view).toList();
    }

    public List<CertificateView> adminList(int limit, int offset) {
        Pagination.check(limit, offset);
        return mapper.findAll(limit, offset).stream().map(this::view).toList();
    }

    public CertificateView get(String username, String serial) {
        Row certificate = find(serial);
        authorize(username, certificate);
        return view(certificate);
    }

    public byte[] download(String username, String serial) {
        Row certificate = find(serial);
        authorize(username, certificate);
        return CertificateAuthority.toPem(certificate.der()).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private String checkedReason(ReasonRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()
                || request.reason().trim().length() > 500) {
            throw ApiException.badRequest("吊销原因须为1至500个字符");
        }
        return request.reason().trim();
    }

    @Transactional
    public RevocationView requestRevocation(String username, String serial, ReasonRequest request) {
        Row certificate = find(serial);
        Account account = accounts.requireAccount(username);
        if (!account.role().equals("USER") || account.id() != certificate.userId()) {
            throw ApiException.notFound("证书不存在");
        }
        if (certificate.revokedAt() != null) throw ApiException.conflict("证书已吊销");
        String reason = checkedReason(request);
        Integer pending = mapper.countPendingRequests(certificate.serial());
        if (pending != null && pending > 0) throw ApiException.conflict("该证书已有待处理的吊销申请");
        Long id = mapper.insertRevocationRequest(certificate.serial(), account.id(),
                protection.encryptText(reason), Instant.now().toString());
        audit.record(account.id(), "REVOCATION_REQUESTED", "CERTIFICATE", certificate.serial());
        return requests(certificate.serial()).stream().filter(item -> item.id().equals(Long.toString(id))).findFirst().orElseThrow();
    }

    @Transactional
    public CertificateView revoke(String username, String serial, ReasonRequest request) {
        Account admin = accounts.requireAccount(username);
        if (!admin.role().equals("ADMIN")) throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "FORBIDDEN", "仅管理员可以吊销");
        Row certificate = find(serial);
        if (certificate.revokedAt() != null) throw ApiException.conflict("证书已吊销");
        String reason = checkedReason(request);
        String now = Instant.now().toString();
        int updated = mapper.revokeIfActive(certificate.serial(), protection.encryptText(reason), now);
        if (updated != 1) throw ApiException.conflict("证书状态已发生变化");
        mapper.completeRequests(certificate.serial(), now);
        audit.record(admin.id(), "CERTIFICATE_REVOKED", "CERTIFICATE", certificate.serial());
        return view(find(certificate.serial()));
    }

    public VerificationView verify(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 65536) {
            throw ApiException.badRequest("证书文件须大于0且不超过64KB");
        }
        try {
            byte[] bytes = file.getBytes();
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            var decoded = CertificateFactory.getInstance("X.509").generateCertificates(input);
            if (decoded.size() != 1) throw ApiException.badRequest("请提交单张X.509证书");
            X509Certificate certificate = (X509Certificate) decoded.iterator().next();
            X509Certificate root = authority.rootCertificate();
            boolean signatureValid;
            try { certificate.verify(root.getPublicKey()); signatureValid = true; }
            catch (Exception ex) { signatureValid = false; }
            boolean trustedIssuer = signatureValid && certificate.getIssuerX500Principal().equals(root.getSubjectX500Principal())
                    && certificate.getBasicConstraints() < 0;
            boolean periodValid;
            try { certificate.checkValidity(); root.checkValidity(); periodValid = true; }
            catch (Exception ex) { periodValid = false; }
            String serial = certificate.getSerialNumber().toString(16);
            List<Row> rows = mapper.findBySerial(serial);
            boolean matches = !rows.isEmpty() && MessageDigest.isEqual(rows.get(0).der(), certificate.getEncoded());
            String revocationStatus = !matches ? "UNKNOWN" : rows.get(0).revokedAt() == null ? "GOOD" : "REVOKED";
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
            boolean valid = trustedIssuer && periodValid && matches && revocationStatus.equals("GOOD");
            return new VerificationView(serial, signatureValid, trustedIssuer, periodValid,
                    revocationStatus, matches, valid, fingerprint);
        } catch (ApiException ex) {
            throw ex;
        } catch (CertificateException | IOException | ClassCastException ex) {
            throw ApiException.badRequest("证书解析失败，请提供单张PEM或DER格式的X.509证书");
        } catch (NoSuchAlgorithmException ex) {
            throw ApiException.unavailable("证书摘要算法不可用");
        }
    }

    public byte[] rootPem() {
        try {
            return CertificateAuthority.toPem(authority.rootCertificate().getEncoded())
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        } catch (java.security.cert.CertificateEncodingException ex) {
            throw ApiException.unavailable("CA证书编码失败");
        }
    }
}
