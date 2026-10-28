package io.github.fkwhao.casystem.auth.entity;

public record Account(long id, String username, String passwordHash, String role) {}
