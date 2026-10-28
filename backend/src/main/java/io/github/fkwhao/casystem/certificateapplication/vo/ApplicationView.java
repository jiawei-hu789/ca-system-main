package io.github.fkwhao.casystem.certificateapplication.vo;

import java.util.List;

public record ApplicationView(String id, String userId, String applicantName, String email,
            String publicKeyPem, String status, String createdAt, String reviewedAt,
            String reviewComment, List<EvidenceView> evidenceFiles) {}
