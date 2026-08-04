package com.techpulse.techradar.features.roadmap.adapters.input;

import com.techpulse.techradar.features.roadmap.domain.LevelMoveResult;
import com.techpulse.techradar.features.roadmap.domain.SimulationResult;
import com.techpulse.techradar.features.salary.adapters.input.SalaryDtos;

import java.util.Map;

public class SimulationDtos {

    public record SimulationResponse(
            String technology,
            long currentJobMatches,
            long simulatedJobMatches,
            SalaryDtos.SalaryInsightResponse salary,
            Map<String, Object> forecast
    ) {
        public static SimulationResponse from(SimulationResult r) {
            return new SimulationResponse(
                    r.technology(),
                    r.currentJobMatches(),
                    r.simulatedJobMatches(),
                    r.salary() == null ? null : SalaryDtos.SalaryInsightResponse.from(r.salary()),
                    r.forecast());
        }
    }

    public record LevelMoveResponse(
            String currentLevel,
            String targetLevel,
            long currentJobMatches,
            long simulatedJobMatches,
            SalaryDtos.SalaryInsightResponse salary
    ) {
        public static LevelMoveResponse from(LevelMoveResult r) {
            return new LevelMoveResponse(
                    r.currentLevel(),
                    r.targetLevel(),
                    r.currentJobMatches(),
                    r.simulatedJobMatches(),
                    r.salary() == null ? null : SalaryDtos.SalaryInsightResponse.from(r.salary()));
        }
    }
}
