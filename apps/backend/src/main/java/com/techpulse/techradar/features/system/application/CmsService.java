package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.system.domain.CmsContent;
import com.techpulse.techradar.features.system.ports.CmsRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * Application service for admin CMS content.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CmsService {

    private final CmsRepository cmsRepository;

    public Flux<CmsContent> list() {
        return cmsRepository.findAll()
                .doOnComplete(() -> log.info("Listed CMS content"))
                .doOnError(e -> log.error("Failed to list CMS content", e));
    }

    public Mono<CmsContent> create(String title, String type, LocalDate date, String status) {
        return cmsRepository.insert(CmsContent.builder()
                .title(title)
                .type(type)
                .contentDate(date)
                .status(StringUtils.hasText(status) ? status : "Pending")
                .build())
                .doOnSubscribe(s -> log.info("Creating CMS content '{}' (type={})", title, type))
                .doOnSuccess(c -> log.info("Created CMS content id={} title='{}'", c.getId(), c.getTitle()))
                .doOnError(e -> log.error("Failed to create CMS content '{}'", title, e));
    }

    public Mono<CmsContent> update(String id, String title, String type, LocalDate date, String status) {
        return cmsRepository.findById(id)
                .switchIfEmpty(Mono.<CmsContent>error(new NotFoundException("CMS content not found"))
                        .doOnSubscribe(s -> log.warn("Update requested for missing CMS content id={}", id)))
                .flatMap(existing -> {
                    if (StringUtils.hasText(title)) {
                        existing.setTitle(title);
                    }
                    if (type != null) {
                        existing.setType(type);
                    }
                    if (date != null) {
                        existing.setContentDate(date);
                    }
                    if (StringUtils.hasText(status)) {
                        existing.setStatus(status);
                    }
                    return cmsRepository.update(existing);
                })
                .doOnSubscribe(s -> log.info("Updating CMS content id={}", id))
                .doOnSuccess(c -> log.info("Updated CMS content id={}", id))
                .doOnError(e -> {
                    if (!(e instanceof NotFoundException)) {
                        log.error("Failed to update CMS content id={}", id, e);
                    }
                });
    }

    public Mono<Void> delete(String id) {
        return cmsRepository.deleteById(id)
                .flatMap(rows -> rows == 0
                        ? Mono.<Void>error(new NotFoundException("CMS content not found"))
                                .doOnSubscribe(s -> log.warn("Delete requested for missing CMS content id={}", id))
                        : Mono.<Void>empty().doOnSubscribe(s -> log.info("Deleted CMS content id={}", id)))
                .doOnError(e -> {
                    if (!(e instanceof NotFoundException)) {
                        log.error("Failed to delete CMS content id={}", id, e);
                    }
                });
    }
}
