package com.flowershop.service;

import com.flowershop.config.AdminTokenInterceptor;
import com.flowershop.exception.BusinessException;
import com.flowershop.rbac.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ensureSchemaCreatesReviewsTable() {
        ReviewService reviewService = new ReviewService(jdbcTemplate);

        reviewService.ensureSchema();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(sqlCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS reviews"));
        assertTrue(sql.contains("COMMENT='product reviews'"));
    }

    @Test
    void createReviewStoresProductReviewIntoReviewsTable() {
        ReviewService reviewService = new ReviewService(jdbcTemplate);
        when(jdbcTemplate.queryForObject(
            contains("FROM customer_order"),
            eq(Integer.class),
            eq(101L),
            eq(7L)
        )).thenReturn(1);
        when(jdbcTemplate.queryForObject(
            contains("FROM order_item"),
            eq(Integer.class),
            eq(101L),
            eq(88L)
        )).thenReturn(1);
        when(jdbcTemplate.queryForObject(
            contains("FROM reviews"),
            eq(Integer.class),
            eq(101L),
            eq(88L),
            eq(7L)
        )).thenReturn(0);

        reviewService.createReview(101L, 88L, 7L, 5, "Great flowers", List.of("/uploads/a.jpg", "/uploads/b.jpg"));

        verify(jdbcTemplate).update(
            contains("INSERT INTO reviews"),
            eq(101L),
            eq(88L),
            eq(7L),
            eq(5),
            eq("Great flowers"),
            eq("[\"/uploads/a.jpg\",\"/uploads/b.jpg\"]")
        );
    }

    @Test
    void createReviewRejectsProductOutsideOrder() {
        ReviewService reviewService = new ReviewService(jdbcTemplate);
        when(jdbcTemplate.queryForObject(
            contains("FROM customer_order"),
            eq(Integer.class),
            eq(101L),
            eq(7L)
        )).thenReturn(1);
        when(jdbcTemplate.queryForObject(
            contains("FROM order_item"),
            eq(Integer.class),
            eq(101L),
            eq(99L)
        )).thenReturn(0);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> reviewService.createReview(101L, 99L, 7L, 5, "Invalid product", null)
        );

        assertEquals("ORDER_PRODUCT_NOT_FOUND", exception.getCode());
    }

    @Test
    void listReviewsByUserIncludesOrderAndProductInfo() {
        ReviewService reviewService = new ReviewService(jdbcTemplate);
        List<Map<String, Object>> expected = List.of(Map.of(
            "id", 1L,
            "orderNo", "FO202603090001",
            "productId", 88L,
            "productTitle", "Rose Bouquet",
            "score", 5,
            "content", "Love it"
        ));
        when(jdbcTemplate.queryForList(
            contains("JOIN product p"),
            eq(7L)
        )).thenReturn(expected);

        List<Map<String, Object>> result = reviewService.listReviewsByUser(7L);

        assertEquals(1, result.size());
        assertEquals("FO202603090001", result.get(0).get("orderNo"));
        assertEquals("Rose Bouquet", result.get(0).get("productTitle"));
        assertEquals(5, result.get(0).get("score"));
        assertEquals(List.of(), result.get(0).get("images"));
    }

    @Test
    void merchantListAllReviewsFiltersByCurrentMerchant() {
        ReviewService reviewService = new ReviewService(jdbcTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminTokenInterceptor.REQUEST_ATTR_ROLES, List.of(Role.MERCHANT));
        request.setAttribute(AdminTokenInterceptor.REQUEST_ATTR_ACCOUNT, "merchant-a");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        List<Map<String, Object>> expected = List.of(Map.of(
            "id", 2L,
            "userName", "Alice",
            "productTitle", "Sunflower Basket",
            "score", 4
        ));
        when(jdbcTemplate.queryForList(
            contains("WHERE p.merchant_account = ?"),
            eq("merchant-a")
        )).thenReturn(expected);

        List<Map<String, Object>> result = reviewService.listAllReviews();

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).get("userName"));
        assertEquals("Sunflower Basket", result.get(0).get("productTitle"));
        assertEquals(4, result.get(0).get("score"));
        assertEquals(List.of(), result.get(0).get("images"));
    }

    @Test
    void replyReviewUpdatesReplyAndReplyTimeForOwnedReview() {
        ReviewService reviewService = new ReviewService(jdbcTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminTokenInterceptor.REQUEST_ATTR_ROLES, List.of(Role.MERCHANT));
        request.setAttribute(AdminTokenInterceptor.REQUEST_ATTR_ACCOUNT, "merchant-a");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(jdbcTemplate.queryForObject(
            contains("JOIN product p ON p.id = r.product_id"),
            eq(Integer.class),
            eq(12L),
            eq("merchant-a")
        )).thenReturn(1);

        reviewService.replyReview(12L, "Thanks for your support");

        verify(jdbcTemplate).update(
            contains("UPDATE reviews SET reply = ?, reply_time = NOW()"),
            eq("Thanks for your support"),
            eq(12L)
        );
    }

    @Test
    void deleteReviewRemovesOwnedReview() {
        ReviewService reviewService = new ReviewService(jdbcTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminTokenInterceptor.REQUEST_ATTR_ROLES, List.of(Role.MERCHANT));
        request.setAttribute(AdminTokenInterceptor.REQUEST_ATTR_ACCOUNT, "merchant-a");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(jdbcTemplate.queryForObject(
            contains("JOIN product p ON p.id = r.product_id"),
            eq(Integer.class),
            eq(12L),
            eq("merchant-a")
        )).thenReturn(1);

        reviewService.deleteReview(12L);

        verify(jdbcTemplate).update("DELETE FROM reviews WHERE id = ?", 12L);
    }
}
