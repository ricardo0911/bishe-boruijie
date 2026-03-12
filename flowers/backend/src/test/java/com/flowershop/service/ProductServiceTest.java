package com.flowershop.service;

import com.flowershop.config.AdminTokenInterceptor;
import com.flowershop.dto.ProductView;
import com.flowershop.exception.BusinessException;
import com.flowershop.rbac.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void merchantCreateProductStoresMerchantAccount() {
        ProductService productService = new ProductService(jdbcTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminTokenInterceptor.REQUEST_ATTR_ROLES, List.of(Role.MERCHANT));
        request.setAttribute(AdminTokenInterceptor.REQUEST_ATTR_ACCOUNT, "merchant-a");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        productService.createProduct(
            "Rose Bouquet",
            "BOUQUET",
            "Romance",
            new BigDecimal("199"),
            new BigDecimal("20"),
            new BigDecimal("15"),
            "Fresh roses",
            "/uploads/rose.jpg"
        );

        verify(jdbcTemplate).update(
            contains("merchant_account"),
            eq("Rose Bouquet"),
            eq("BOUQUET"),
            eq("Romance"),
            eq(new BigDecimal("199")),
            eq(new BigDecimal("20")),
            eq(new BigDecimal("15")),
            eq("Fresh roses"),
            eq("/uploads/rose.jpg"),
            eq("merchant-a")
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void listProductsMapsMerchantInfo() throws Exception {
        ProductService productService = new ProductService(jdbcTemplate);
        doReturn(List.of()).when(jdbcTemplate)
            .query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ProductView>>any(), org.mockito.ArgumentMatchers.<Object[]>any());

        productService.listProducts(null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RowMapper> rowMapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), rowMapperCaptor.capture(), org.mockito.ArgumentMatchers.<Object[]>any());

        assertTrue(sqlCaptor.getValue().contains("LEFT JOIN auth_account aa"));
        assertTrue(sqlCaptor.getValue().contains("AS merchant_name"));

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("title")).thenReturn("Rose Bouquet");
        when(rs.getString("type")).thenReturn("BOUQUET");
        when(rs.getString("category")).thenReturn("DAILY");
        when(rs.getBigDecimal("auto_price")).thenReturn(new BigDecimal("188.00"));
        when(rs.getString("cover_image")).thenReturn("/uploads/rose.jpg");
        when(rs.getString("status")).thenReturn("ON_SALE");
        when(rs.getString("merchant_account")).thenReturn("merchant-a");
        when(rs.getString("merchant_name")).thenReturn("Shop A");

        ProductView productView = (ProductView) rowMapperCaptor.getValue().mapRow(rs, 0);
        assertEquals("merchant-a", productView.merchantAccount());
        assertEquals("Shop A", productView.merchantName());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void listProductsFiltersToCurrentMerchantWhenMerchantLoggedIn() {
        ProductService productService = new ProductService(jdbcTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminTokenInterceptor.REQUEST_ATTR_ROLES, List.of(Role.MERCHANT));
        request.setAttribute(AdminTokenInterceptor.REQUEST_ATTR_ACCOUNT, "merchant-b");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        doReturn(List.of()).when(jdbcTemplate)
            .query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ProductView>>any(), eq("merchant-b"), eq("DAILY"));

        productService.listProducts("DAILY");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), org.mockito.ArgumentMatchers.<RowMapper<ProductView>>any(), eq("merchant-b"), eq("DAILY"));

        assertTrue(sqlCaptor.getValue().contains("p.merchant_account = ?"));
    }
    @Test
    void getProductDetailReturnsMerchantInfo() {
        ProductService productService = new ProductService(jdbcTemplate);
        Long productId = 9L;

        doReturn(List.of(new ProductService.ProductSnapshot(
            productId,
            "Rose Bouquet",
            "BOUQUET",
            "DAILY",
            "Fresh roses",
            "/uploads/rose.jpg",
            new BigDecimal("168.00"),
            new BigDecimal("10.00"),
            new BigDecimal("12.00"),
            "merchant-a",
            "Shop A",
            "ON_SALE"
        ))).when(jdbcTemplate).query(
            contains("FROM product p"),
            org.mockito.ArgumentMatchers.<RowMapper<ProductService.ProductSnapshot>>any(),
            eq(productId)
        );
        doReturn(List.of()).when(jdbcTemplate).query(
            contains("FROM product_bom b"),
            org.mockito.ArgumentMatchers.<RowMapper>any(),
            eq(productId)
        );
        doReturn(1).when(jdbcTemplate).queryForObject(
            contains("FROM information_schema.TABLES"),
            eq(Integer.class),
            org.mockito.ArgumentMatchers.anyString()
        );
        doReturn(23).when(jdbcTemplate).queryForObject(
            contains("FROM order_item oi"),
            eq(Integer.class),
            eq(productId)
        );
        doReturn(7).when(jdbcTemplate).queryForObject(
            contains("MIN(stock_units)"),
            eq(Integer.class),
            eq(productId)
        );

        var detail = productService.getProductDetail(productId);

        assertEquals("merchant-a", detail.merchantAccount());
        assertEquals("Shop A", detail.merchantName());
        assertEquals(7, detail.stock());
        assertEquals(23, detail.sales());
    }

    @Test
    void deletesDependentRecordsBeforeDeletingProduct() {
        ProductService productService = new ProductService(jdbcTemplate);
        Long productId = 7L;

        when(jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM product WHERE id = ?",
            Integer.class,
            productId
        )).thenReturn(1);

        productService.deleteProduct(productId);

        InOrder inOrder = inOrder(jdbcTemplate);
        inOrder.verify(jdbcTemplate).queryForObject("SELECT COUNT(1) FROM product WHERE id = ?", Integer.class, productId);
        inOrder.verify(jdbcTemplate).update("DELETE FROM user_favorite WHERE product_id = ?", productId);
        inOrder.verify(jdbcTemplate).update("DELETE FROM cart_item WHERE product_id = ?", productId);
        inOrder.verify(jdbcTemplate).update("DELETE FROM product_bom WHERE product_id = ?", productId);
        inOrder.verify(jdbcTemplate).update("DELETE FROM product WHERE id = ?", productId);
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void throwsWhenDeletingMissingProduct() {
        ProductService productService = new ProductService(jdbcTemplate);
        Long productId = 99L;

        when(jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM product WHERE id = ?",
            Integer.class,
            productId
        )).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> productService.deleteProduct(productId));

        assertEquals("PRODUCT_NOT_FOUND", exception.getCode());
        verifyNoMoreInteractions(jdbcTemplate);
    }
}
