# Backend Development Guide — TechRadar VN

> Tài liệu chi tiết về kiến trúc, phát triển và best practices cho Spring Boot backend.

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Kiến trúc Hexagonal](#2-kiến-trúc-hexagonal)
3. [Cấu trúc dự án](#3-cấu-trúc-dự án)
4. [Feature Modules](#4-feature-modules)
5. [Database Layer](#5-database-layer)
6. [Security & Authentication](#6-security--authentication)
7. [API Design](#7-api-design)
8. [External Service Integration](#8-external-service-integration)
9. [Testing](#9-testing)
10. [Deployment](#10-deployment)

---

## 1. Tổng quan

Backend TechRadar VN được xây dựng với:

- **Java 21** với các tính năng modern (records, pattern matching, virtual threads)
- **Spring Boot 3.4** với WebFlux cho reactive programming
- **Hexagonal Architecture** (Ports & Adapters)
- **Feature-Based Modularization**
- **R2DBC** cho non-blocking database access
- **Neo4j** cho Knowledge Graph

### Mục tiêu thiết kế

- **Clean Architecture**: Tách biệt domain logic khỏi infrastructure
- **Reactive**: Non-blocking I/O cho high throughput
- **Testable**: Dependency injection, testcontainers cho integration tests
- **Maintainable**: Clear separation of concerns, consistent naming

---

## 2. Kiến trúc Hexagonal

### 2.1 Ports & Adapters Pattern

```
┌─────────────────────────────────────────────────────────────┐
│                     APPLICATION LAYER                         │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Domain (Business Logic)                   │  │
│  │  - Entities                                            │  │
│  │  - Value Objects                                       │  │
│  │  - Domain Services                                     │  │
│  └──────────────────────────────────────────────────────┘  │
│                            │                                 │
│                    ┌───────┴───────┐                         │
│                    │   Ports      │                         │
│              ┌─────┴──────┬──────┴─────┐                    │
│              │  Input     │   Output    │                    │
│              │  Ports     │   Ports     │                    │
│              └─────┬──────┴──────┬─────┘                    │
└────────────────────┼───────────────┼─────────────────────────┘
                     │               │
        ┌────────────▼─────┐ ┌─────▼────────────┐
        │  Input Adapters  │ │  Output Adapters │
        │  - REST Controllers│ │ - Repositories  │
        │  - Event Listeners│ │ - External APIs │
        └──────────────────┘ └──────────────────┘
```

### 2.2 Dependency Rule

- **Domain** không phụ thuộc bất kỳ layer nào
- **Application** chỉ phụ thuộc vào Domain
- **Adapters** phụ thuộc vào Application và Domain
- **Infrastructure** implements Adapters

### 2.3 Implementation trong Spring Boot

```java
// Input Port (Use Case Interface)
public interface LoginUseCase {
    Mono<LoginResponse> login(LoginRequest request);
}

// Output Port (Repository Interface)
public interface UserRepository {
    Mono<User> findById(UUID id);
    Mono<User> save(User user);
}

// Application Service
@Service
public class LoginService implements LoginUseCase {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    
    public LoginService(UserRepository userRepository, 
                        PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }
    
    @Override
    public Mono<LoginResponse> login(LoginRequest request) {
        return userRepository.findByEmail(request.getEmail())
            .switchIfEmpty(Mono.error(new AuthenticationException("User not found")))
            .flatMap(user -> {
                if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                    return Mono.error(new AuthenticationException("Invalid password"));
                }
                String accessToken = jwtTokenProvider.generateAccessToken(user);
                String refreshToken = jwtTokenProvider.generateRefreshToken(user);
                return Mono.just(LoginResponse.from(user, accessToken, refreshToken));
            });
    }
}

// Input Adapter (REST Controller)
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final LoginUseCase loginUseCase;
    
    public AuthController(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }
    
    @PostMapping("/login")
    public Mono<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return loginUseCase.login(request);
    }
}

// Output Adapter (Repository Implementation)
@Repository
public class UserRepositoryImpl implements UserRepository {
    private final R2dbcEntityTemplate template;
    
    public UserRepositoryImpl(R2dbcEntityTemplate template) {
        this.template = template;
    }
    
    @Override
    public Mono<User> findById(UUID id) {
        return template.select(User.class)
            .matching(Query.query(Criteria.where("id").is(id)))
            .one();
    }
    
    @Override
    public Mono<User> save(User user) {
        return template.insert(user);
    }
}
```

---

## 3. Cấu trúc dự án

```
apps/backend/src/main/java/com/techpulse/
├── TechRadarApplication.java          # Main entry point
├── features/                           # Feature modules
│   ├── auth/                          # Authentication feature
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── User.java
│   │   │   │   ├── Role.java
│   │   │   │   └── UserProfile.java
│   │   │   ├── repository/
│   │   │   │   └── UserRepository.java
│   │   │   └── service/
│   │   │       ├── LoginService.java
│   │   │       ├── RegisterService.java
│   │   │       └── PasswordResetService.java
│   │   ├── application/
│   │   │   ├── port/in/
│   │   │   │   ├── LoginUseCase.java
│   │   │   │   └── RegisterUseCase.java
│   │   │   └── port/out/
│   │   │       └── UserRepository.java
│   │   └── adapter/
│   │       ├── in/web/
│   │       │   ├── AuthController.java
│   │       │   └── dto/
│   │       │       ├── LoginRequest.java
│   │       │       ├── LoginResponse.java
│   │       │       └── RegisterRequest.java
│   │       └── out/persistence/
│   │           ├── UserRepositoryImpl.java
│   │           └── mapper/
│   │               └── UserMapper.java
│   │   └── AuthModuleConfig.java
│   │
│   ├── radar/                         # Tech radar feature
│   ├── compare/                       # Technology comparison
│   ├── graph/                         # Knowledge graph explorer
│   ├── chat/                          # RAG chat
│   ├── clustering/                    # ML clustering
│   ├── user/                          # User management
│   ├── system/                        # System settings
│   ├── health/                        # Health checks
│   └── kafka/                         # Kafka event handling
│
├── shared/                            # Shared infrastructure
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── R2dbcConfig.java
│   │   ├── Neo4jConfig.java
│   │   ├── RedisConfig.java
│   │   ├── KafkaConfig.java
│   │   └── WebFluxConfig.java
│   ├── security/
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── JwtTokenProvider.java
│   │   └── PasswordEncoder.java
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── AuthenticationException.java
│   │   └── ResourceNotFoundException.java
│   ├── common/
│   │   ├── ApiResponse.java
│   │   ├── PageResponse.java
│   │   └── Constants.java
│   └── util/
│       └── DateUtil.java
│
└── infrastructure/
    ├── flyway/
    │   └── migrations/
    │       ├── V1__create_users.sql
    │       ├── V2__create_chat_tables.sql
    │       ├── V3__create_analytics.sql
    │       ├── V4__create_cms.sql
    │       ├── V5__create_system.sql
    │       ├── V900__seed_admin_user.sql
    │       └── V901__seed_settings.sql
    └── kafka/
        ├── producer/
        │   └── KafkaEventPublisher.java
        └── consumer/
            └── KafkaEventListener.java
```

---

## 4. Feature Modules

### 4.1 Auth Feature

**Responsibilities:**
- User registration and login
- JWT token generation and validation
- Password reset flow
- Refresh token rotation

**Key Components:**
- `LoginService`: Authenticate user, generate tokens
- `RegisterService`: Create new user, hash password
- `PasswordResetService`: Handle forgot/reset password
- `JwtTokenProvider`: Generate and validate JWT tokens
- `JwtAuthenticationFilter`: Filter for JWT authentication

**Database Tables:**
- `users`: User credentials and status
- `user_profiles`: User profile information
- `user_avatar`: Avatar images (BYTEA)

### 4.2 Radar Feature

**Responsibilities:**
- Tech trend analytics
- Top technologies by growth rate
- Search and filter technologies
- Export radar data (PNG, CSV)

**Key Components:**
- `RadarAnalyticsService`: Compute trend analytics
- `RadarSearchService`: Search technologies by keywords
- `RadarExportService`: Export radar visualizations

**Data Source:**
- PostgreSQL `tech_analytics` table (populated by Gold ETL)

### 4.3 Graph Feature

**Responsibilities:**
- Knowledge graph exploration
- Graph traversal queries
- Node and edge filtering
- Shortest path analysis

**Key Components:**
- `GraphExplorerService`: Execute Cypher queries
- `GraphFilterService`: Filter nodes/edges by criteria
- `GraphPathService`: Find shortest paths

**Data Source:**
- Neo4j Knowledge Graph

### 4.4 Chat Feature

**Responsibilities:**
- RAG chat session management
- Message history
- Proxy to ai-rag-core service
- SSE streaming for real-time responses

**Key Components:**
- `ChatSessionService`: Create and manage sessions
- `ChatMessageService`: Store and retrieve messages
- `RagProxyService`: Proxy requests to ai-rag-core

**Database Tables:**
- `chat_sessions`: Chat sessions
- `chat_messages`: Message history

### 4.5 Clustering Feature

**Responsibilities:**
- Technology clustering results
- Cluster information retrieval
- Batch prediction for technologies

**Key Components:**
- `ClusteringService`: Retrieve cluster information
- `ClusteringPredictionService`: Predict cluster for technologies

**Data Source:**
- ml-clustering service (FastAPI)

### 4.6 User Feature

**Responsibilities:**
- User profile management
- Avatar upload
- Preference management
- Notification settings

**Key Components:**
- `UserProfileService`: CRUD user profiles
- `AvatarService`: Handle avatar upload/retrieval
- `PreferenceService`: Manage user preferences

### 4.7 System Feature

**Responsibilities:**
- Application settings
- Feature flags
- Activity logging
- Notification management

**Key Components:**
- `SettingsService`: CRUD application settings
- `ActivityLogService`: Log user activities
- `NotificationService`: Create and retrieve notifications

**Database Tables:**
- `settings`: Application settings
- `activity_log`: User activity logs
- `notifications`: User notifications

### 4.8 Health Feature

**Responsibilities:**
- Health check endpoints
- Dependency health checks
- Actuator integration

**Key Components:**
- `HealthCheckService`: Check all dependencies
- `StatusService`: Return feature flags

### 4.9 Kafka Feature

**Responsibilities:**
- Event publishing
- Event consumption
- Trend alert notifications

**Key Components:**
- `KafkaEventPublisher`: Publish events to Kafka
- `KafkaEventListener`: Consume events from Kafka
- `TrendAlertDispatcher`: Dispatch trend alerts to users

---

## 5. Database Layer

### 5.1 PostgreSQL (R2DBC)

**Configuration:**
```yaml
# application.yml
spring:
  r2dbc:
    url: r2dbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
    pool:
      initial-size: 5
      max-size: 20
      max-idle-time: 30m
```

**Repository Pattern:**
```java
@Repository
public class UserRepositoryImpl implements UserRepository {
    private final R2dbcEntityTemplate template;
    
    @Override
    public Mono<User> findById(UUID id) {
        return template.select(User.class)
            .matching(Query.query(Criteria.where("id").is(id)))
            .one();
    }
    
    @Override
    public Flux<User> findAll() {
        return template.select(User.class).all();
    }
    
    @Override
    public Mono<User> save(User user) {
        return template.insert(user);
    }
    
    @Override
    public Mono<User> update(User user) {
        return template.update(user);
    }
    
    @Override
    public Mono<Void> deleteById(UUID id) {
        return template.delete(User.class)
            .matching(Query.query(Criteria.where("id").is(id)))
            .all()
            .then();
    }
}
```

**Flyway Migrations:**
- Located in `src/main/resources/db/migration/`
- Naming convention: `V{version}__{description}.sql`
- Executed at startup via Flyway auto-configuration

### 5.2 Neo4j

**Configuration:**
```java
@Configuration
public class Neo4jConfig {
    
    @Bean
    public Driver neo4jDriver(
            @Value("${neo4j.uri}") String uri,
            @Value("${neo4j.username}") String username,
            @Value("${neo4j.password}") String password) {
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }
    
    @Bean
    public SessionFactory neo4jSessionFactory(Driver driver) {
        return new SessionFactory(driver, "com.techpulse.features.graph.domain.model");
    }
}
```

**Cypher Query Execution:**
```java
@Repository
public class GraphRepositoryImpl implements GraphRepository {
    private final Driver driver;
    
    @Override
    public Flux<GraphNode> exploreGraph(List<String> keywords, int depth) {
        return Mono.fromCallable(() -> driver.session())
            .flatMapMany(session -> Flux.using(
                session,
                s -> {
                    String cypher = """
                        MATCH (n)
                        WHERE any(keyword IN $keywords WHERE toLower(n.name) CONTAINS toLower(keyword))
                        CALL apoc.path.subgraphAll(n, {
                            maxLevel: $depth,
                            relationshipFilter: "MENTIONS|REQUIRES|RELATED_TO"
                        })
                        YIELD nodes, relationships
                        RETURN nodes, relationships
                        """;
                    return Flux.from(s.run(cypher, 
                        Map.of("keywords", keywords, "depth", depth)))
                        .flatMap(record -> Mono.just(record));
                },
                Session::close
            ));
    }
}
```

### 5.3 Redis

**Configuration:**
```java
@Configuration
public class RedisConfig {
    
    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${spring.redis.host}") String host,
            @Value("${spring.redis.port}") int port) {
        return new LettuceConnectionFactory(host, port);
    }
    
    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            LettuceConnectionFactory connectionFactory) {
        return new ReactiveStringRedisTemplate(connectionFactory);
    }
}
```

**Use Cases:**
- Token blacklist (refresh tokens)
- Caching frequently accessed data
- Rate limiting
- Session storage (optional)

---

## 6. Security & Authentication

### 6.1 JWT Authentication

**Token Provider:**
```java
@Component
public class JwtTokenProvider {
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.access-token-expiration:900000}") // 15 minutes
    private long accessTokenExpiration;
    
    @Value("${jwt.refresh-token-expiration:604800000}") // 7 days
    private long refreshTokenExpiration;
    
    public String generateAccessToken(User user) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusMillis(accessTokenExpiration)))
            .signWith(getSigningKey())
            .compact();
    }
    
    public String generateRefreshToken(User user) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusMillis(refreshTokenExpiration)))
            .signWith(getSigningKey())
            .compact();
    }
    
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
    
    public Claims getClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
    
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, SignatureAlgorithm.HS256.getJcaName());
    }
}
```

**Authentication Filter:**
```java
@Component
public class JwtAuthenticationFilter implements WebFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final ReactiveStringRedisTemplate redisTemplate;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // Skip public paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }
        
        String token = extractToken(exchange.getRequest());
        
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return Mono.error(new AuthenticationException("Invalid or missing token"));
        }
        
        // Check if token is blacklisted (refresh token)
        Claims claims = jwtTokenProvider.getClaims(token);
        String userId = claims.getSubject();
        
        return redisTemplate.opsForValue().get("blacklist:refresh:" + token)
            .flatMap(blacklisted -> {
                if (blacklisted != null) {
                    return Mono.error(new AuthenticationException("Token has been revoked"));
                }
                return chain.filter(exchange);
            })
            .switchIfEmpty(chain.filter(exchange));
    }
    
    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    private boolean isPublicPath(String path) {
        return path.equals("/api/v1/auth/login") ||
               path.equals("/api/v1/auth/register") ||
               path.equals("/api/v1/auth/refresh") ||
               path.equals("/health") ||
               path.equals("/status");
    }
}
```

### 6.2 Security Configuration

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/api/v1/auth/login", "/api/v1/auth/register", 
                              "/api/v1/auth/refresh", "/api/v1/auth/forgot-password",
                              "/api/v1/auth/reset-password", "/health", "/status",
                              "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**")
                .permitAll()
                .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyExchange().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 6.3 Role-Based Access Control

```java
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    
    @GetMapping("/users")
    public Flux<User> getAllUsers() {
        return userService.getAllUsers();
    }
    
    @PostMapping("/users")
    public Mono<User> createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }
}
```

---

## 7. API Design

### 7.1 Response Format

**Standard Response:**
```java
public record ApiResponse<T>(
    boolean success,
    T data,
    String message,
    String errorCode,
    long timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null, System.currentTimeMillis());
    }
    
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, null, System.currentTimeMillis());
    }
    
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return new ApiResponse<>(false, null, message, errorCode, System.currentTimeMillis());
    }
}
```

**Bare Response (for auth endpoints):**
```java
public record LoginResponse(
    String accessToken,
    String refreshToken,
    UUID userId,
    String email,
    String role,
    long expiresIn
) {
    public static LoginResponse from(User user, String accessToken, String refreshToken) {
        return new LoginResponse(
            accessToken,
            refreshToken,
            user.getId(),
            user.getEmail(),
            user.getRole().name(),
            900000 // 15 minutes
        );
    }
}
```

### 7.2 Controller Pattern

```java
@RestController
@RequestMapping("/api/v1/user")
public class UserController {
    
    private final UserProfileService userProfileService;
    
    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }
    
    @GetMapping("/profile")
    public Mono<ApiResponse<UserProfile>> getProfile(
            @AuthenticationPrincipal Mono<Authentication> auth) {
        return auth
            .map(Authentication::getPrincipal)
            .cast(User.class)
            .flatMap(userProfileService::getProfile)
            .map(ApiResponse::success);
    }
    
    @PutMapping("/profile")
    public Mono<ApiResponse<UserProfile>> updateProfile(
            @AuthenticationPrincipal Mono<Authentication> auth,
            @Valid @RequestBody UpdateProfileRequest request) {
        return auth
            .map(Authentication::getPrincipal)
            .cast(User.class)
            .flatMap(user -> userProfileService.updateProfile(user.getId(), request))
            .map(ApiResponse::success);
    }
}
```

### 7.3 Validation

```java
public record LoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,
    
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    String password
) {}

public record RegisterRequest(
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    String fullName,
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).{8,}$",
             message = "Password must contain at least one digit, one lowercase, one uppercase, and one special character")
    String password,
    
    SubscriptionTier subscriptionTier
) {}
```

### 7.4 Error Handling

```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(AuthenticationException.class)
    public Mono<ServerResponse> handleAuthenticationException(
            AuthenticationException ex, ServerRequest request) {
        return ServerResponse.status(HttpStatus.UNAUTHORIZED)
            .bodyValue(ApiResponse.error(ex.getMessage(), "AUTH_ERROR"));
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public Mono<ServerResponse> handleNotFoundException(
            ResourceNotFoundException ex, ServerRequest request) {
        return ServerResponse.status(HttpStatus.NOT_FOUND)
            .bodyValue(ApiResponse.error(ex.getMessage(), "NOT_FOUND"));
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Mono<ServerResponse> handleValidationException(
            MethodArgumentNotValidException ex, ServerRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return ServerResponse.status(HttpStatus.BAD_REQUEST)
            .bodyValue(ApiResponse.error(message, "VALIDATION_ERROR"));
    }
    
    @ExceptionHandler(Exception.class)
    public Mono<ServerResponse> handleGenericException(
            Exception ex, ServerRequest request) {
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .bodyValue(ApiResponse.error("Internal server error", "INTERNAL_ERROR"));
    }
}
```

---

## 8. External Service Integration

### 8.1 ai-rag-core Integration

**WebClient Configuration:**
```java
@Configuration
public class AiRagClientConfig {
    
    @Value("${python.rag.base-url}")
    private String ragBaseUrl;
    
    @Value("${python.internal.token}")
    private String internalToken;
    
    @Bean
    public WebClient ragWebClient(WebClient.Builder builder) {
        return builder
            .baseUrl(ragBaseUrl)
            .defaultHeader("X-Internal-Auth", internalToken)
            .build();
    }
}
```

**Service Integration:**
```java
@Service
public class RagProxyService {
    
    private final WebClient ragWebClient;
    
    public RagProxyService(WebClient ragWebClient) {
        this.ragWebClient = ragWebClient;
    }
    
    public Mono<RagResponse> chat(String query, UUID userId, UUID sessionId) {
        return ragWebClient.post()
            .uri("/chat")
            .bodyValue(Map.of(
                "query", query,
                "user_id", userId != null ? userId.toString() : null,
                "session_id", sessionId.toString()
            ))
            .retrieve()
            .bodyToMono(RagResponse.class)
            .timeout(Duration.ofSeconds(120))
            .onErrorMap(WebClientResponseException.class, ex -> {
                if (ex.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                    return new ServiceUnavailableException("AI service unavailable");
                }
                return ex;
            });
    }
    
    public Flux<String> chatStream(String query, UUID userId, UUID sessionId) {
        return ragWebClient.post()
            .uri("/chat/stream")
            .bodyValue(Map.of(
                "query", query,
                "user_id", userId != null ? userId.toString() : null,
                "session_id", sessionId.toString()
            ))
            .retrieve()
            .bodyToFlux(String.class)
            .timeout(Duration.ofSeconds(120));
    }
}
```

### 8.2 ml-clustering Integration

```java
@Service
public class ClusteringProxyService {
    
    private final WebClient clusteringWebClient;
    
    public ClusteringProxyService(WebClient clusteringWebClient) {
        this.clusteringWebClient = clusteringWebClient;
    }
    
    public Mono<ClustersResponse> getClusters(boolean isCoherent) {
        return clusteringWebClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/clustering/clusters")
                .queryParam("is_coherent", isCoherent)
                .build())
            .retrieve()
            .bodyToMono(ClustersResponse.class)
            .timeout(Duration.ofSeconds(60));
    }
    
    public Mono<ClusterPredictionResponse> predictBatch(List<String> techNames) {
        return clusteringWebClient.post()
            .uri("/clustering/predict/batch")
            .bodyValue(Map.of("tech_names", techNames))
            .retrieve()
            .bodyToMono(ClusterPredictionResponse.class)
            .timeout(Duration.ofSeconds(60));
    }
}
```

### 8.3 Resilience with Circuit Breaker

```java
@Configuration
public class ResilienceConfig {
    
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(5)
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .build();
        
        return CircuitBreakerRegistry.of(config);
    }
}

@Service
public class RagProxyService {
    
    private final WebClient ragWebClient;
    private final CircuitBreaker circuitBreaker;
    
    public RagProxyService(WebClient ragWebClient, CircuitBreakerRegistry registry) {
        this.ragWebClient = ragWebClient;
        this.circuitBreaker = registry.circuitBreaker("ai-rag-core");
    }
    
    public Mono<RagResponse> chat(String query, UUID userId, UUID sessionId) {
        return Mono.fromCallable(() -> circuitBreaker.executeSupplier(() -> 
            ragWebClient.post()
                .uri("/chat")
                .bodyValue(Map.of(
                    "query", query,
                    "user_id", userId != null ? userId.toString() : null,
                    "session_id", sessionId.toString()
                ))
                .retrieve()
                .bodyToMono(RagResponse.class)
                .block(Duration.ofSeconds(120))
        ))
        .subscribeOn(Schedulers.boundedElastic());
    }
}
```

---

## 9. Testing

### 9.1 Unit Testing

```java
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    
    @InjectMocks
    private LoginService loginService;
    
    @Test
    void login_success() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("test@example.com")
            .passwordHash("$2a$10$encoded")
            .role(Role.USER)
            .build();
        
        when(userRepository.findByEmail(request.getEmail()))
            .thenReturn(Mono.just(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
            .thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user))
            .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(user))
            .thenReturn("refresh-token");
        
        // When
        Mono<LoginResponse> result = loginService.login(request);
        
        // Then
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.accessToken().equals("access-token") &&
                response.refreshToken().equals("refresh-token"))
            .verifyComplete();
        
        verify(userRepository).findByEmail(request.getEmail());
        verify(passwordEncoder).matches(request.getPassword(), user.getPasswordHash());
        verify(jwtTokenProvider).generateAccessToken(user);
        verify(jwtTokenProvider).generateRefreshToken(user);
    }
    
    @Test
    void login_userNotFound() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        
        when(userRepository.findByEmail(request.getEmail()))
            .thenReturn(Mono.empty());
        
        // When
        Mono<LoginResponse> result = loginService.login(request);
        
        // Then
        StepVerifier.create(result)
            .expectError(AuthenticationException.class)
            .verify();
    }
}
```

### 9.2 Integration Testing with Testcontainers

```java
@SpringBootTest
@Testcontainers
class UserRepositoryIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5")
            .withAdminPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> 
            String.format("r2dbc:postgresql://%s:%s/%s", 
                postgres.getHost(), postgres.getMappedPort(5432), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("neo4j.uri", neo4j::getBoltUrl);
        registry.add("neo4j.username", () -> "neo4j");
        registry.add("neo4j.password", () -> "test");
    }
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void saveAndFindUser() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("test@example.com")
            .passwordHash("$2a$10$encoded")
            .role(Role.USER)
            .status(Status.ACTIVE)
            .build();
        
        StepVerifier.create(userRepository.save(user))
            .expectNextCount(1)
            .verifyComplete();
        
        StepVerifier.create(userRepository.findById(user.getId()))
            .expectNextMatches(saved -> saved.getEmail().equals("test@example.com"))
            .verifyComplete();
    }
}
```

### 9.3 WebFlux Controller Testing

```java
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
    
    @Mock
    private LoginUseCase loginUseCase;
    
    @Mock
    private RegisterUseCase registerUseCase;
    
    @InjectMocks
    private AuthController authController;
    
    private WebTestClient webTestClient;
    
    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(authController)
            .configureClient()
            .build();
    }
    
    @Test
    void login_success() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        LoginResponse response = new LoginResponse(
            "access-token", "refresh-token", UUID.randomUUID(), 
            "test@example.com", "USER", 900000
        );
        
        when(loginUseCase.login(request)).thenReturn(Mono.just(response));
        
        webTestClient.post()
            .uri("/api/v1/auth/login")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody(LoginResponse.class)
            .isEqualTo(response);
        
        verify(loginUseCase).login(request);
    }
}
```

---

## 10. Deployment

### 10.1 Docker Build

```dockerfile
# apps/backend/Dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 10.2 Environment Variables

```yaml
# docker-compose.yml
spring-api:
  environment:
    APP_ENV: ${APP_ENV:-dev}
    POSTGRES_HOST: postgres
    POSTGRES_PORT: "5432"
    POSTGRES_DB: techradar
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: postgres
    NEO4J_URI: bolt://neo4j:7687
    NEO4J_USERNAME: neo4j
    NEO4J_PASSWORD: password
    REDIS_HOST: redis
    REDIS_PORT: "6379"
    JWT_SECRET: ${JWT_SECRET:-change-this-in-production}
    PYTHON_RAG_BASE_URL: http://ai-rag-core:8000
    PYTHON_ML_CLUSTERING_BASE_URL: http://ml-clustering:8001
    PYTHON_INTERNAL_TOKEN: ${INTERNAL_API_TOKEN:-techradar-internal-secret}
```

### 10.3 Health Checks

```bash
# Check health
curl http://localhost:8080/health

# Check actuator endpoints
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics
```

---

## Best Practices

### 1. Use Reactive Programming

```java
// Good
public Mono<User> findById(UUID id) {
    return userRepository.findById(id);
}

// Bad (blocking)
public User findById(UUID id) {
    return userRepository.findById(id).block();
}
```

### 2. Handle Errors Properly

```java
// Good
userRepository.findById(id)
    .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found")))
    .flatMap(user -> processUser(user))
    .onErrorResume(ResourceNotFoundException.class, ex -> 
        Mono.just(ApiResponse.error(ex.getMessage(), "NOT_FOUND")));

// Bad
userRepository.findById(id)
    .flatMap(user -> processUser(user));
```

### 3. Use DTOs for API Layer

```java
// Good
public record LoginRequest(String email, String password) {}
public record LoginResponse(String accessToken, String refreshToken) {}

// Bad (exposing domain entities)
public Mono<User> login(String email, String password) {}
```

### 4. Keep Controllers Thin

```java
// Good
@RestController
public class UserController {
    private final UserProfileService userProfileService;
    
    @GetMapping("/profile")
    public Mono<ApiResponse<UserProfile>> getProfile(
            @AuthenticationPrincipal Mono<Authentication> auth) {
        return auth
            .map(Authentication::getPrincipal)
            .cast(User.class)
            .flatMap(userProfileService::getProfile)
            .map(ApiResponse::success);
    }
}

// Bad (business logic in controller)
@RestController
public class UserController {
    @GetMapping("/profile")
    public Mono<ApiResponse<UserProfile>> getProfile(
            @AuthenticationPrincipal Mono<Authentication> auth) {
        return auth
            .map(Authentication::getPrincipal)
            .cast(User.class)
            .flatMap(user -> {
                // Business logic here...
                return Mono.just(profile);
            })
            .map(ApiResponse::success);
    }
}
```

### 5. Use Configuration Classes

```java
// Good
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(...)
            .build();
    }
}

// Bad (configuration in main class)
@SpringBootApplication
public class TechRadarApplication {
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        // Configuration here...
    }
}
```

---

## Resources

- [Spring WebFlux Documentation](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Spring Data R2DBC](https://docs.spring.io/spring-data/r2dbc/reference/)
- [Neo4j Java Driver](https://neo4j.com/docs/java-manual/current/)
- [Reactive Programming with Project Reactor](https://projectreactor.io/docs)
- [Testcontainers Documentation](https://www.testcontainers.org/)
