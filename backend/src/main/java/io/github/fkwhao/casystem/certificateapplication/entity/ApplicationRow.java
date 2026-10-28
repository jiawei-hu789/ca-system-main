package io.github.fkwhao.casystem.certificateapplication.entity;

public record ApplicationRow(long id, long userId, byte[] applicantName, byte[] email,
            String publicKeyPem, String status, String createdAt, String reviewedAt, byte[] reviewComment) {}
