package io.github.fkwhao.casystem.certificate.entity;

public record Row(String serial, long applicationId, long userId, byte[] der,
            String issuedAt, String notBefore, String notAfter, String revokedAt, byte[] revocationReason) {}
