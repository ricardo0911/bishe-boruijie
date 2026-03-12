package com.flowershop.config;

import com.flowershop.exception.BusinessException;
import com.flowershop.rbac.RbacService;
import com.flowershop.service.AuthSession;
import com.flowershop.service.AuthSessionGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminTokenInterceptorTest {

    private final InMemoryAuthSessionGateway sessionGateway = new InMemoryAuthSessionGateway();
    private final AdminTokenInterceptor interceptor = new AdminTokenInterceptor(new RbacService(), sessionGateway);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(interceptor, "authEnabled", true);
    }

    @Test
    void allowsPublicAfterSaleApplyWithoutToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/after-sales");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
    }

    @Test
    void allowsPublicAfterSaleLookupWithoutToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/after-sales/ORD202603080001");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
    }

    @Test
    void allowsPublicSupportTicketCreateWithoutToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/support-tickets");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
    }

    @Test
    void allowsPublicSupportTicketUserListWithoutToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/support-tickets/user/12");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
    }

    @Test
    void attachesMerchantContextForPublicProductRequestWhenTokenProvided() {
        sessionGateway.save(new AuthSession("merchant-token", "MERCHANT", "merchant-a", "MERCHANT"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader("X-Admin-Token", "merchant-token");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertEquals("merchant-a", request.getAttribute(AdminTokenInterceptor.REQUEST_ATTR_ACCOUNT));
        assertEquals("MERCHANT", request.getAttribute(AdminTokenInterceptor.REQUEST_ATTR_LOGIN_TYPE));
    }

    @Test
    void rejectsAfterSaleListWithoutToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/after-sales/list");

        assertThrows(BusinessException.class, () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void rejectsSupportTicketListWithoutToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/support-tickets");

        assertThrows(BusinessException.class, () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
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
