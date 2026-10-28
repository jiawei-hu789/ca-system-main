package io.github.fkwhao.casystem.certificateapplication.entity;

public record EvidenceRow(long applicationId, byte[] name, String type, byte[] content) {}
