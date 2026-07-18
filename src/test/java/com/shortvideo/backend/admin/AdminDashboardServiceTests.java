package com.shortvideo.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.shortvideo.backend.admin.dto.AdminDashboardResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AdminDashboardServiceTests {

    @Autowired
    private AdminDashboardService dashboardService;

    @Test
    void summaryReturnsDashboardSectionsFromDatabase() {
        AdminDashboardResponse summary = dashboardService.summary();

        assertThat(summary.kpis()).hasSize(4);
        assertThat(summary.trend()).hasSize(7);
        assertThat(summary.topDramas()).isNotEmpty();
        assertThat(summary.tasks()).isNotEmpty();
        assertThat(summary.kpis())
                .extracting(AdminDashboardResponse.Kpi::label)
                .containsExactly("今日收入", "播放量", "付费订单", "投放 ROI");
    }
}
