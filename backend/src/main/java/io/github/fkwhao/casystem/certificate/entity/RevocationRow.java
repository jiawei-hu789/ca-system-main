package io.github.fkwhao.casystem.certificate.entity;

public record RevocationRow(long id, byte[] reason, String status, String createdAt, String completedAt) {}
