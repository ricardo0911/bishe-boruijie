package com.flowershop.service;

import com.flowershop.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private final InMemoryAuthAccountGateway accountGateway = new InMemoryAuthAccountGateway();
    private final InMemoryAuthSessionGateway sessionGateway = new InMemoryAuthSessionGateway();
    private final PasswordCodec passwordCodec = new PasswordCodec();
    private final AuthService authService = new AuthService(
        accountGateway,
        sessionGateway,
        passwordCodec,
        "admin",
        "admin123456",
        "System Admin",
        "merchant",
        "merchant123456",
        "Merchant Operator"
    );

    @Test
    void bootstrapsDefaultAccountsIntoGateway() {
        authService.initializeAuthAccounts();

        assertTrue(accountGateway.findByLoginTypeAndAccount("ADMIN", "admin").isPresent());
        assertTrue(accountGateway.findByLoginTypeAndAccount("MERCHANT", "merchant").isPresent());
    }

    @Test
    void authenticatesAgainstStoredPasswordDigestAndCreatesServerSession() {
        authService.initializeAuthAccounts();

        Map<String, Object> result = authService.login("ADMIN", "admin", "admin123456");

        String token = String.valueOf(result.get("token"));
        assertFalse(token.isBlank());
        assertNotEquals("shared-demo-token", token);
        assertEquals("SUPER_ADMIN", result.get("role"));
        assertEquals("System Admin", result.get("displayName"));
        assertEquals("/admin", result.get("landingPath"));
        assertTrue(sessionGateway.findByToken(token).isPresent());
    }

    @Test
    void registersNewMerchantAccountAndCreatesSession() {
        authService.initializeAuthAccounts();

        Map<String, Object> result = authService.register("MERCHANT", "merchant_new", "merchant888", "New Merchant");

        String token = String.valueOf(result.get("token"));
        assertEquals("merchant_new", result.get("account"));
        assertEquals("MERCHANT", result.get("role"));
        assertEquals("/merchant", result.get("landingPath"));
        assertTrue(accountGateway.findByLoginTypeAndAccount("MERCHANT", "merchant_new").isPresent());
        assertTrue(sessionGateway.findByToken(token).isPresent());
    }

    @Test
    void rejectsDuplicateAccountRegistration() {
        authService.initializeAuthAccounts();

        assertThrows(BusinessException.class, () -> authService.register("ADMIN", "admin", "admin999999", "Another Admin"));
    }

    @Test
    void changesPasswordAndRevokesExistingSessions() {
        authService.initializeAuthAccounts();

        Map<String, Object> firstLogin = authService.login("ADMIN", "admin", "admin123456");
        String firstToken = String.valueOf(firstLogin.get("token"));
        assertTrue(sessionGateway.findByToken(firstToken).isPresent());

        authService.changePassword("ADMIN", "admin", "admin123456", "new-password-123");

        assertFalse(sessionGateway.findByToken(firstToken).isPresent());
        assertThrows(BusinessException.class, () -> authService.login("ADMIN", "admin", "admin123456"));

        Map<String, Object> relogin = authService.login("ADMIN", "admin", "new-password-123");
        String newToken = String.valueOf(relogin.get("token"));
        assertEquals("admin", relogin.get("account"));
        assertTrue(sessionGateway.findByToken(newToken).isPresent());
    }

    @Test
    void passwordCodecMatchesEncodedPasswords() {
        String digest = passwordCodec.encode("hello-123456");

        assertFalse("hello-123456".equals(digest));
        assertTrue(passwordCodec.matches("hello-123456", digest));
    }

    private static final class InMemoryAuthAccountGateway implements AuthAccountGateway {

        private final Map<String, AuthAccount> store = new HashMap<>();

        @Override
        public void ensureSchema() {
        }

        @Override
        public Optional<AuthAccount> findByLoginTypeAndAccount(String loginType, String account) {
            return Optional.ofNullable(store.get(key(loginType, account)));
        }

        @Override
        public void save(AuthAccount authAccount) {
            store.put(key(authAccount.loginType(), authAccount.account()), authAccount);
        }

        @Override
        public void updatePasswordDigest(String loginType, String account, String passwordDigest) {
            AuthAccount existing = store.get(key(loginType, account));
            if (existing == null) {
                return;
            }
            store.put(
                key(loginType, account),
                new AuthAccount(
                    existing.loginType(),
                    existing.account(),
                    passwordDigest,
                    existing.displayName(),
                    existing.roleCode(),
                    existing.enabled()
                )
            );
        }

        private String key(String loginType, String account) {
            return loginType + "::" + account;
        }
    }

    private static final class InMemoryAuthSessionGateway implements AuthSessionGateway {

        private final Map<String, AuthSession> sessions = new HashMap<>();

        @Override
        public void ensureSchema() {
        }

        @Override
        public void save(AuthSession authSession) {
            sessions.put(authSession.token(), authSession);
        }

        @Override
        public Optional<AuthSession> findByToken(String token) {
            return Optional.ofNullable(sessions.get(token));
        }

        @Override
        public void deleteByLoginTypeAndAccount(String loginType, String account) {
            sessions.entrySet().removeIf(entry ->
                loginType.equals(entry.getValue().loginType()) && account.equals(entry.getValue().account())
            );
        }

        @Override
        public void deleteByToken(String token) {
            sessions.remove(token);
        }
    }
}