package com.techpulse.techradar.features.kafka.domain;

import com.techpulse.techradar.features.kafka.event.Entities;
import com.techpulse.techradar.features.kafka.ports.TechAliasResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EntityExtractionService {

    // Giữ song song với TECH_KEYWORDS trong data-platform/common/tech_keywords.py —
    // mở rộng một bên thì nên mở rộng bên kia để Technology node không bị phân
    // mảnh tên giữa hai đường ghi (Java Kafka pipeline vs Python gap-filler sync).
    private static final List<String> TECH_KEYWORDS = List.of(
            // Ngôn ngữ
            "Python", "Java", "JavaScript", "TypeScript", "Golang", "Go", "Rust",
            "C++", "C#", "PHP", "Ruby", "Swift", "Kotlin", "Scala", "Dart", "Perl",
            "Elixir", "Haskell",
            // Frontend
            "React", "Vue", "Angular", "Svelte", "Next.js", "Nuxt", "jQuery",
            "Tailwind", "Bootstrap", "Webpack", "Vite", "HTML", "CSS", "Sass",
            // Backend / framework
            "Spring Boot", "Spring", "Django", "Flask", "FastAPI", "Node.js",
            "Express", "Laravel", "Rails", ".NET", "ASP.NET", "NestJS",
            // Mobile
            "Flutter", "React Native", "Android", "iOS", "Xamarin",
            // Data / AI
            "AI", "ML", "NLP", "RPA", "Machine Learning", "Deep Learning",
            "Computer Vision", "Big Data", "Data Science", "LLM", "GPT", "ChatGPT",
            "Gemini", "Claude", "TensorFlow", "PyTorch", "Keras", "Hadoop", "Spark",
            "Flink", "Airflow", "dbt", "Databricks", "Snowflake", "Power BI",
            "Tableau", "BigQuery",
            // Database
            "SQL", "NoSQL", "PostgreSQL", "MySQL", "MongoDB", "Redis", "Neo4j",
            "Qdrant", "Elasticsearch", "Cassandra", "SQLite", "Oracle", "MariaDB",
            "GraphQL",
            // Cloud / DevOps
            "AWS", "GCP", "Azure", "Docker", "Kubernetes", "CI/CD", "DevOps",
            "Terraform", "Ansible", "Jenkins", "GitLab", "GitHub Actions",
            "Prometheus", "Grafana",
            // Messaging / infra
            "Kafka", "RabbitMQ", "gRPC", "Microservices", "WebSocket",
            // Bảo mật / công nghệ mới
            "Blockchain", "Web3", "Solidity", "IoT", "AR", "VR", "Cybersecurity",
            "5G", "Semiconductor"
    );

    // Cụm từ tiếng Việt phổ biến trong tin tức công nghệ VN -> tên canonical.
    private static final Map<String, String> VN_TECH_ALIASES = Map.ofEntries(
            Map.entry("trí tuệ nhân tạo", "AI"),
            Map.entry("học máy", "Machine Learning"),
            Map.entry("học sâu", "Deep Learning"),
            Map.entry("dữ liệu lớn", "Big Data"),
            Map.entry("an ninh mạng", "Cybersecurity"),
            Map.entry("bảo mật mạng", "Cybersecurity"),
            Map.entry("chuyển đổi số", "Digital Transformation"),
            Map.entry("bán dẫn", "Semiconductor"),
            Map.entry("chip bán dẫn", "Semiconductor"),
            Map.entry("vi mạch", "Semiconductor"),
            Map.entry("điện toán đám mây", "Cloud"),
            Map.entry("chuỗi khối", "Blockchain"),
            Map.entry("thực tế ảo", "VR"),
            Map.entry("thực tế tăng cường", "AR"),
            Map.entry("vạn vật kết nối", "IoT"),
            Map.entry("internet vạn vật", "IoT")
    );

    private final Map<Pattern, String> techPatterns;
    private final Pattern datePattern;
    private final Pattern salaryPattern;
    private final TechAliasResolver techAliasResolver;

    public EntityExtractionService(TechAliasResolver techAliasResolver) {
        this.techAliasResolver = techAliasResolver;
        Map<String, String> canonicalByLower = new LinkedHashMap<>();
        for (String keyword : TECH_KEYWORDS) {
            canonicalByLower.put(keyword.toLowerCase(), keyword);
        }
        VN_TECH_ALIASES.forEach((alias, canonical) -> canonicalByLower.put(alias.toLowerCase(), canonical));

        techPatterns = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : canonicalByLower.entrySet()) {
            // \b không match đúng ở term kết thúc bằng ký tự không phải chữ/số
            // (C++, C#, .NET, CI/CD) vì \b đòi hỏi ranh giới \w<->\W; dùng
            // lookaround (?<!\w)/(?!\w) để match đúng trong mọi trường hợp.
            Pattern pattern = Pattern.compile(
                    "(?<!\\w)(" + Pattern.quote(entry.getKey()) + ")(?!\\w)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            );
            techPatterns.put(pattern, entry.getValue());
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
                    // Skill tag thô từ job posting (chưa qua regex TECH_KEYWORDS) — vẫn
                    // phải qua tra cứu alias để "Golang"/"ML" không tách khỏi "Go"/
                    // "Machine Learning" đã được nhận diện ở extractTech().
                    tech.add(techAliasResolver.resolve(skill.trim()));
                }
            }
        }

        List<String> dates = extractMatches(text, datePattern);
        List<String> salaries = extractMatches(text, salaryPattern);

        return new Entities(new ArrayList<>(tech), List.of(), List.of(), dates, List.of(), salaries);
    }

    private Set<String> extractTech(String text) {
        Set<String> result = new TreeSet<>();
        for (Map.Entry<Pattern, String> entry : techPatterns.entrySet()) {
            Matcher matcher = entry.getKey().matcher(text);
            if (matcher.find()) {
                // entry.getValue() đã canonical theo TECH_KEYWORDS/VN_TECH_ALIASES (vd
                // "golang" -> "Golang"), nhưng bảng đó không biết "Golang" chính là "Go"
                // — tra thêm techAliasResolver (dp_tech_alias_map, dùng chung với Python)
                // để gộp đúng nghĩa trước khi thêm vào kết quả.
                result.add(techAliasResolver.resolve(entry.getValue()));
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
}
