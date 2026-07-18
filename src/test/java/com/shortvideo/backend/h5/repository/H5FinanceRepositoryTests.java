package com.shortvideo.backend.h5.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class H5FinanceRepositoryTests {

    @Test
    void paidAmountDefaultsToZeroWhenDatabaseReturnsNull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        H5FinanceRepository repository = new H5FinanceRepository(jdbc);
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(42L))).thenReturn(null);

        assertThat(repository.paidAmount(42L)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void debitBalanceReturnsDatabaseUpdateCount() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        H5FinanceRepository repository = new H5FinanceRepository(jdbc);
        BigDecimal amount = new BigDecimal("9.90");
        when(jdbc.update(anyString(), eq(amount), eq(7L), eq(amount))).thenReturn(1);

        assertThat(repository.debitBalanceIfEnough(7L, amount)).isEqualTo(1);
        verify(jdbc).update(anyString(), eq(amount), eq(7L), eq(amount));
    }
}
