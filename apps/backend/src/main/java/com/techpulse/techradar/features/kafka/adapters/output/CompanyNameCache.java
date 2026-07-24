package com.techpulse.techradar.features.kafka.adapters.output;

import com.techpulse.techradar.features.kafka.ports.CompanyNameProvider;
import com.techpulse.techradar.shared.neo4j.Neo4jReadTemplate;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cache trong RAM danh sách tên Company đã biết trong Neo4j (tạo qua đường Job — company_name
 * lấy trực tiếp từ field crawler, xem docs/DATABASE.md §4.1) — dùng làm "từ điển" để
 * {@link com.techpulse.techradar.features.kafka.domain.EntityExtractionService} nhận diện
 * Company được nhắc tới trong Article, vì service đó không có NER thật.
 * <p>
 * Giới hạn có chủ đích: chỉ phát hiện được Company ĐÃ TỪNG biết qua Job posting — Company hoàn
 * toàn mới, chưa từng đăng tin tuyển dụng, sẽ không được nhận diện dù bài viết có nhắc tên. Đây
 * là đánh đổi chấp nhận được so với trạng thái trước khi có cache này (ORG luôn rỗng, 0% Company
 * nào được nhận diện từ Article).
 * <p>
 * Refresh định kỳ (không phải mỗi message) — tra cache là 1 lần đọc {@link List} trong RAM,
 * không có round-trip Neo4j nào trên luồng Kafka realtime. Chu kỳ refresh dài hơn
 * {@link TechAliasCache} (15 phút thay vì 5 phút) vì query quét toàn bộ Company node, nặng hơn
 * đọc 1 bảng Postgres nhỏ.
 */
@Slf4j
@Component
public class CompanyNameCache implements CompanyNameProvider {

    // Loại tên bất thường dài (rác crawl bị dán nhầm vào field name — xem
    // data-platform/gold/kg_health_audit.py's _COMPANY_NAME_MAX_LEN, cùng ngưỡng) — tên dài cỡ
    // này không thể là tên công ty thật, và match substring với nó vừa vô nghĩa vừa tốn CPU.
    private static final int MAX_NAME_LENGTH = 200;

    private final Driver driver;
    private volatile List<String> names = List.of();

    public CompanyNameCache(Driver driver) {
        this.driver = driver;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${app.company-name-cache.refresh-ms:900000}")
    public void refresh() {
        Neo4jReadTemplate.read(driver, session ->
                        session.run("MATCH (c:Company) WHERE c.name IS NOT NULL RETURN DISTINCT c.name AS name")
                                .list(r -> r.get("name").asString()))
                .subscribe(
                        fetched -> {
                            names = fetched.stream()
                                    .filter(n -> n != null && !n.isBlank() && n.length() <= MAX_NAME_LENGTH)
                                    .toList();
                            log.info("CompanyNameCache refreshed: {} company name(s)", names.size());
                        },
                        err -> log.warn("CompanyNameCache refresh failed, giữ cache cũ ({} entries): {}",
                                names.size(), err.getMessage()));
    }

    @Override
    public List<String> knownCompanyNames() {
        return names;
    }
}
