package io.github.fkwhao.casystem.auth.controller;

import io.github.fkwhao.casystem.auth.vo.AccountView;

import io.github.fkwhao.casystem.auth.service.AccountService;
import io.github.fkwhao.casystem.common.ApiException;
import io.github.fkwhao.casystem.common.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@lombok.RequiredArgsConstructor
public class AuthController {
    private final AccountService accounts;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfRepository;



    public record Credentials(@NotBlank String username, @NotBlank String password) {}
    public record CsrfView(String headerName, String token) {}

    @GetMapping("/csrf")
    public ApiResponse<CsrfView> csrf(CsrfToken token, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.success("CSRF令牌", new CsrfView(token.getHeaderName(), token.getToken()));
    }

    @PostMapping("/register")
    public ApiResponse<AccountView> register(@Valid @RequestBody Credentials request) {
        return ApiResponse.success("注册成功", accounts.register(request.username(), request.password()));
    }

    @PostMapping("/login")
    public ApiResponse<AccountView> login(@Valid @RequestBody Credentials body,
            HttpServletRequest request, HttpServletResponse response) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
            if (request.getSession(false) != null) request.changeSessionId();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);
            csrfRepository.saveToken(null, request, response);
            return ApiResponse.success("登录成功，请重新获取CSRF令牌", accounts.view(auth.getName()));
        } catch (AuthenticationException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
        }
    }

    @GetMapping("/me")
    public ApiResponse<AccountView> me(Principal principal) {
        return ApiResponse.success("当前用户", accounts.view(principal.getName()));
    }
}
