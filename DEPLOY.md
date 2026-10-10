# Deploying ParcelShare (free tiers)

Three parts: **Neon** (database), **Render** (Spring Boot backend), **Vercel** (website). Verify each provider's current free-tier limits before relying on them.

## 1. Database: Neon
Create a project, then copy the **direct (non-pooled)** connection details. Convert them like this:

| From the Neon connection string | Becomes |
|---|---|
| `postgresql://USER:PASSWORD@HOST/DB?sslmode=require` | `DB_URL=jdbc:postgresql://HOST/DB?sslmode=require`, `DB_USER=USER`, `DB_PASSWORD=PASSWORD` |

The first backend start creates every table (Flyway migrations V1 to V5).

## 2. Backend: Render
New Web Service, repo `parcelshare`, **Language Docker**, **Root Directory `backend`**, **Free** instance.

| Variable | Value | Notes |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | from step 1 | |
| `JWT_SECRET` | 40+ random characters | **Required.** The app refuses to start on a real database without it |
| `ADMIN_PASSWORD` | your own strong password | Creates `admin@parcelshare.local`. If unset, no admin is created |
| `CORS_ORIGINS` | your Vercel address, e.g. `https://parcelshare.vercel.app` | Set after step 3. No trailing slash |
| `FRONTEND_URL` | same Vercel address | Used in email links |
| `SEED_DEMO` | **do not set** | Demo accounts and the "simulate" button must never reach production |

Check: open `https://YOUR-API.onrender.com/public/config`. You should see `{"demoMode":false}`.
The free instance sleeps after about 15 idle minutes; the first request then takes up to a minute.

## 3. Website: Vercel
Import the repo, **Root Directory `frontend`** (framework: Vite). Add `VITE_API_URL` = your Render address (no trailing slash). Deploy, then put the resulting address into Render's `CORS_ORIGINS` and `FRONTEND_URL`.

## 4. Email (optional): Brevo
Create a free account, verify a sender address, and copy the SMTP login and key from *SMTP & API*. Then add to Render:

```
SPRING_MAIL_HOST=smtp-relay.brevo.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<SMTP login>
SPRING_MAIL_PASSWORD=<SMTP key>
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
MAIL_FROM=<verified sender address>
REQUIRE_EMAIL_VERIFICATION=true   # only after you have confirmed emails arrive
```

## Other settings
| Variable | Default | Meaning |
|---|---|---|
| `RELEASE_WINDOW_HOURS` | 48 | Dispute window before a delivered booking's payment auto-releases |
| `EVIDENCE_RETENTION_DAYS` | 180 | Photos of finished bookings are deleted after this long |

## Before real users
- Have the Terms of use reviewed by a lawyer or your institution's legal cell.
- Payments are simulated. Do not take real money until a real gateway is integrated.
- Back up the database (the free tier has no automatic backups).
