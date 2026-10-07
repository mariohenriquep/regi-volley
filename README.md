# regi-volley

REST API for **RegiVolley** — a platform where amateur indoor volleyball associations manage
level-based training groups and members book a spot session by session, with a waitlist,
plans/credits and manual payment tracking.

- Product vision & requirements: [`docs/requirements.md`](docs/requirements.md)
- Architecture rules: [`docs/architecture.md`](docs/architecture.md)

## Stack

Java 21 · Spring Boot 4.1 · PostgreSQL + Flyway · JUnit 5, Mockito, AssertJ, Testcontainers ·
JaCoCo (≥ 85%) · Maven wrapper

## Running it

One-time per machine: copy `docs/toolchains.sample.xml` to `~/.m2/toolchains.xml` and point it
at a JDK 21.

```bash
docker compose up -d       # local Postgres
./mvnw spring-boot:run     # http://localhost:8080
./mvnw verify              # tests + coverage gate (needs Docker for Testcontainers)
```

## CI pipeline

`.github/workflows/ci.yml`, with a read-only `GITHUB_TOKEN` by default:

| Job | PR | push to `main` | What it does |
|---|---|---|---|
| `build-and-test` | yes | yes | `./mvnw verify` (tests + JaCoCo >= 85%), CycloneDX SBOM artifact |
| `secret-scan` | yes | yes | Gitleaks |
| `sast` | yes | yes | Semgrep (`p/java`, `p/owasp-top-ten`, `p/secrets`) |

Container image build, scan and publish (CD) come in a later issue. Dependabot covers `maven`
and `github-actions`.