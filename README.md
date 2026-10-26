# Leave Application System

Employees apply for leave in a React portal, a Spring Boot API starts a **Camunda 8** BPMN process,
and managers approve or reject from an inbox. Automated steps (validation, finalisation) run as
Spring Boot job workers.


### Folder Structure

```
leave-system/
├── bpmn/                         leave-request.bpmn + leave-approval.form 
├── backend/                      Spring Boot 3.5 · Java 21
│   └── src/main/resources/bpmn/  copy of the two files above 
├── frontend/                     React + TypeScript + Vite
├── camunda/docker-compose.yml    Camunda 8 only (for local development)
├── docker-compose.yml            Full stack: Camunda + PostgreSQL + backend + frontend
└── .github/workflows/ci.yml      Build checks
```

### Workflow

`Leave requested` → **validate-leave** (worker) → `Request valid?`
- valid → **Manager approval** (user task, form `leave-approval`) → `Approved?`
  - approved → **finalize-leave** (`outcome=APPROVED`) → *Leave approved*
  - rejected → merge
- invalid → merge → **finalize-leave** (`outcome=REJECTED`) → *Leave rejected*

Business rules: weekdays only (Mon–Fri, no holiday calendar), 20 annual days by default, annual leave
is deducted only on approval, and finalisation is idempotent so retried jobs can't deduct twice.

### Run everything with Docker

```bash
cp .env.example .env        # then edit .env: set CAMUNDA_PASSWORD (and POSTGRES_PASSWORD)
docker compose up --build
```

| Service | URL |
|---|---|
| Leave portal | http://localhost:3000 |
| Spring Boot API | http://localhost:8081 |
| Camunda Operate / Tasklist | http://localhost:8080/operate · /tasklist |

Camunda needs a minute to start; the backend waits for it and restarts itself if needed.

### Run locally for development

Requires Java 21, Maven 3.9+, Node 20+, Docker.

```bash
# 1. Camunda - then open http://localhost:8080 and create the admin user (first start only)
cd camunda && docker compose up -d

# 2. Backend (H2 file database in backend/data) - log in to Camunda with that user
cd backend
export CAMUNDA_USERNAME=admin CAMUNDA_PASSWORD='...'      # PowerShell: $env:CAMUNDA_USERNAME='admin'; $env:CAMUNDA_PASSWORD='...'
mvn spring-boot:run                        # http://localhost:8081, H2 console at /h2-console

# 3. Frontend
cd frontend && npm install && npm run dev  # http://localhost:5173 (proxies /api to :8081)
```

Run the unit tests with `cd backend && mvn test`.





