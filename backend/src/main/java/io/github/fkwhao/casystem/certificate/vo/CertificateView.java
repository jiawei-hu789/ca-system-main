package io.github.fkwhao.casystem.certificate.vo;

import java.util.List;

public record CertificateView(String serial, String applicationId, String userId, String issuedAt,
            String notBefore, String notAfter, String status, String revokedAt, String revocationReason,
            List<RevocationView> revocationRequests) {}
