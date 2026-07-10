# Shrimpista — API Documentation

**Version:** MVP v1.0
**Stack:** Java 17 · Spring Boot · PostgreSQL · Redis

## Table of Contents

- [Overview](#overview)
- [Related Project](#related-project)
- [Base URL](#base-url)
- [Authentication](#authentication)
- [Common Response Shapes](#common-response-shapes)
- [1. Auth Endpoints](#1-auth-endpoints-proxy-to-mtauth)
- [2. OAuth Endpoints](#2-oauth-endpoints-proxy-to-mtauth)
- [3. URL Endpoints](#3-url-endpoints)
- [4. Click Events Endpoints](#4-click-events-endpoints)
- [5. Stats Endpoints](#5-stats-endpoints)
- [6. Admin Endpoints](#6-admin-endpoints)
- [Background Jobs](#background-jobs-not-http-endpoints-but-part-of-the-clickstats-pipeline)
- [Data Model Summary](#data-model-summary)
- [Redis Key Reference](#redis-key-reference)

## Overview

The URL Shortener Service (`Shrimpista`) is a Spring Boot microservice that provides short-link creation, redirection, click analytics, and admin management. It does **not** implement its own identity system — it is a **registered client** of `mtAuth`, Harsh's standalone multi-tenant authentication and authorization platform.

All identity, credential, and token-issuing concerns are delegated to `mtAuth`. This service:

- Injects its own `clientId` / `clientSecret` server-side when forwarding signup, login, and OAuth requests, so the frontend never sees or handles multi-tenant credentials.
- Validates JWTs issued by `mtAuth` locally (manual validation, no `AuthenticationManager`) and resolves them into a `JwtPrincipal` for use in `@AuthenticationPrincipal`.
- Owns its own domain data: short links, click events, and aggregated stats.

### Distributed JWT Revocation

JWT access tokens are validated locally and statelessly — Shrimpista does not call mtAuth on every request. Immediate revocation (logout) is still supported, via Redis Pub/Sub rather than a synchronous introspection call:

1. When a user logs out (or a token is otherwise revoked), mtAuth publishes a message on the `auth:blacklist` Redis Pub/Sub channel, in the form `{jti}:{remainingMillis}` — the token's JWT ID and how many milliseconds are left until it would have expired naturally.
2. Shrimpista's `BlacklistEventSubscriber` (registered against `auth:blacklist` via a `RedisMessageListenerContainer`) receives the message and writes `blacklist:{jti} = "true"` into its own Redis, with a TTL set to exactly `remainingMillis`.
3. Because the TTL mirrors the token's own remaining lifetime, blacklist entries expire on their own the moment the token would have anyway — no separate cleanup job is needed.
4. `TokenBlacklistService.isBlacklisted(jti)` is checked during JWT validation; a `jti` present in the local blacklist causes the token to be rejected even though its signature and expiry are otherwise still valid.

```
mtAuth: user logs out
      │
mtAuth blacklists jti, publishes "{jti}:{remainingMillis}" on auth:blacklist
      │
Shrimpista's BlacklistEventSubscriber receives the message
      │
Local Redis key blacklist:{jti} set with TTL = remainingMillis
      │
Subsequent requests with that JWT are rejected during validation
```

This keeps JWT validation stateless and fast (no per-request network call to mtAuth) while still achieving near-immediate cross-service revocation once the Pub/Sub message is delivered. The trade-off is the usual one for Pub/Sub-based invalidation: propagation isn't transactionally guaranteed, so there's a small window between mtAuth publishing the event and Shrimpista's subscriber processing it during which a just-revoked token could still pass local validation.

```
Client (browser/app)
      │
      ▼
Shrimpista  ──── proxies auth/OAuth ────▶  mtAuth (identity provider)
      │
      ├── PostgreSQL (Url, ClickEvent, UrlStats)
      └── Redis (routing cache, click event queue, HyperLogLog unique visitors)
```

---

## Related Project

Shrimpista relies on **mtAuth**, a standalone multi-tenant authentication and authorization service, for:

- Local email/password authentication
- OAuth (Google / GitHub)
- JWT access/refresh token issuance
- Refresh token rotation
- Email verification
- Password reset

Shrimpista holds no user credentials itself — it is a registered client (`clientId`/`clientSecret`) of mtAuth, and all identity concerns are proxied through the endpoints documented in sections 1 and 2 below.

## Base URL

```
https://shrimpista.onrender.com/
```

All endpoints below are relative to this base URL.

---

## Authentication

Endpoints that require authentication expect a bearer JWT (issued by `mtAuth` on login/refresh/OAuth exchange) in the `Authorization` header:

```
Authorization: Bearer <accessToken>
```

The service resolves this token into a `JwtPrincipal`:

```java
public record JwtPrincipal(
        UUID userId,
        UUID clientId,
        List<String> roles
)
```

- Endpoints marked **Public** require no token.
- Endpoints marked **Authenticated** require `isAuthenticated()` — any valid JWT.
- Endpoints marked **Admin** require the `ADMIN` authority/role (`@PreAuthorize("hasAuthority('ADMIN') or hasRole('ADMIN')")`).
- The `POST /urls` endpoint accepts an **optional** principal — anonymous users can shorten links, but the resulting link won't be attached to an account (`userId` is `null`).

### Distributed JWT Revocation

Shrimpista validates JWT access tokens locally without contacting mtAuth on every request.

To support immediate logout across services, mtAuth publishes JWT revocation events using Redis Pub/Sub.

Shrimpista subscribes to these events and maintains a local blacklist of revoked JWT IDs (JTIs), allowing revoked access tokens to be rejected without remote token introspection.

Current implementation:
- Local JWT validation
- Redis Pub/Sub for revocation propagation
- Shared Redis instance with mtAuth

Future:
- Kafka event bus for distributed services

---

## Common Response Shapes

### `MessageResponse`

Generic acknowledgement payload, used for signup, verification, and password-reset flows.

```json
{
  "message": "string"
}
```

### `ErrorResponse`

Returned by the global exception handler for all error conditions.

```json
{
  "status": 404,
  "message": "URL not found",
  "timestamp": "2026-07-09T10:15:30Z"
}
```

> Exact field set is defined by `GlobalExceptionHandler` / `ErrorResponse`; the shape above reflects the standard fields used across the handler's mapped exceptions.

### Exceptions mapped by `GlobalExceptionHandler`

| Exception | Typical HTTP Status | Meaning |
|---|---|---|
| `UrlNotFoundException` | 404 Not Found | Short code/URL ID doesn't exist, or is masked to prevent ownership enumeration |
| `UrlExpiredException` | 410 Gone (or 404, per handler) | Link's `expiresAt` has passed |
| `UrlDisabledException` | 403 / 404 (per handler) | Link was soft-deleted / deactivated |
| `AccessDeniedException` | 403 Forbidden | Authenticated user does not own the resource |
| `AuthServiceException` | Passed through from `mtAuth`, or 503 | `mtAuth` rejected the request, or is unreachable |

---

## 1. Auth Endpoints (proxy to `mtAuth`)

Base path: `/auth`

These endpoints don't implement authentication themselves — `AuthController` forwards requests to `mtAuth` via `AuthClientService`, which injects this service's `clientId` (and `clientSecret`, where required) before calling `mtAuth`.

### `POST /auth/signup`

Registers a new user account with `mtAuth` under this service's client.

**Auth:** Public

**Request body** (`UserSignupRequest`):

```json
{
  "email": "user@example.com",
  "password": "string"
}
```

| Field | Type | Constraints |
|---|---|---|
| `email` | string | `@NotBlank`, `@Email` |
| `password` | string | `@NotBlank` |

**Response:** `200 OK` — `MessageResponse`

```json
{ "message": "Signup successful, please verify your email." }
```

**Internally forwards** as `AuthServiceSignupRequest` with `email`, `password`, `clientId`, `clientSecret` injected server-side:

```java
public record AuthServiceSignupRequest(
        String email,
        String password,
        String clientId,
        String clientSecret
) {}
```

---

### `POST /auth/login`

Authenticates a user against `mtAuth` and returns a token pair.

**Auth:** Public

**Request body** (`UserLoginRequest`):

```json
{
  "email": "user@example.com",
  "password": "string"
}
```

| Field | Type | Constraints |
|---|---|---|
| `email` | string | `@NotBlank`, `@Email` |
| `password` | string | `@NotBlank` |

**Response:** `200 OK` — `AuthResponse`

```json
{
  "accessToken": "string",
  "refreshToken": "string",
  "tokenType": "Bearer"
}
```

**Internally forwards** as `AuthServiceLoginRequest` with `email`, `password`, `clientId`:

```java
public record AuthServiceLoginRequest(
        String email,
        String password,
        String clientId
) {}
```

---

### `POST /auth/refresh`

Exchanges a refresh token for a new access/refresh pair (rotation happens on `mtAuth`'s side).

**Auth:** Public (the refresh token itself is the credential)

**Request body** (`RefreshRequest`):

```json
{
  "refreshToken": "string"
}
```

| Field | Type | Constraints |
|---|---|---|
| `refreshToken` | string | `@NotBlank` |

**Response:** `200 OK` — `AuthResponse` (same shape as login)

---

### `POST /auth/logout`

Revokes/blacklists the caller's current token on `mtAuth`.

**Auth:** Requires `Authorization` header (forwarded as-is)

**Headers:**

| Header | Required | Description |
|---|---|---|
| `Authorization` | Yes | Bearer token to invalidate |

**Response:** `204 No Content`

**Behavior notes:**
- If `mtAuth` rejects the token (e.g. already invalid/expired), the failure is logged and swallowed — logout is treated as a no-op rather than surfaced as an error to the client.
- If `mtAuth` is unreachable, a `503 Service Unavailable` `AuthServiceException` is thrown.
- On a successful revocation, `mtAuth` blacklists the token's `jti` and publishes it on the `auth:blacklist` Redis Pub/Sub channel; Shrimpista's own subscriber picks this up and updates its local blacklist so subsequent requests bearing that token are rejected during validation without a round-trip to `mtAuth`. See [Distributed JWT Revocation](#distributed-jwt-revocation) above for the full flow.

---

### `POST /auth/resend-verification`

Requests a fresh verification email from `mtAuth`.

**Auth:** Public

**Request body** (`ResendVerificationRequest`):

```json
{
  "email": "user@example.com"
}
```

**Response:** `200 OK` — `MessageResponse`

**Internally forwards** as `AuthResendVerificationRequest` with `email`, `clientId`:

```java
public record AuthResendVerificationRequest(
        String email,
        String clientId
) {}
```

---

### `POST /auth/forgot-password`

Initiates a password-reset flow via `mtAuth`.

**Auth:** Public

**Request body** (`ForgotPasswordRequest`):

```json
{
  "email": "user@example.com"
}
```

**Response:** `200 OK` — `MessageResponse`

**Internally forwards** as `AuthForgotPasswordRequest` with `email`, `clientId`:

```java
public record AuthForgotPasswordRequest(
        String email,
        String clientId
) {}
```

---

## 2. OAuth Endpoints (proxy to `mtAuth`)

Base path: `/oauth`

`OAuthController` never exposes this service's `clientId`/`clientSecret` to the frontend — the browser is only ever redirected to `mtAuth`, and the final code exchange happens server-to-server.

### `GET /oauth/{provider}/start`

Redirects the browser to `mtAuth`'s OAuth start endpoint for the given provider, with this service's `clientId` attached as a query param.

**Auth:** Public

**Path parameters:**

| Param | Type | Values |
|---|---|---|
| `provider` | `OAuthProvider` (enum) | `GOOGLE`, `GITHUB` |

**Response:** `302 Found` — `Location` header points to:

```
{auth-service.base-url}/oauth/{provider}/start?clientId={clientId}
```

---

### `GET /oauth/callback`

Completes the OAuth flow. Receives the short-lived, single-use "bridge code" that `mtAuth` issued after the provider redirected back to it, and exchanges it server-side for a real token pair.

**Auth:** Public (the bridge `code` is the credential; it is burn-after-read with a short Redis TTL on `mtAuth`)

**Query parameters:**

| Param | Type | Required |
|---|---|---|
| `code` | string | Yes |

**Response:** `200 OK` — `AuthResponse`

```json
{
  "accessToken": "string",
  "refreshToken": "string",
  "tokenType": "Bearer"
}
```

**Internally forwards** to `mtAuth`'s `/oauth/exchange` as:

```java
public record OAuthExchangeRequest(
        String code,
        String clientId,
        String clientSecret
) {}
```

**Errors:** `503 Service Unavailable` if `mtAuth` cannot be reached.

---

### Supporting types

```java
public enum OAuthProvider {
    GOOGLE,
    GITHUB
}
```

OAuthLoginRequest — reserved for token-based OAuth login (not currently wired to a controller)
```json
{
  "token": "string",
  "authProvider": "string"
}
```

`AuthServiceOAuthRequest` is the corresponding internal DTO that would carry a client-injected `clientId`/`clientSecret` alongside that token/provider pair when forwarding to `mtAuth`. Like `OAuthLoginRequest`, it isn't referenced by `OAuthClientService` yet — the currently-wired flow is the redirect-based `start` → `callback` → `OAuthExchangeRequest` path documented above, not a direct token exchange.

```java
public record AuthServiceOAuthRequest(
        String token,
        String authProvider,
        String clientId,
        String clientSecret
) {}
```

---

## 3. URL Endpoints

Base path: `/urls`

Core short-link lifecycle: create, redirect, list, update, toggle, delete. Backed by PostgreSQL with a Redis cache-aside layer for hot redirects.

### `POST /urls`

Creates a new short link for a long URL. Deduplicates by content hash — a user (or anonymous caller) requesting the same URL twice gets back the same short code rather than a duplicate row.

**Auth:** Optional (works for both authenticated and anonymous callers)

**Request body** (`UrlCreateRequest`):

```json
{
  "longUrl": "https://example.com/some/very/long/path",
  "title": "My link"
}
```

| Field | Type | Constraints |
|---|---|---|
| `longUrl` | string | `@NotBlank`, `@URL` |
| `title` | string | optional |

**Response:** `201 Created` — `UrlResponse`

```json
{
  "urlId": 123,
  "shortCode": "b7F3q",
  "longUrl": "https://example.com/some/very/long/path",
  "title": "My link",
  "createdAt": "2026-07-09T10:15:30Z"
}
```

**Behavior notes:**
- Dedup lookup is scoped by `userId` — an anonymous submission and an authenticated user's submission of the identical URL are treated as separate entries (`findByLongUrlHashAndUserId` vs. `findByLongUrlHashAndUserIdIsNull`).
- The short code is derived from the database-assigned primary key via Base62 encoding, so the row is saved with a placeholder (`"PENDING"`) and flushed first to obtain the ID.
- On creation, both `url:route:{shortCode}` (the destination) and `url:route:{shortCode}:id` (the numeric ID) are written to Redis with a configurable TTL (`url.cache-ttl-days`, default 7 days).

---

### `GET /urls/{shortCode}`

Public redirect endpoint. Resolves a short code to its destination and issues an HTTP redirect. Also fires an asynchronous click-tracking event.

**Auth:** Public

**Path parameters:**

| Param | Type |
|---|---|
| `shortCode` | string |

**Response:** `302 Found` — `Location` header set to the resolved long URL.

**Errors:**

| Condition | Exception |
|---|---|
| Short code doesn't exist / inactive | `UrlNotFoundException` |
| Link soft-deleted | `UrlDisabledException` |
| Link past `expiresAt` | `UrlExpiredException` |

**Behavior notes:**
- Cache-aside: checks `url:route:{shortCode}` and `url:route:{shortCode}:id` in Redis first; on a hit, both values are needed to short-circuit the DB. On a miss, it queries Postgres (`findByShortCodeAndIsActiveTrue`), then re-hydrates both cache keys.
- Click tracking is fire-and-forget: the resolved `urlId`, caller IP, `User-Agent`, and `Referer` headers are handed to `ClickIngestionService.publishClickEvent`, which pushes to a Redis list and updates a HyperLogLog — the redirect response itself doesn't wait on any DB write.

---

### `GET /urls/my`

Lists the authenticated user's own short links, most recent first.

**Auth:** Authenticated

**Query parameters:**

| Param | Type | Default |
|---|---|---|
| `page` | int | `0` |
| `size` | int | `10` |

**Response:** `200 OK` — `Page<UrlDashboardResponse>`

```json
{
  "content": [
    {
      "urlId": 123,
      "shortCode": "b7F3q",
      "title": "My link",
      "createdAt": "2026-07-09T10:15:30Z",
      "expiresAt": null,
      "isActive": true
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 10
}
```

**Behavior notes:**
- Excludes soft-deleted URLs.
- Batch-fetches stats for the returned page to avoid N+1 queries — though the current `UrlDashboardResponse` mapping does not yet surface those stats fields in the response body.

---

### `PATCH /urls/{urlId}/toggle`

Flips a link's `isActive` flag (quick enable/disable, distinct from soft delete).

**Auth:** Authenticated (must be the owner)

**Path parameters:**

| Param | Type |
|---|---|
| `urlId` | Long |

**Response:** `200 OK` — `UrlResponse`

**Errors:** `UrlNotFoundException` (masked) if the link doesn't exist or isn't owned by the caller — anonymous links (`userId == null`) can never be toggled by anyone.

**Behavior notes:** Evicts both Redis cache keys for the short code regardless of which direction the toggle went, so the next redirect always re-checks DB state.

---

### `PATCH /urls/{urlId}`

Partial update of a link's destination and/or metadata.

**Auth:** Authenticated (must be the owner)

**Path parameters:**

| Param | Type |
|---|---|
| `urlId` | Long |

**Request body** (`UrlUpdateRequest`):

```json
{
  "longUrl": "https://example.com/new-destination",
  "title": "Updated title",
  "isActive": true,
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

| Field | Type | Constraints |
|---|---|---|
| `longUrl` | string | `@NotBlank` (required on every update call, even if unchanged) |
| `title` | string | optional |
| `isActive` | Boolean | optional |
| `expiresAt` | OffsetDateTime | optional |

**Response:** `200 OK` — `UrlResponse`

**Errors:**

| Condition | Exception |
|---|---|
| Link doesn't exist | `UrlNotFoundException` |
| Not the owner (including anonymous links) | `UrlNotFoundException` (masked to prevent ownership enumeration) |
| Link already soft-deleted | `UrlNotFoundException` |

**Behavior notes:**
- Only re-hashes and evicts cache if `longUrl` actually changed from the stored value.
- If `isActive` is explicitly set to `false`, cache is evicted so the next redirect attempt hits the DB and fails fast rather than continuing to serve a stale cached destination.

---

### `DELETE /urls/{urlId}`

Soft-deletes a link (sets `deletedAt` and `isActive = false`).

**Auth:** Authenticated (must be the owner)

**Path parameters:**

| Param | Type |
|---|---|
| `urlId` | Long |

**Response:** `204 No Content`

**Errors:** `UrlNotFoundException` (masked) if not found or not owned.

**Behavior notes:** Evicts both Redis cache keys for the short code on delete.

---

### Supporting types

```java
public record UrlResponse(
        Long urlId,
        String shortCode,
        String longUrl,
        String title,
        OffsetDateTime createdAt
) {}

public record UrlDashboardResponse(
        Long urlId,
        String shortCode,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        boolean isActive
) {}

public record RedirectResult(
        Long urlId,
        String longUrl
) {}
```

---

## 4. Click Events Endpoints

Base path: `/clicks`

Exposes raw, per-click history for a link's owner (as opposed to `/stats`, which returns aggregate counts).

### `GET /clicks/{shortCode}`

Returns a paginated list of individual click events for a short link, most recent first.

**Auth:** Authenticated (must be the owner)

**Path parameters:**

| Param | Type |
|---|---|
| `shortCode` | string |

**Query parameters:**

| Param | Type | Default |
|---|---|---|
| `page` | int | `0` |
| `size` | int | `10` |

**Response:** `200 OK` — `Page<ClickEventResponse>`

```json
{
  "content": [
    {
      "ipAddress": "sha256-hash-of-ip",
      "userAgent": "Mozilla/5.0 ...",
      "referrer": "https://twitter.com/",
      "clickedAt": "2026-07-09T10:20:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 10
}
```

**Errors:**

| Condition | Exception |
|---|---|
| Short code doesn't exist | `UrlNotFoundException` |
| Not the owner (including anonymous links) | `AccessDeniedException` |
| Link soft-deleted | `UrlNotFoundException` |

**Behavior notes:**
- `ipAddress` in the response is the SHA-256 hash produced at ingestion time — raw IPs are never persisted or returned.
- Click events are read directly from Postgres; they only appear here once `StatsAggregationService` has flushed them from the Redis event queue, so very recent clicks may have a short visibility delay.

---

### Supporting types

```java
public record ClickEventResponse(
        String ipAddress,
        String userAgent,
        String referrer,
        OffsetDateTime clickedAt
) {}
```

---

## 5. Stats Endpoints

Base path: `/stats`

Aggregate, owner-only summary counts for a link — combines a persisted rollup with a live Redis HyperLogLog read so unique-visitor counts stay fresh between flush cycles.

### `GET /stats/{shortCode}`

**Auth:** Authenticated (must be the owner)

**Path parameters:**

| Param | Type |
|---|---|
| `shortCode` | string |

**Response:** `200 OK` — `StatsResponse`

```json
{
  "shortCode": "b7F3q",
  "url": "https://example.com/some/very/long/path",
  "totalClicks": 482,
  "uniqueVisitors": 317,
  "lastUpdatedAt": "2026-07-09T09:00:00Z"
}
```

**Errors:**

| Condition | Exception |
|---|---|
| Short code doesn't exist | `UrlNotFoundException` |
| Not the owner | `AccessDeniedException` |
| Link soft-deleted | `UrlNotFoundException` |

**Behavior notes:**
- `totalClicks` comes from the persisted `UrlStats` row (updated by the hourly aggregation job); if no row exists yet, a zeroed default is used instead of 404ing.
- `uniqueVisitors` is `max(persisted value, live HyperLogLog cardinality)` — the live read covers clicks that happened after the last flush but haven't been rolled into Postgres yet, without ever double-counting or summing the two sources.

---

### Supporting types

```java
public record StatsResponse(
        String shortCode,
        String url,
        Long totalClicks,
        Long uniqueVisitors,
        OffsetDateTime lastUpdatedAt
) {}
```

---

## 6. Admin Endpoints

Base path: `/admin`

System-wide moderation surface. Every endpoint under this controller requires the `ADMIN` authority/role.

### `GET /admin/urls`

Lists **every** URL in the system, regardless of owner.

**Auth:** Admin

**Query parameters:**

| Param | Type | Default |
|---|---|---|
| `page` | int | `0` |
| `size` | int | `50` |

**Response:** `200 OK` — `Page<AdminUrlResponse>`

```json
{
  "content": [
    {
      "id": 123,
      "shortCode": "b7F3q",
      "longUrl": "https://example.com/some/very/long/path",
      "ownerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "isActive": true,
      "isDeleted": false,
      "createdAt": "2026-07-09T10:15:30Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

---

### `DELETE /admin/urls/{urlId}`

Force-takedown of any URL in the system, regardless of ownership — a moderation kill switch distinct from the owner-scoped `DELETE /urls/{urlId}`.

**Auth:** Admin

**Path parameters:**

| Param | Type |
|---|---|
| `urlId` | Long |

**Response:** `204 No Content`

**Errors:** `UrlNotFoundException` if the ID doesn't exist anywhere in the system.

**Behavior notes:** Soft-deletes (`deletedAt` + `isActive = false`) and evicts both Redis cache keys. A Redis eviction failure is caught and logged rather than failing the request — the DB takedown is treated as the source of truth even if the cache briefly serves a stale entry.

---

### Supporting types

```java
public record AdminUrlResponse(
        Long id,
        String shortCode,
        String longUrl,
        UUID ownerId,
        boolean isActive,
        boolean isDeleted,
        OffsetDateTime createdAt
) {}
```

---

## Background Jobs (not HTTP endpoints, but part of the click/stats pipeline)

These run inside `StatsAggregationService` and affect what `/clicks` and `/stats` return, so they're documented here for completeness.

### Click Aggregation Job

Runs every hour.

Responsibilities:

- Reads click events from the Redis queue.
- Persists events to PostgreSQL.
- Updates aggregated statistics.
- Updates unique visitor counts using HyperLogLog.

### Click Retention Job

Runs daily at 2 AM.

Responsibilities:

- Deletes click events older than 30 days.

---

## Data Model Summary

| Entity / Table | Key fields |
|---|---|
| `Url` | `id`, `shortCode`, `longUrl`, `longUrlHash`, `userId` (nullable), `title`, `isActive`, `createdAt`, `expiresAt`, `deletedAt` |
| `ClickEvent` | `urlId`, `ipAddress` (hashed), `userAgent`, `referrer`, `clickedAt` |
| `UrlStats` | `urlId` (PK), `totalClicks`, `uniqueVisitors`, `lastUpdatedAt` |

## Redis Key Reference

| Key pattern | Purpose | TTL |
|---|---|---|
| `url:route:{shortCode}` | Cached long URL for redirects | `url.cache-ttl-days` (default 7d) |
| `url:route:{shortCode}:id` | Cached numeric URL ID paired with the above | same as above |
| `click:unique:{urlId}` | HyperLogLog of hashed IPs, for unique visitor estimation | none (persistent, cumulative) |
| `events:url:clicks` | Redis list acting as the click-event ingestion queue | none (drained by scheduled job) |