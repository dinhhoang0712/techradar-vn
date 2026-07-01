# Development Guide — TechRadar VN

> Hướng dẫn phát triển chi tiết cho TechRadar VN, bao gồm thiết lập môi trường, quy trình phát triển và best practices.

---

## Mục lục

1. [Yêu cầu hệ thống](#1-yêu-cầu-hệ-thống)
2. [Thiết lập môi trường](#2-thiết-lập-môi-trường)
3. [Chạy hệ thống locally](#3-chạy-hệ-thống-locally)
4. [Quy trình phát triển](#4-quy-trình-phát-triển)
5. [Coding Standards](#5-coding-standards)
6. [Git Workflow](#6-git-workflow)
7. [Testing](#7-testing)
8. [Debugging](#8-debugging)
9. [Troubleshooting](#9-troubleshooting)
10. [Contributing](#10-contributing)

---

## 1. Yêu cầu hệ thống

### 1.1 Phần mềm bắt buộc

| Phần mềm | Version tối thiểu | Mô tả |
|----------|------------------|-------|
| **Java** | 21+ | Backend development |
| **Node.js** | 20+ | Frontend development |
| **Python** | 3.11+ | AI services development |
| **Docker** | 24+ | Containerization |
| **Docker Compose** | v2 | Container orchestration |
| **Git** | 2.30+ | Version control |
| **Maven** | 3.9+ | Java build tool (tùy chọn, dùng Maven wrapper) |
| **npm** | 9+ | JavaScript package manager |

### 1.2 IDE khuyến nghị

- **Backend**: IntelliJ IDEA (Ultimate hoặc Community)
- **Frontend**: VS Code với các extensions:
  - ESLint
  - Prettier
  - GitLens
  - Auto Rename Tag
  - Path Intellisense
- **Python Services**: VS Code với Python extension hoặc PyCharm

### 1.3 Tài nguyên hệ thống

- **RAM**: Tối thiểu 8GB (khuyến nghị 16GB)
- **Disk**: Tối thiểu 20GB free space
- **CPU**: Multi-core processor (4+ cores khuyến nghị)

---

## 2. Thiết lập môi trường

### 2.1 Clone repository

```bash
git clone https://github.com/dinhhoang0712/techradar-vn.git
cd techradar-vn
```

### 2.2 Backend Setup (Spring Boot)

```bash
cd apps/backend

# Sử dụng Maven wrapper (không cần cài Maven)
./mvnw clean install

# Hoặc dùng Maven nếu đã cài
mvn clean install

# Chạy tests
./mvnw test

# Chạy application locally
./mvnw spring-boot:run
```

**Cấu hình môi trường:**

Tạo file `apps/backend/src/main/resources/application-local.yml`:

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/techradar
    username: postgres
    password: postgres
  data:
    redis:
      host: localhost
      port: 6379

neo4j:
  uri: bolt://localhost:7687
  username: neo4j
  password: password

jwt:
  secret: your-local-jwt-secret-at-least-256-bits
  access-token-expiration: 900000
  refresh-token-expiration: 604800000

python:
  rag:
    base-url: http://localhost:8000
  ml:
    clustering:
      base-url: http://localhost:8001
  internal:
    token: techradar-internal-secret
```

### 2.3 Frontend Setup (React)

```bash
cd apps/web

# Install dependencies
npm install

# Start development server
npm run dev

# Run linter
npm run lint

# Run tests
npm test
npm run test:watch
```

**Cấu hình môi trường:**

Tạo file `apps/web/.env.local`:

```env
VITE_API_URL=http://localhost:8080
```

### 2.4 AI Services Setup

#### ai-rag-core

```bash
cd services/ai-rag-core

# Create virtual environment
python -m venv .venv
source .venv/bin/activate  # On Windows: .venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Run service
MODEL_WARMUP=background uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

**Cấu hình môi trường:**

File `.env` ở project root:

```env
NEO4J_URI=bolt://localhost:7687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=password
USE_LOCAL_NEO4J=true
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=techradar
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
REDIS_URL=redis://localhost:6379
LLM_PROVIDER=openai
OPENAI_API_KEY=your-openai-api-key
INTERNAL_API_TOKEN=techradar-internal-secret
CORS_ORIGINS=*
```

#### ml-clustering

```bash
cd services/ml-clustering

# Create virtual environment
python -m venv .venv
source .venv/bin/activate  # On Windows: .venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Run service
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

### 2.5 Database Setup

#### PostgreSQL

**Sử dụng Docker:**

```bash
docker run -d \
  --name techradar-postgres \
  -e POSTGRES_DB=techradar \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16-alpine
```

**Hoặc cài locally:**

- Download PostgreSQL 16 từ https://www.postgresql.org/download/
- Tạo database `techradar`
- Flyway migrations sẽ tự chạy khi Spring Boot start

#### Neo4j

**Sử dụng Docker:**

```bash
docker run -d \
  --name techradar-neo4j \
  -e NEO4J_AUTH=neo4j/password \
  -p 7474:7474 \
  -p 7687:7687 \
  neo4j:5
```

**Hoặc cài locally:**

- Download Neo4j Desktop từ https://neo4j.com/download/
- Tạo database với user/password: `neo4j/password`

#### Redis

**Sử dụng Docker:**

```bash
docker run -d \
  --name techradar-redis \
  -p 6379:6379 \
  redis:7-alpine
```

**Hoặc cài locally:**

- Download Redis từ https://redis.io/download
- Chạy `redis-server`

### 2.6 Knowledge Graph Setup

```bash
cd knowledge-graph

# Install dependencies
pip install -r requirements.txt

# Run complete pipeline
python utils/run_complete_pipeline.py
```

### 2.7 Data Platform Setup

```bash
cd data-platform

# Install dependencies
pip install -r requirements.txt

# Copy example env
cp .env.example .env

# Edit .env with your configuration
# Then run
python main.py
```

---

## 3. Chạy hệ thống locally

### 3.1 Chạy toàn bộ với Docker Compose (Khuyến nghị)

```bash
# Copy example env
cp .env.docker.example .env

# Edit .env with your API keys
# OPENAI_API_KEY=your-key
# GEMINI_API_KEY=your-key

# Start all services
docker compose up --build

# Start with vector pipeline
docker compose --profile vector up --build

# Start with crawler
docker compose --profile crawl up --build

# Start with observability
docker compose --profile observability up --build
```

**Truy cập services:**

| Service | URL |
|---------|-----|
| Web App | http://localhost:5173 |
| API Gateway | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| ai-rag-core | http://localhost:8000/docs |
| ml-clustering | http://localhost:8001/docs |
| Neo4j Browser | http://localhost:7474 |
| MinIO Console | http://localhost:9001 |
| MailHog | http://localhost:8025 |
| Grafana | http://localhost:3001 (observability profile) |

### 3.2 Chạy từng service riêng

**Backend:**

```bash
cd apps/backend
./mvnw spring-boot:run
```

**Frontend:**

```bash
cd apps/web
npm run dev
```

**ai-rag-core:**

```bash
cd services/ai-rag-core
source .venv/bin/activate
MODEL_WARMUP=background uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

**ml-clustering:**

```bash
cd services/ml-clustering
source .venv/bin/activate
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

### 3.3 Kiểm tra health

```bash
# Backend health
curl http://localhost:8080/health

# ai-rag-core health
curl http://localhost:8000/health

# ml-clustering health
curl http://localhost:8001/health

# PostgreSQL
docker exec techradar-postgres pg_isready -U postgres

# Neo4j
docker exec techradar-neo4j cypher-shell -u neo4j -p password "RETURN 1"
```

---

## 4. Quy trình phát triển

### 4.1 Branch Strategy

```
main (production)
  ↑
develop (integration)
  ↑
feature/ticket-description (feature branches)
hotfix/critical-fix (hotfix branches)
```

### 4.2 Workflow

1. **Tạo feature branch từ develop**

```bash
git checkout develop
git pull origin develop
git checkout -b feature/TECH-123-add-new-feature
```

2. **Thực hiện thay đổi**

```bash
# Make changes
git add .
git commit -m "TECH-123: Add new feature"
```

3. **Push và tạo Pull Request**

```bash
git push origin feature/TECH-123-add-new-feature
# Create PR on GitHub/GitLab
```

4. **Code Review**

- Request review từ team
- Address feedback
- Update PR nếu cần

5. **Merge vào develop**

- Sau khi approved, merge vào develop
- Delete feature branch

6. **Release**

- Merge develop vào main khi ready
- Create release tag
- Deploy to production

### 4.3 Commit Message Convention

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks
- `perf`: Performance improvements

**Examples:**

```
feat(auth): add refresh token rotation

- Implement refresh token rotation logic
- Add blacklist for revoked tokens in Redis
- Update JWT token provider

Closes TECH-123
```

```
fix(radar): resolve memory leak in chart rendering

The chart component was not properly cleaning up
event listeners on unmount, causing memory leaks.

Fixes TECH-456
```

---

## 5. Coding Standards

### 5.1 Java (Backend)

**Naming Conventions:**

```java
// Classes: PascalCase
public class UserService {}

// Methods: camelCase
public Mono<User> findById(UUID id) {}

// Constants: UPPER_SNAKE_CASE
public static final int MAX_RETRY_ATTEMPTS = 3;

// Variables: camelCase
private final UserRepository userRepository;

// Packages: lowercase
package com.techpulse.features.user;
```

**Code Style:**

- Use 4 spaces for indentation
- Maximum line length: 120 characters
- Use meaningful names
- Add Javadoc for public APIs
- Follow Clean Code principles

**Example:**

```java
/**
 * Service for managing user profiles.
 * 
 * @author TechRadar Team
 * @since 1.0.0
 */
@Service
public class UserProfileService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserProfileService.class);
    
    private final UserRepository userRepository;
    private final AvatarService avatarService;
    
    /**
     * Constructs a new UserProfileService.
     *
     * @param userRepository the user repository
     * @param avatarService the avatar service
     */
    public UserProfileService(UserRepository userRepository, 
                              AvatarService avatarService) {
        this.userRepository = userRepository;
        this.avatarService = avatarService;
    }
    
    /**
     * Retrieves the user profile for the given user ID.
     *
     * @param userId the user ID
     * @return a Mono emitting the user profile
     * @throws ResourceNotFoundException if user not found
     */
    public Mono<UserProfile> getProfile(UUID userId) {
        return userRepository.findById(userId)
            .switchIfEmpty(Mono.error(
                new ResourceNotFoundException("User not found: " + userId)))
            .map(this::mapToProfile);
    }
}
```

### 5.2 JavaScript/React (Frontend)

**Naming Conventions:**

```jsx
// Components: PascalCase
const UserProfile = () => {};

// Functions/Variables: camelCase
const handleButtonClick = () => {};

// Constants: UPPER_SNAKE_CASE
const MAX_RETRY_COUNT = 3;

// Files: PascalCase for components, camelCase for utilities
// UserProfile.jsx
// formatters.js
```

**Code Style:**

- Use 2 spaces for indentation
- Maximum line length: 100 characters
- Use functional components with hooks
- Prefer arrow functions
- Add PropTypes or TypeScript

**Example:**

```jsx
/**
 * UserProfile component displays user profile information.
 * 
 * @param {Object} props - Component props
 * @param {Object} props.user - User data
 * @param {Function} props.onUpdate - Callback for updates
 */
const UserProfile = ({ user, onUpdate }) => {
  const [isEditing, setIsEditing] = useState(false);
  const [formData, setFormData] = useState({ ...user });

  const handleSave = async () => {
    try {
      await updateUserProfile(formData);
      onUpdate(formData);
      setIsEditing(false);
    } catch (error) {
      console.error('Failed to update profile:', error);
    }
  };

  return (
    <div className="user-profile">
      {isEditing ? (
        <EditForm 
          data={formData} 
          onChange={setFormData}
          onSave={handleSave}
          onCancel={() => setIsEditing(false)}
        />
      ) : (
        <ProfileView user={user} onEdit={() => setIsEditing(true)} />
      )}
    </div>
  );
};

UserProfile.propTypes = {
  user: PropTypes.shape({
    id: PropTypes.string.isRequired,
    email: PropTypes.string.isRequired,
    fullName: PropTypes.string.isRequired,
  }).isRequired,
  onUpdate: PropTypes.func.isRequired,
};
```

### 5.3 Python (AI Services)

**Naming Conventions:**

```python
# Classes: PascalCase
class UserService:

# Functions/Variables: snake_case
def get_user_profile(user_id):

# Constants: UPPER_SNAKE_CASE
MAX_RETRY_ATTEMPTS = 3

# Modules: lowercase
# user_service.py
```

**Code Style:**

- Follow PEP 8
- Use 4 spaces for indentation
- Maximum line length: 88 characters (Black default)
- Add docstrings for functions/classes
- Use type hints

**Example:**

```python
"""
Service for managing user profiles.
"""

from typing import Optional
from pydantic import BaseModel


class UserProfile(BaseModel):
    """User profile model."""
    
    id: str
    email: str
    full_name: str
    bio: Optional[str] = None


async def get_user_profile(user_id: str) -> UserProfile:
    """
    Retrieve user profile by ID.
    
    Args:
        user_id: The user ID
        
    Returns:
        UserProfile: The user profile
        
    Raises:
        UserNotFoundError: If user not found
    """
    user = await db.fetch_user(user_id)
    if not user:
        raise UserNotFoundError(f"User not found: {user_id}")
    return UserProfile(**user)
```

---

## 6. Git Workflow

### 6.1 .gitignore

**Backend (.gitignore):**

```
# Maven
target/
!.mvn/wrapper/maven-wrapper.jar
!**/src/main/**/target/
!**/src/test/**/target/

# IDE
.idea/
*.iml
.vscode/
*.swp
*.swo

# OS
.DS_Store
Thumbs.db

# Logs
*.log
logs/
```

**Frontend (.gitignore):**

```
# Dependencies
node_modules/
package-lock.json

# Build
dist/
build/

# IDE
.idea/
.vscode/
*.swp

# OS
.DS_Store
Thumbs.db

# Environment
.env.local
.env.*.local
```

**Python Services (.gitignore):**

```
# Virtual Environment
.venv/
venv/
ENV/

# IDE
.idea/
.vscode/
*.swp

# Python
__pycache__/
*.py[cod]
*$py.class
*.so
.Python

# ML
mlruns/
*.db
data/
models/

# Environment
.env
```

### 6.2 Pre-commit Hooks

**Install pre-commit:**

```bash
pip install pre-commit
pre-commit install
```

**.pre-commit-config.yaml:**

```yaml
repos:
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v4.5.0
    hooks:
      - id: trailing-whitespace
      - id: end-of-file-fixer
      - id: check-yaml
      - id: check-added-large-files
        args: ['--maxkb=1000']
  
  - repo: https://github.com/psf/black
    rev: 24.3.0
    hooks:
      - id: black
        language_version: python3.11
  
  - repo: https://github.com/pycqa/isort
    rev: 5.13.2
    hooks:
      - id: isort
        args: ["--profile", "black"]
```

---

## 7. Testing

### 7.1 Backend Testing

**Unit Tests:**

```bash
cd apps/backend
./mvnw test
```

**Integration Tests:**

```bash
cd apps/backend
./mvnw verify
```

**Test Coverage:**

```bash
cd apps/backend
./mvnw jacoco:report
# Report at target/site/jacoco/index.html
```

### 7.2 Frontend Testing

```bash
cd apps/web
npm test
npm run test:watch
```

### 7.3 Python Services Testing

```bash
cd services/ai-rag-core
pytest

cd services/ml-clustering
pytest
```

### 7.4 End-to-End Testing

```bash
# Run all tests
pytest tests/
```

---

## 8. Debugging

### 8.1 Backend Debugging

**Remote Debugging with IntelliJ IDEA:**

1. Add JVM args to `application.yml`:

```yaml
spring:
  devtools:
    remote:
      secret: mysecret
```

2. Run with debug:

```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
```

3. Configure Remote Debug in IntelliJ IDEA:
   - Run → Edit Configurations → Add Remote
   - Host: localhost, Port: 5005

### 8.2 Frontend Debugging

**Chrome DevTools:**

- Open DevTools (F12)
- Use React DevTools extension
- Set breakpoints in Sources tab
- Use console.log for debugging

**VS Code Debugging:**

Create `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "chrome",
      "request": "launch",
      "name": "Launch Chrome",
      "url": "http://localhost:5173",
      "webRoot": "${workspaceFolder}/apps/web"
    }
  ]
}
```

### 8.3 Python Debugging

**VS Code Debugging:**

Create `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "name": "Python: FastAPI",
      "type": "debugpy",
      "request": "launch",
      "module": "uvicorn",
      "args": [
        "app.main:app",
        "--host",
        "0.0.0.0",
        "--port",
        "8000",
        "--reload"
      ],
      "cwd": "${workspaceFolder}/services/ai-rag-core",
      "env": {
        "PYTHONPATH": "${workspaceFolder}"
      }
    }
  ]
}
```

---

## 9. Troubleshooting

### 9.1 Common Issues

**Backend không start được:**

```bash
# Check PostgreSQL connection
docker exec techradar-postgres pg_isready -U postgres

# Check Neo4j connection
docker exec techradar-neo4j cypher-shell -u neo4j -p password "RETURN 1"

# Check logs
docker logs techradar-spring-api
```

**Frontend không kết nối được API:**

```bash
# Check API is running
curl http://localhost:8080/health

# Check CORS configuration
# Verify VITE_API_URL in .env.local
```

**AI services không load được model:**

```bash
# Check HuggingFace connection
# Verify OPENAI_API_KEY or GEMINI_API_KEY
# Check model warmup logs
docker logs techradar-rag
```

**Docker compose build fails:**

```bash
# Clear Docker cache
docker system prune -a

# Rebuild specific service
docker compose up --build -d <service-name>

# Check disk space
docker system df
```

### 9.2 Log Locations

**Backend:**
- Console output (dev)
- Log file (prod): `/var/log/techradar/`

**Frontend:**
- Browser console
- Vite dev server output

**AI Services:**
- Console output
- Docker logs: `docker logs <container-name>`

### 9.3 Performance Issues

**Slow API responses:**

```bash
# Check database connection pool
# Enable query logging in application.yml
logging:
  level:
    org.springframework.r2dbc: DEBUG
```

**Memory issues:**

```bash
# Check container memory usage
docker stats

# Increase memory in docker-compose.yml
services:
  spring-api:
    deploy:
      resources:
        limits:
          memory: 2G
```

---

## 10. Contributing

### 10.1 Pull Request Checklist

- [ ] Code follows project style guidelines
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] Commit messages follow convention
- [ ] No merge conflicts
- [ ] All tests pass
- [ ] No linting errors
- [ ] PR description clearly explains changes

### 10.2 Code Review Guidelines

**Reviewer:**

- Check for bugs and edge cases
- Verify code follows best practices
- Ensure tests are adequate
- Check for security issues
- Verify documentation is accurate

**Author:**

- Address all review comments
- Update PR as needed
- Be responsive to feedback
- Ask questions if unclear

### 10.3 Release Process

1. Update version numbers
2. Update CHANGELOG.md
3. Create release branch
4. Run full test suite
5. Merge to main
6. Create git tag
7. Deploy to production
8. Announce release

---

## Resources

### Documentation

- [Architecture Overview](./ARCHITECTURE.md)
- [Backend Guide](./BACKEND_GUIDE.md)
- [Frontend Guide](./FRONTEND_GUIDE.md)
- [AI Platform](./AI_PLATFORM.md)
- [API Documentation](./API_DOCs_v1.md)
- [Deployment Guide](./DEPLOYMENT.md)
- [Knowledge Graph](../knowledge-graph/README.md)
- [Data Platform](../data-platform/README.md)

### External Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/)
- [React Documentation](https://react.dev/)
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
- [Neo4j Documentation](https://neo4j.com/docs/)
- [Docker Documentation](https://docs.docker.com/)

### Support

- GitHub Issues: https://github.com/dinhhoang0712/techradar-vn/issues
- Discussions: https://github.com/dinhhoang0712/techradar-vn/discussions

---

## Quick Reference

### Useful Commands

```bash
# Start everything
docker compose up --build

# Stop everything
docker compose down

# View logs
docker logs -f techradar-spring-api

# Run backend tests
cd apps/backend && ./mvnw test

# Run frontend tests
cd apps/web && npm test

# Run Python tests
cd services/ai-rag-core && pytest

# Format Python code
black .

# Format Java code (IntelliJ: Ctrl+Alt+L)

# Format JavaScript code (VS Code: Shift+Alt+F)
```

### Environment Variables

```bash
# Backend
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=techradar
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
NEO4J_URI=bolt://localhost:7687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=password
JWT_SECRET=your-secret
PYTHON_RAG_BASE_URL=http://localhost:8000
PYTHON_ML_CLUSTERING_BASE_URL=http://localhost:8001
INTERNAL_API_TOKEN=techradar-internal-secret

# Frontend
VITE_API_URL=http://localhost:8080

# AI Services
OPENAI_API_KEY=your-key
GEMINI_API_KEY=your-key
LLM_PROVIDER=openai
```

### Default Credentials

- **Admin User**: admin@techradar.vn / Admin@12345 (dev mode)
- **PostgreSQL**: postgres / postgres
- **Neo4j**: neo4j / password
- **MinIO**: minioadmin / minioadmin123
