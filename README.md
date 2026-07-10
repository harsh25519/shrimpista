# Shrimpista

A production-inspired URL shortening service built with **Spring Boot** that supports authenticated and anonymous URL shortening, click analytics, Redis-powered caching, asynchronous event processing, and integration with **mtAuth**, a standalone multi-tenant authentication platform.

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.7-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Neon-blue)
![Redis](https://img.shields.io/badge/Redis-Cloud-red)

---

## Features

- Short URL generation using Base62 encoding
- Anonymous and authenticated URL shortening
- Google & GitHub OAuth authentication (via mtAuth)
- Local email/password authentication (via mtAuth)
- JWT-based authorization
- URL management dashboard
- URL expiration support
- Enable/Disable URLs
- Soft deletion
- Click analytics
- Unique visitor estimation using Redis HyperLogLog
- Redis-backed redirect cache
- Asynchronous click event processing
- Admin moderation endpoints

---

## Architecture

Shrimpista delegates all authentication and authorization responsibilities to **mtAuth**.

```
Browser / Client
        │
        ▼
   Shrimpista
        │
 ┌──────┴─────────┐
 │                │
 ▼                ▼
PostgreSQL      Redis
 │                │
 │         Cache
 │         Event Queue
 │         HyperLogLog
 │
 ▼
mtAuth
(Authentication Provider)
```

For the complete architecture and request flows, see:

-🏗️ ️ [Architecture](docs/images/shrimpista.png)

---

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 17 |
| Framework | Spring Boot 4.0.7 |
| Build Tool | Maven |
| Database | PostgreSQL |
| Production Database | Neon PostgreSQL |
| Cache | Redis |
| Production Cache | Redis Cloud |
| Security | Spring Security + JWT |
| OAuth Providers | Google, GitHub |

---

## Authentication

Shrimpista does **not** maintain its own user accounts.

Authentication is delegated to **mtAuth**, which provides:

- User registration
- Login
- JWT Access Tokens
- Refresh Tokens
- OAuth Login
- Email Verification
- Password Reset

Shrimpista validates JWTs issued by mtAuth and extracts the authenticated principal for authorization.

---

## Analytics Pipeline

Click analytics are processed asynchronously.

```
Redirect
     │
     ▼
Redis Queue
     │
     ▼
Scheduled Aggregation
     │
     ▼
PostgreSQL
```

Unique visitor counts are estimated using Redis HyperLogLog.

---

## Running Locally

### Requirements

- Java 17
- Maven
- PostgreSQL
- Redis
- mtAuth integration

---

### Clone

```bash
git clone https://github.com/harsh25519/shrimpista.git
cd shrimpista
```

---

### Configure

Update `application.yml`.

Development

- PostgreSQL
- Local Redis

Production

- Neon PostgreSQL
- Redis Cloud

---

### Build

```bash
./mvnw clean package
```

---

### Run

```bash
./mvnw spring-boot:run
```

---

## Project Structure

```
src
├── controller
├── service
├── security
├── repository
├── entity
├── dto
├── mapper
├── scheduler
├── config
└── exception
```

---

## Documentation

- 📖 [API Documentation](docs/API.md)
- 🏗️ [Architecture](docs/images/shrimpista.png)

---

## Related Project

Shrimpista depends on **mtAuth**, a standalone multi-tenant authentication and authorization service.

Repository:

```
https://github.com/harsh25519/mtAuth
```

---

## Future Improvements

- Kafka-based event streaming
- Distributed cache invalidation
- Custom domains
- QR code generation
- Rate limiting
- API Keys
- Dashboard enhancements

---

## License

This project is licensed under the MIT License.