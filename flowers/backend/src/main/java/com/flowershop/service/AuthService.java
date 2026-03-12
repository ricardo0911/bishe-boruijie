package com.flowershop.service;

import com.flowershop.exception.BusinessException;
import com.flowershop.rbac.Role;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthAccountGateway authAccountGateway;
    private final AuthSessionGateway authSessionGateway;
    private final PasswordCodec passwordCodec;
    private final String adminAccount;
    private final String adminPassword;
    private final String adminDisplayName;
    private final String merchantAccount;
    private final String merchantPassword;
    private final String merchantDisplayName;

    public AuthService(
            AuthAccountGateway authAccountGateway,
            AuthSessionGateway authSessionGateway,
            PasswordCodec passwordCodec,
            @Value("${app.auth.accounts.admin.account:admin}") String adminAccount,
            @Value("${app.auth.accounts.admin.password:admin123456}") String adminPassword,
            @Value("${app.auth.accounts.admin.display-name:System Admin}") String adminDisplayName,
            @Value("${app.auth.accounts.merchant.account:merchant}") String merchantAccount,
            @Value("${app.auth.accounts.merchant.password:merchant123456}") String merchantPassword,
            @Value("${app.auth.accounts.merchant.display-name:Merchant Operator}") String merchantDisplayName
    ) {
        this.authAccountGateway = authAccountGateway;
        this.authSessionGateway = authSessionGateway;
        this.passwordCodec = passwordCodec;
        this.adminAccount = adminAccount;
        this.adminPassword = adminPassword;
        this.adminDisplayName = adminDisplayName;
        this.merchantAccount = merchantAccount;
        this.merchantPassword = merchantPassword;
        this.merchantDisplayName = merchantDisplayName;
    }

    @PostConstruct
    public void initializeAuthAccounts() {
        authAccountGateway.ensureSchema();
        authSessionGateway.ensureSchema();
        seedDefaultAccount("ADMIN", adminAccount, adminPassword, adminDisplayName, Role.SUPER_ADMIN.getCode());
        seedDefaultAccount("MERCHANT", merchantAccount, merchantPassword, merchantDisplayName, Role.MERCHANT.getCode());
    }

    public Map<String, Object> login(String loginType, String account, String password) {
        String normalizedType = normalizeLoginType(loginType);
        AuthAccount authAccount = loadEnabledAccount(normalizedType, normalizeAccount(account));
        if (!passwordCodec.matches(password, authAccount.passwordDigest())) {
            throw new BusinessException("AUTH_FAILED", "Invalid account or password");
        }
        return buildLoginResult(authAccount);
    }

    public Map<String, Object> register(String loginType, String account, String password, String displayName) {
        String normalizedType = normalizeLoginType(loginType);
        String normalizedAccount = normalizeAccount(account);
        validatePassword(password);

        if (normalizedAccount.isBlank()) {
            throw new BusinessException("ACCOUNT_INVALID", "Account is required");
        }
        if (authAccountGateway.findByLoginTypeAndAccount(normalizedType, normalizedAccount).isPresent()) {
            throw new BusinessException("ACCOUNT_EXISTS", "Account already exists");
        }

        AuthAccount authAccount = new AuthAccount(
                normalizedType,
                normalizedAccount,
                passwordCodec.encode(password),
                normalizeDisplayName(displayName, normalizedAccount),
                resolveRoleCode(normalizedType),
                true
        );
        authAccountGateway.save(authAccount);
        return buildLoginResult(authAccount);
    }

    public void changePassword(String loginType, String account, String oldPassword, String newPassword) {
        String normalizedType = normalizeLoginType(loginType);
        String normalizedAccount = normalizeAccount(account);
        validatePassword(newPassword);

        AuthAccount authAccount = loadEnabledAccount(normalizedType, normalizedAccount);
        if (!passwordCodec.matches(oldPassword, authAccount.passwordDigest())) {
            throw new BusinessException("AUTH_FAILED", "Current password is incorrect");
        }
        if (passwordCodec.matches(newPassword, authAccount.passwordDigest())) {
            throw new BusinessException("PASSWORD_UNCHANGED", "New password must be different from the current password");
        }

        authAccountGateway.updatePasswordDigest(normalizedType, normalizedAccount, passwordCodec.encode(newPassword));
        authSessionGateway.deleteByLoginTypeAndAccount(normalizedType, normalizedAccount);
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("UNAUTHORIZED", "Missing session token");
        }
        authSessionGateway.deleteByToken(token.trim());
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException("PASSWORD_INVALID", "New password must be at least 8 characters");
        }
    }

    private void seedDefaultAccount(String loginType, String account, String rawPassword, String displayName, String roleCode) {
        String normalizedAccount = normalizeAccount(account);
        if (authAccountGateway.findByLoginTypeAndAccount(loginType, normalizedAccount).isPresent()) {
            return;
        }

        authAccountGateway.save(new AuthAccount(
                loginType,
                normalizedAccount,
                passwordCodec.encode(rawPassword),
                displayName,
                roleCode,
                true
        ));
    }

    private AuthAccount loadEnabledAccount(String normalizedType, String account) {
        AuthAccount authAccount = authAccountGateway.findByLoginTypeAndAccount(normalizedType, account)
                .orElseThrow(() -> new BusinessException("AUTH_FAILED", "Invalid account or password"));
        if (!authAccount.enabled()) {
            throw new BusinessException("AUTH_DISABLED", "Account is disabled");
        }
        return authAccount;
    }

    private Map<String, Object> buildLoginResult(AuthAccount authAccount) {
        String token = createSession(authAccount);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("role", authAccount.roleCode());
        result.put("displayName", authAccount.displayName());
        result.put("landingPath", "MERCHANT".equals(authAccount.loginType()) ? "/merchant" : "/admin");
        result.put("account", authAccount.account());
        result.put("loginType", authAccount.loginType());
        return result;
    }

    private String createSession(AuthAccount authAccount) {
        authSessionGateway.deleteByLoginTypeAndAccount(authAccount.loginType(), authAccount.account());
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        authSessionGateway.save(new AuthSession(
                token,
                authAccount.loginType(),
                authAccount.account(),
                authAccount.roleCode()
        ));
        return token;
    }

    private String resolveRoleCode(String loginType) {
        return "MERCHANT".equals(loginType) ? Role.MERCHANT.getCode() : Role.SUPER_ADMIN.getCode();
    }

    private String normalizeDisplayName(String displayName, String fallbackAccount) {
        String normalized = displayName == null ? "" : displayName.trim();
        return normalized.isBlank() ? fallbackAccount : normalized;
    }

    private String normalizeAccount(String account) {
        return account == null ? "" : account.trim();
    }

    private String normalizeLoginType(String loginType) {
        String normalizedType = loginType == null ? "" : loginType.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedType) {
            case "ADMIN", "SUPER_ADMIN" -> "ADMIN";
            case "MERCHANT" -> "MERCHANT";
            default -> throw new BusinessException("AUTH_TYPE_INVALID", "Unsupported login type");
        };
    }
}
