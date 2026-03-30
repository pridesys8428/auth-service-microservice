# Authentication Microservice — API Documentation

**Version:** v1  
**Base URL:** `/api/v1/auth`  
**Content-Type:** `application/json`

---

## Overview

This service is a standalone authentication microservice shared across all products and platforms. It handles user registration, credential verification, OTP-based two-factor authentication, token issuance, token rotation, and password recovery. All responses follow a consistent envelope format.

### Standard Response Envelope

Every response — success or error — is wrapped in the following structure:

```json
{
  "success": true,
  "data": { },
  "error": null,
  "timestamp": "2026-03-30T10:00:00Z"
}
```

| Field | Type | Description |
|---|---|---|
| `success` | boolean | `true` for 2xx responses; `false` for all errors |
| `data` | object \| null | The response payload on success; `null` on error |
| `error` | object \| null | Error detail object on failure; `null` on success |
| `timestamp` | string (ISO 8601) | Server UTC time at response generation |

### Error Object Structure

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "The provided identifier or password is incorrect."
  },
  "timestamp": "2026-03-30T10:00:00Z"
}
```

### Authentication Header

All protected endpoints require a valid access token in the `Authorization` header:

```
Authorization: Bearer <access_token>
```

---

## Endpoints

| # | Method | Endpoint | Auth Required | Description |
|---|---|---|---|---|
| 1 | `POST` | `/register` | No | Register a new user account |
| 2 | `POST` | `/verify-otp` | No | Verify OTP for email or phone confirmation |
| 3 | `POST` | `/resend-otp` | No | Resend an OTP for a pending verification |
| 4 | `POST` | `/login` | No | Authenticate with credentials; triggers login OTP |
| 5 | `POST` | `/login/verify` | No | Complete login by verifying the login OTP |
| 6 | `POST` | `/refresh-token` | No | Exchange a refresh token for a new token pair |
| 7 | `POST` | `/forgot-password` | No | Request a password reset OTP |
| 8 | `POST` | `/reset-password` | No | Set a new password using a valid reset OTP |
| 9 | `POST` | `/logout` | Yes | Revoke the current session's tokens |
| 10 | `GET` | `/validate` | Yes | Validate an access token and return user claims |

---

## 1. Register

Creates a new user account with `PENDING` status. An OTP is dispatched to the provided email address to confirm ownership before the account is activated.

```
POST /api/v1/auth/register
```

### Request Body

```json
{
  "full_name": "string",
  "email": "string",
  "phone": "string | null",
  "password": "string",
  "role": "string"
}
```

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `full_name` | string | Yes | 2–100 characters | User's full display name |
| `email` | string | Yes | Valid RFC 5321 email | Primary identifier and OTP delivery channel |
| `phone` | string | No | E.164 format e.g. `+8801711000000` | Optional phone number for SMS OTP fallback |
| `password` | string | Yes | Min 8 chars, 1 uppercase, 1 digit, 1 special char | Plain-text password; BCrypt-hashed server-side before storage |
| `role` | string | Yes | One of: `USER`, `ADMIN`, `SUPERVISOR` | Role assigned at registration; controls access across downstream services |

### Response — `201 Created`

```json
{
  "success": true,
  "data": {
    "user_id": "string",
    "otp_reference": "string",
    "contact_hint": "string",
    "message": "string"
  },
  "error": null,
  "timestamp": "2026-03-30T10:00:00Z"
}
```

| Field | Type | Description |
|---|---|---|
| `user_id` | string (UUID) | Newly created user's unique identifier. Required for the OTP verification step. |
| `otp_reference` | string | Opaque token referencing the pending OTP in Redis. Pass this to `/verify-otp`. Valid for 5 minutes. |
| `contact_hint` | string | Masked address showing where the OTP was sent, e.g. `m***@example.com` |
| `message` | string | Human-readable confirmation, e.g. `"Account created. Please verify your email."` |

### Error Cases

| Status | `code` | Condition |
|---|---|---|
| `409` | `EMAIL_ALREADY_EXISTS` | An account with this email address already exists regardless of its current status |
| `422` | `INVALID_EMAIL_FORMAT` | The `email` field does not conform to RFC 5321 |
| `422` | `WEAK_PASSWORD` | The `password` does not meet the minimum complexity requirements |
| `422` | `INVALID_PHONE_FORMAT` | The `phone` field is provided but is not valid E.164 format |
| `422` | `INVALID_ROLE` | The `role` field is not one of the accepted values |
| `500` | `OTP_DISPATCH_FAILED` | Account was created but the OTP email could not be dispatched. Client should trigger `/resend-otp`. |

---

## 2. Verify OTP

Verifies the OTP sent during registration or any other flow that requires contact confirmation. On success, the account's `status` is transitioned from `PENDING` to `ACTIVE`.

```
POST /api/v1/auth/verify-otp
```

### Request Body

```json
{
  "user_id": "string",
  "otp_reference": "string",
  "otp": "string",
  "purpose": "string"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `user_id` | string (UUID) | Yes | The user ID returned from the initiating endpoint (register, forgot-password, etc.) |
| `otp_reference` | string | Yes | The `otp_reference` value returned from the initiating endpoint. Scopes the OTP lookup and prevents cross-flow reuse. |
| `otp` | string | Yes | The 6-digit code received by the user |
| `purpose` | string | Yes | Declares the intent of verification. One of: `REGISTRATION`, `PASSWORD_RESET` |

### Response — `200 OK`

```json
{
  "success": true,
  "data": {
    "message": "string"
  },
  "error": null,
  "timestamp": "2026-03-30T10:00:00Z"
}
```

| Field | Type | Description |
|---|---|---|
| `message` | string | Human-readable confirmation, e.g. `"Email verified. Your account is now active."` |

### Error Cases

| Status | `code` | Condition |
|---|---|---|
| `400` | `INVALID_OTP` | The submitted OTP does not match the stored value for the given `otp_reference` |
| `400` | `OTP_EXPIRED` | The OTP has exceeded its 5-minute TTL and has been removed from the store |
| `400` | `OTP_ALREADY_USED` | The OTP has already been consumed by a prior successful call |
| `400` | `PURPOSE_MISMATCH` | The `purpose` field does not match the purpose under which the OTP was issued |
| `404` | `USER_NOT_FOUND` | No user exists for the provided `user_id` |
| `410` | `OTP_REFERENCE_INVALID` | The `otp_reference` is not found — either never issued or already expired |

---

## 3. Resend OTP

Re-dispatches an OTP when the original was not received or has expired. Invalidates any previously issued OTP for the same user and purpose before issuing a new one. Subject to rate limiting: maximum **3 resend attempts per 15 minutes per user**.

```
POST /api/v1/auth/resend-otp
```

### Request Body

```json
{
  "user_id": "string",
  "purpose": "string"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `user_id` | string (UUID) | Yes | The user ID for whom the OTP should be resent |
| `purpose` | string | Yes | Must match the original purpose. One of: `REGISTRATION`, `PASSWORD_RESET` |

### Response — `200 OK`

```json
{
  "success": true,
  "data": {
    "otp_reference": "string",
    "contact_hint": "string",
    "message": "string"
  },
  "error": null,
  "timestamp": "2026-03-30T10:00:00Z"
}
```

| Field | Type | Description |
|---|---|---|
| `otp_reference` | string | New opaque reference for the freshly issued OTP. The previous `otp_reference` is immediately invalidated. |
| `contact_hint` | string | Masked address confirming where the new OTP was sent |
| `message` | string | Human-readable confirmation, e.g. `"A new OTP has been sent to m***@example.com"` |

### Error Cases

| Status | `code` | Condition |
|---|---|---|
| `404` | `USER_NOT_FOUND` | No account exists for the provided `user_id` |
| `409` | `ACCOUNT_ALREADY_ACTIVE` | The account's status is already `ACTIVE`; OTP verification is no longer required |
| `422` | `INVALID_PURPOSE` | The `purpose` value is not one of the accepted options |
| `429` | `RESEND_LIMIT_EXCEEDED` | 3 or more resend attempts have been made within the last 15 minutes. Includes a `Retry-After` header. |
| `500` | `OTP_DISPATCH_FAILED` | New OTP was generated but the delivery attempt to the user's contact failed |

---

## 4. Login

Authenticates a user with their credentials. On success, the server dispatches a login OTP to the user's registered contact for the second factor. The actual access and refresh tokens are **not** returned from this endpoint — they are issued only after the OTP is verified in `/login/verify`.

```
POST /api/v1/auth/login
```

### Request Body

```json
{
  "email": "string",
  "password": "string",
  "device_id": "string | null",
  "device_info": {
    "platform": "string | null",
    "os_version": "string | null",
    "app_version": "string | null"
  }
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string | Yes | The registered email address of the user |
| `password` | string | Yes | The account password |
| `device_id` | string | No | Unique device identifier used for device binding. Required for roles where device tracking is enforced (e.g. `SUPERVISOR`, `USER`). |
| `device_info` | object | No | Optional metadata about the client device, recorded in the audit log |
| `device_info.platform` | string | No | e.g. `android`, `ios`, `web` |
| `device_info.os_version` | string | No | e.g. `Android 14`, `iOS 17.4` |
| `device_info.app_version` | string | No | e.g. `2.4.1` |

### Response — `200 OK`

```json
{
  "success": true,
  "data": {
    "user_id": "string",
    "otp_reference": "string",
    "contact_hint": "string",
    "message": "string"
  },
  "error": null,
  "timestamp": "2026-03-30T10:00:00Z"
}
```

| Field | Type | Description |
|---|---|---|
| `user_id` | string (UUID) | The authenticated user's ID. Required for the `/login/verify` call. |
| `otp_reference` | string | Opaque token scoping the login OTP. Valid for 5 minutes. Pass to `/login/verify`. |
| `contact_hint` | string | Masked contact showing where the OTP was dispatched, e.g. `m***@example.com` or `+88017****5432` |
| `message` | string | Human-readable status, e.g. `"OTP sent to your registered email. Valid for 5 minutes."` |

### Error Cases

| Status | `code` | Condition |
|---|---|---|
| `401` | `INVALID_CREDENTIALS` | Email or password does not match any active account |
| `403` | `ACCOUNT_INACTIVE` | Account status is `PENDING` — registration OTP has not been verified |
| `403` | `ACCOUNT_SUSPENDED` | Account has been administratively suspended |
| `403` | `ACCOUNT_LOCKED` | Account is temporarily locked due to 5 or more consecutive failed login attempts. Locked for 30 minutes. |
| `429` | `LOGIN_RATE_LIMIT_EXCEEDED` | More than 10 login attempts in 1 minute from this IP address. Includes a `Retry-After` header. |

---

## 5. Login Verify (Complete Login)

Completes the two-factor login flow by verifying the OTP issued in step 4. On success, the server returns a short-lived access token and a long-lived refresh token.

```
POST /api/v1/auth/login/verify
```

### Request Body

```json
{
  "user_id": "string",
  "otp_reference": "string",
  "otp": "string"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `user_id` | string (UUID) | Yes | User ID returned from `/login` |
| `otp_reference` | string | Yes | The `otp_reference` returned from `/login` |
| `otp` | string | Yes | The 6-digit code received by the user |

### Response — `200 OK`

```json
{
  "success": true,
  "data": {
    "access_token": "string",
    "refresh_token": "string",
    "token_type": "Bearer",
    "expires_in": 900,
    "user": {
      "user_id": "string",
      "full_name": "string",
      "email": "string",
      "role": "string",
      "status": "string"
    }
  },
  "error": null,
  "timestamp": "2026-03-30T10:00:00Z"
}
```

| Field | Type | Description |
|---|---|---|
| `access_token` | string (JWT) | Short-lived JWT for authenticating requests. Signed with the server's access token secret. Contains: `user_id`, `email`, `role`, `iat`, `exp`. **TTL: 15 minutes.** |
| `refresh_token` | string (opaque UUID) | Long-lived opaque token for obtaining new access tokens. Must be stored securely by the client. **TTL: 30 days.** |
| `token_type` | string | Always `"Bearer"` |
| `expires_in` | integer | Access token lifetime in seconds from issuance (`900` = 15 minutes) |
| `user.user_id` | string (UUID) | Unique identifier of the authenticated user |
| `user.full_name` | string | User's registered display name |
| `user.email` | string | User's registered email address |
| `user.role` | string | Assigned role, e.g. `USER`, `ADMIN`, `SUPERVISOR` |
| `user.status` | string | Account status at time of login, e.g. `ACTIVE` |

### Error Cases

| Status | `code` | Condition |
|---|---|---|
| `400` | `INVALID_OTP` | OTP does not match the value stored for the given `otp_reference` |
| `400` | `OTP_EXPIRED` | OTP TTL has elapsed (5 minutes) |
| `400` | `OTP_ALREADY_USED` | OTP was already consumed by a prior call |
| `404` | `USER_NOT_FOUND` | No user exists for the provided `user_id` |
| `410` | `OTP_REFERENCE_INVALID` | `otp_reference` is not found or has expired |

---

## 6. Refresh Token

Exchanges a valid, non-revoked refresh token for a new access token and a new refresh token. The submitted refresh token is **immediately revoked** upon receipt and replaced with the newly issued pair. This enforces single-use token rotation — if a previously used refresh token is replayed, all active sessions for the user are revoked as a security measure.

```
POST /api/v1/auth/refresh-token
```

### Request Body

```json
{
  "refresh_token": "string"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `refresh_token` | string | Yes | The opaque refresh token previously issued by `/login/verify` or a prior `/refresh-token` call |

### Response — `200 OK`

```json
{
  "success": true,
  "data": {
    "access_token": "string",
    "refresh_token": "string",
    "token_type": "Bearer",
    "expires_in": 900
  },
  "error": null,
  "timestamp": "2026-03-30T10:00:00Z"
}
```

| Field | Type | Description |
|---|---|---|
| `access_token` | string (JWT) | Newly issued access token. The previous access token remains valid until its own natural expiry. **TTL: 15 minutes.** |
| `refresh_token` | string (opaque UUID) | Newly issued refresh token. The previously submitted token is now revoked and must not be reused. **TTL: 30 days from issuance.** |
| `token_type` | string | Always `"Bearer"` |
| `expires_in` | integer | Access token lifetime in seconds (`900`) |

### Error Cases

| Status | `code` | Condition |
|---|---|---|
| `401` | `REFRESH_TOKEN_INVALID` | The token does not exist in the database — never issued or already deleted |
| `401` | `REFRESH_TOKEN_EXPIRED` | The token's TTL has elapsed (30 days) |
| `401` | `REFRESH_TOKEN_REVOKED` | The token has been explicitly revoked (logout) or was already rotated |
| `401` | `REFRESH_TOKEN_REUSE_DETECTED` | A previously rotated token was submitted. All sessions for this user have been revoked as a precaution. User must log in again. |

---

## 7. Forgot Password

Initiates the password recovery flow. Looks up the account by email and dispatches a password-reset OTP to the registered contact. This endpoint **does not reveal** whether an account exists for a given email — the response is identical whether or not the email is found, to prevent account enumeration.

```
POST /api/v1/auth/forgot-password
```

### Request Body

```json
{
  "email": "string"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string | Yes | The email address associated with the account to recover |

### Response — `200 OK`

The response is identical regardless of whether the email corresponds to an existing account.

```json
{
  "success": true,
  "data": {
    "user_id": "string | null",
    "otp_reference": "string | null",
    "contact_hint": "string | null",
    "message": "string"
  },
  "error": null,
  "timestamp": "2026-03-30T10:00:00Z"
}
```

| Field | Type | Description |
|---|---|---|
| `user_id` | string (UUID) \| null | Present only if the account was found. Required for the `/reset-password` call. `null` if no account matches. |
| `otp_reference` | string \| null | Opaque OTP reference. Present only if account found and OTP dispatched. `null` otherwise. |
| `contact_hint` | string \| null | Masked contact if account found, e.g. `m***@example.com`. `null` if no account. |
| `message` | string | Always `"If an account is registered with this email, a reset code has been sent."` |

### Error Cases

| Status | `code` | Condition |
|---|---|---|
| `422` | `INVALID_EMAIL_FORMAT` | The supplied value is not a valid email address |
| `403` | `ACCOUNT_SUSPENDED` | Account exists but is suspended. Password reset is blocked. Support contact required. |
| `429` | `RESET_RATE_LIMIT_EXCEEDED` | More than 3 forgot-password requests in 15 minutes for this email. Includes a `Retry-After` header. |

---

## 8. Reset Password

Completes the password recovery flow. Verifies the OTP issued in `/forgot-password` and updates the user's password. On success, all existing refresh tokens for the account are revoked, forcing re-authentication on all active sessions.

```
POST /api/v1/auth/reset-password
```

### Request Body

```json
{
  "user_id": "string",
  "otp_reference": "string",
  "otp": "string",
  "new_password": "string",
  "confirm_password": "string"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `user_id` | string (UUID) | Yes | User ID returned from `/forgot-password` |
| `otp_reference` | string | Yes | `otp_reference` returned from `/forgot-password` |
| `otp` | string | Yes | The 6-digit code received by the user |
| `new_password` | string | Yes | The desired new password. Must meet complexity requirements: min 8 chars, 1 uppercase, 1 digit, 1 special character. |
| `confirm_password` | string | Yes | Must exactly match `new_password`. Validated server-side as a safeguard. |

### Response — `200 OK`

```json
{
  "success": true,
  "data": {
    "message": "string"
  },
  "error": null,
  "timestamp": "2026-03-30T10:00:00Z"
}
```

| Field | Type | Description |
|---|---|---|
| `message` | string | Human-readable confirmation, e.g. `"Password updated successfully. Please log in with your new credentials."` |

### Error Cases

| Status | `code` | Condition |
|---|---|---|
| `400` | `INVALID_OTP` | Submitted OTP does not match the stored value |
| `400` | `OTP_EXPIRED` | OTP TTL has elapsed |
| `400` | `OTP_ALREADY_USED` | OTP has already been consumed |
| `400` | `PASSWORDS_DO_NOT_MATCH` | `new_password` and `confirm_password` values differ |
| `400` | `WEAK_PASSWORD` | `new_password` does not meet complexity requirements |
| `400` | `PASSWORD_SAME_AS_CURRENT` | The new password is identical to the existing password |
| `404` | `USER_NOT_FOUND` | No user exists for the provided `user_id` |
| `410` | `OTP_REFERENCE_INVALID` | `otp_reference` is not found or has already expired |

---

## 9. Logout

Revokes the current session by invalidating all active refresh tokens associated with the authenticated user. The access token itself is short-lived and will expire naturally — it is not blacklisted, as doing so would require a distributed blocklist lookup on every request. Clients must discard both the access token and refresh token on their side after calling this endpoint.

```
POST /api/v1/auth/logout
```

**Authorization required:** `Bearer <access_token>`

### Request Body

No request body is required. The authenticated user is identified from the `Authorization` header.

```json
{}
```

### Optional: Targeted Device Logout

To revoke only the token for a specific device (rather than all sessions), include the `device_id`:

```json
{
  "device_id": "string | null"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `device_id` | string | No | If provided, only the refresh token associated with this device is revoked. If omitted, all refresh tokens for the user are revoked (full logout from all devices). |

### Response — `204 No Content`

No response body. The client should delete all locally stored tokens.

### Error Cases

| Status | `code` | Condition |
|---|---|---|
| `401` | `TOKEN_MISSING` | No `Authorization` header was provided |
| `401` | `TOKEN_INVALID` | The access token is malformed or has an invalid signature |
| `401` | `TOKEN_EXPIRED` | The access token's TTL has elapsed. The session can be considered already invalid. |

---

## 10. Validate Token

Validates an access token and returns the embedded user claims. Intended for use by **downstream microservices** that need to verify a token without importing JWT libraries or managing signing secrets. The service validates the signature, expiry, and issuer claims server-side.

```
GET /api/v1/auth/validate
```

**Authorization required:** `Bearer <access_token>`

### Request Body

No request body.

### Response — `200 OK`

```json
{
  "success": true,
  "data": {
    "user_id": "string",
    "email": "string",
    "role": "string",
    "status": "string",
    "issued_at": "string",
    "expires_at": "string"
  },
  "error": null,
  "timestamp": "2026-03-30T10:00:00Z"
}
```

| Field | Type | Description |
|---|---|---|
| `user_id` | string (UUID) | Unique identifier of the token owner |
| `email` | string | Registered email of the token owner |
| `role` | string | Role encoded in the token at issuance, e.g. `USER`, `ADMIN`, `SUPERVISOR` |
| `status` | string | Account status at time of token issuance, e.g. `ACTIVE` |
| `issued_at` | string (ISO 8601) | UTC timestamp of when this token was issued |
| `expires_at` | string (ISO 8601) | UTC timestamp of when this token expires |

### Error Cases

| Status | `code` | Condition |
|---|---|---|
| `401` | `TOKEN_MISSING` | No `Authorization` header provided |
| `401` | `TOKEN_INVALID` | Token is malformed, signature does not verify, or issuer claim is unexpected |
| `401` | `TOKEN_EXPIRED` | Token has passed its expiry time |

---

## Token Specification

### Access Token (JWT)

| Property | Value |
|---|---|
| Format | Signed JWT (JWS) — Compact Serialization |
| Algorithm | `HS256` (HMAC-SHA256) |
| TTL | 15 minutes |
| Delivery | Response body only. Never in cookies unless explicitly configured per deployment. |

**Payload claims:**

| Claim | Type | Description |
|---|---|---|
| `sub` | string | User ID (UUID) |
| `email` | string | User's email address |
| `role` | string | User's assigned role |
| `iat` | integer | Issued At (Unix epoch seconds) |
| `exp` | integer | Expiry (Unix epoch seconds) |
| `iss` | string | Issuer — always `auth-service` |

### Refresh Token (Opaque)

| Property | Value |
|---|---|
| Format | Opaque UUID (v4) string |
| Storage | SHA-256 hash stored in `refresh_tokens` table. Raw token returned to client only. |
| TTL | 30 days |
| Rotation | Single-use. Rotated on every call to `/refresh-token`. Replay of a used token triggers full session revocation. |
| Scope | One token per device per user. Each login with a `device_id` replaces the previous token for that device. |

---

## OTP Specification

| Property | Value |
|---|---|
| Format | 6-digit numeric string, e.g. `"492031"` |
| Generator | `SecureRandom` (cryptographically secure) — no sequential or predictable values |
| Storage | Redis key with automatic TTL expiry. Format: `otp:{PURPOSE}:{user_id}` |
| TTL | 5 minutes |
| Use | Single-use. Deleted from Redis immediately on first successful verification. |
| Max resends | 3 per 15-minute window per user per purpose |
| Purposes | `REGISTRATION`, `PASSWORD_RESET`, `LOGIN` |

---

## Rate Limiting

All endpoints are subject to IP-based rate limiting enforced via Bucket4j with a Redis-backed distributed token bucket. Responses that exceed a rate limit return `429 Too Many Requests` with a `Retry-After` header indicating the wait time in seconds.

| Endpoint | Limit |
|---|---|
| `POST /login` | 10 requests / 1 minute / IP |
| `POST /login/verify` | 5 requests / 5 minutes / user |
| `POST /register` | 5 requests / 1 hour / IP |
| `POST /forgot-password` | 3 requests / 15 minutes / email |
| `POST /resend-otp` | 3 requests / 15 minutes / user |
| `POST /refresh-token` | 20 requests / 1 minute / user |
| `GET /validate` | 200 requests / 1 minute / IP |

---

## Security Headers

All responses include the following security headers:

| Header | Value |
|---|---|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` |
| `Cache-Control` | `no-store` |

---

## Audit Events

Every authentication action is written to the `audit_logs` table. Downstream services can query audit history via the internal admin API (not documented here).

| Event | Trigger |
|---|---|
| `REGISTER_SUCCESS` | New account created successfully |
| `OTP_VERIFIED` | OTP validated; account activated |
| `OTP_FAILED` | Incorrect OTP submitted |
| `LOGIN_SUCCESS` | Credentials verified; login OTP dispatched |
| `LOGIN_FAILED` | Invalid credentials submitted |
| `LOGIN_COMPLETE` | Login OTP verified; tokens issued |
| `TOKEN_REFRESHED` | New token pair issued |
| `TOKEN_REUSE_DETECTED` | Rotated refresh token replayed; all sessions revoked |
| `PASSWORD_RESET_REQUESTED` | Forgot-password OTP dispatched |
| `PASSWORD_RESET_SUCCESS` | New password saved; all sessions revoked |
| `LOGOUT` | Session tokens revoked |
| `ACCOUNT_LOCKED` | 5 consecutive failed login attempts reached |
