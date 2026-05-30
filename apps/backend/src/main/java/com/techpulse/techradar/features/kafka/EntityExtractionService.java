package com.techpulse.techradar.features.kafka;

import com.techpulse.techradar.features.kafka.model.Entities;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EntityExtractionService {

    private static final List<String> TECH_KEYWORDS = List.of(
            "AI", "ML", "NLP", "RPA", "BigQuery", "Kafka", "Spark", "Flink",
            "TensorFlow", "PyTorch", "React", "Vue", "Angular", "Spring Boot",
            "Spring", "Django", "Flask", "FastAPI", "Node.js", "Express",
            "Docker", "Kubernetes", "Neo4j", "Qdrant", "PostgreSQL", "MySQL",
            "Redis", "MongoDB", "GraphQL", "TypeScript", "JavaScript", "Java",
            "Python", "Golang", "Go", "Rust", "SQL", "NoSQL", "AWS", "GCP",
            "Azure", "CI/CD", "DevOps", "Hadoop", "Snowflake", "Elasticsearch"
    );

    private final List<Pattern> techPatterns;
    private final Pattern datePattern;
    private final Pattern salaryPattern;

    public EntityExtractionService() {
        techPatterns = new ArrayList<>();
        for (String keyword : TECH_KEYWORDS) {
            techPatterns.add(Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b", Pattern.CASE_INSENSITIVE));
        }

        datePattern = Pattern.compile("\\b(?:\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}|\\d{4})\\b");
        salaryPattern = Pattern.compile("\\b(?:\\$?\\d+[kK]?\\s*(?:VNĐ|VND|USD|US|\\$|đ|dong)?|\\d{1,3}(?:\\.\\d{3})+\\s*(?:đ|VND)?)\\b", Pattern.CASE_INSENSITIVE);
    }

    public Entities extractEntities(String text, List<String> jobSkills) {
        if (text == null) {
            text = "";
        }
        Set<String> tech = extractTech(text);
        if (jobSkills != null) {
            for (String skill : jobSkills) {
                if (skill != null && !skill.isBlank()) {
                    tech.add(skill.trim());
                }
            }
        }

        List<String> dates = extractMatches(text, datePattern);
        List<String> salaries = extractMatches(text, salaryPattern);

        return new Entities(new ArrayList<>(tech), List.of(), List.of(), dates, List.of(), salaries);
    }

    private Set<String> extractTech(String text) {
        Set<String> result = new HashSet<>();
        for (Pattern pattern : techPatterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String match = matcher.group();
                if (match != null && !match.isBlank()) {
                    result.add(normalizeTechName(match));
                }
            }
        }
        return result;
    }

    private List<String> extractMatches(String text, Pattern pattern) {
        List<String> values = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();
            if (match != null && !match.isBlank()) {
                values.add(match.trim());
            }
        }
        return values;
    }

    private String normalizeTechName(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        if (cleaned.isBlank()) {
            return cleaned;
        }
        if (cleaned.equalsIgnoreCase("AI") || cleaned.equalsIgnoreCase("ML") || cleaned.equalsIgnoreCase("NLP") || cleaned.equalsIgnoreCase("RPA") || cleaned.equalsIgnoreCase("CI/CD") || cleaned.equalsIgnoreCase("SQL") || cleaned.equalsIgnoreCase("NoSQL") || cleaned.equalsIgnoreCase("Go")) {
            return cleaned.toUpperCase();
        }
        if (cleaned.equalsIgnoreCase("Node.js")) {
            return "Node.js";
        }
        if (cleaned.equalsIgnoreCase("Spring Boot")) {
            return "Spring Boot";
        }
        return capitalize(cleaned);
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() == 1) {
            return value.toUpperCase();
        }
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }
}
