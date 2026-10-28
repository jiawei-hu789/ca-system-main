package io.github.fkwhao.casystem.certificate.vo;

public record VerificationView(String serial, boolean signatureValid, boolean trustedIssuer,
            boolean periodValid, String revocationStatus, boolean certificateMatchesRecord,
            boolean valid, String fingerprintSha256) {}
