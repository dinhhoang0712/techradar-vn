package com.techpulse.techradar.features.kgreview.application;

import com.techpulse.techradar.features.kgreview.domain.CompanyDuplicateGroup;
import com.techpulse.techradar.features.kgreview.ports.CompanyDuplicatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class ListCompanyNearDuplicatesUseCase {

    private final CompanyDuplicatePort companyDuplicatePort;

    public Flux<CompanyDuplicateGroup> execute() {
        return companyDuplicatePort.detectNearDuplicates();
    }
}
