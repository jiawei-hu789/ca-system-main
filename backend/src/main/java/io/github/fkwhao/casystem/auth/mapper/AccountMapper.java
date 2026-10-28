package io.github.fkwhao.casystem.auth.mapper;

import io.github.fkwhao.casystem.auth.entity.Account;

import io.github.fkwhao.casystem.auth.service.AccountService;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@lombok.RequiredArgsConstructor
public class AccountMapper {
    private final JdbcTemplate jdbc;



    public List<Account> findByUsername(String username) {
        return jdbc.query("SELECT id,username,password_hash,role FROM users WHERE username=?",
                (rs, row) -> new Account(rs.getLong("id"), rs.getString("username"),
                        rs.getString("password_hash"), rs.getString("role")), username);
    }

    public Long insert(String username, String passwordHash, String role, String createdAt) {
        return jdbc.queryForObject(
                "INSERT INTO users(username,password_hash,role,created_at) VALUES(?,?,?,?) RETURNING id",
                Long.class, username, passwordHash, role, createdAt);
    }
}
