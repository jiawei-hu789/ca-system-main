package io.github.fkwhao.casystem.auth.service.impl;

import io.github.fkwhao.casystem.auth.entity.Account;
import io.github.fkwhao.casystem.auth.vo.AccountView;

import io.github.fkwhao.casystem.auth.service.AccountService;

import io.github.fkwhao.casystem.auth.mapper.AccountMapper;
import io.github.fkwhao.casystem.common.ApiException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountServiceImpl implements AccountService, UserDetailsService, ApplicationRunner {
    private final AccountMapper mapper;
    private final PasswordEncoder encoder;
    private final String adminUsername;
    private final String adminPassword;

    public AccountServiceImpl(AccountMapper mapper, PasswordEncoder encoder,
            @Value("${ca.admin-username:admin}") String adminUsername,
            @Value("${ca.admin-password:}") String adminPassword) {
        this.mapper = mapper;
        this.encoder = encoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }


    private List<Account> find(String username) {
        return mapper.findByUsername(username);
    }

    public Account requireAccount(String username) {
        return find(username).stream().findFirst().orElseThrow(() -> ApiException.notFound("用户不存在"));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = find(username).stream().findFirst()
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        return User.withUsername(account.username()).password(account.passwordHash()).roles(account.role()).build();
    }

    public AccountView view(String username) {
        Account account = requireAccount(username);
        return new AccountView(Long.toString(account.id()), account.username(), account.role());
    }

    public AccountView register(String username, String password) {
        validateCredentials(username, password);
        if (username.equalsIgnoreCase(adminUsername)) throw ApiException.conflict("用户名不可用");
        if (!find(username).isEmpty()) throw ApiException.conflict("用户名已存在");
        Long id = mapper.insert(username, encoder.encode(password), "USER", Instant.now().toString());
        return new AccountView(Long.toString(id), username, "USER");
    }

    private void validateCredentials(String username, String password) {
        if (username == null || !username.matches("[A-Za-z0-9_.-]{3,32}")) {
            throw ApiException.badRequest("用户名须为3至32位字母、数字、点、下划线或连字符");
        }
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("密码至少12个字符，UTF-8编码不超过72字节");
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) return;
        validateCredentials(adminUsername, adminPassword);
        if (find(adminUsername).isEmpty()) {
            mapper.insert(adminUsername, encoder.encode(adminPassword), "ADMIN", Instant.now().toString());
        } else if (!"ADMIN".equals(requireAccount(adminUsername).role())) {
            throw new IllegalStateException("配置的管理员用户名已被普通用户使用");
        }
    }
}
