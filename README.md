# AthletiqAPI

API REST d'Athletiq, consommée par l'app web coach et l'app mobile athlète.

**Stack** : Java 21 · Spring Boot 3.5 · Spring Security (JWT HS256, resource server) · Spring Data JPA · MySQL 8.4 · Flyway · MapStruct · Lombok · springdoc-openapi · Testcontainers.

## Démarrer

```bash
cp .env.example .env               # renseigner ATHLETIQ_JWT_SECRET (openssl rand -base64 32) et l'admin
docker compose up -d               # MySQL de développement
./mvnw spring-boot:run             # profil dev par défaut, http://localhost:8080
./mvnw test                        # nécessite Docker (Testcontainers)
```

Swagger UI (profil `dev` uniquement) : http://localhost:8080/swagger-ui.html

## Arborescence

```
src/main/java/com/athletiq/
├── common/{config,security,exception,persistence}
├── auth/ · organisation/ · coach/ · athlete/ · group/
└── exercise/ · workout/ · planning/ · performance/
src/main/resources/
├── application.yml, application-{dev,prod}.yml
└── db/migration/                  (V<n>__<description>.sql)
```

Conventions : skill grepo `back-spring-boot-conventions`.
