package com.techpulse.techradar.features.salary.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface SalaryRepository {

    /**
     * For each tech that appears in at least minJobs job postings,
     * returns the tech name, total job count, and all raw salary strings.
     */
    Flux<TechSalaryRaw> findTechSalaries(int minJobs, int techLimit);

    /**
     * Raw salary strings + co-tech frequency for one technology.
     */
    Mono<TechSalaryDetailRaw> findTechSalaryDetail(String techName);

    record TechSalaryRaw(String techName, int totalJobs, List<String> salaries) {}

    record TechSalaryDetailRaw(
            String techName,
            int totalJobs,
            List<String> salaries,
            List<Map.Entry<String, Integer>> coTechs  // co-required techs ordered by frequency
    ) {}
}
