package com.techpulse.techradar.features.system.ports;

import com.techpulse.techradar.features.system.domain.CmsContent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Output port for the {@code cms_content} table.
 */
public interface CmsRepository {

    Flux<CmsContent> findAll();

    Mono<CmsContent> findById(String id);

    Mono<CmsContent> insert(CmsContent content);

    Mono<CmsContent> update(CmsContent content);

    Mono<Long> deleteById(String id);
}
