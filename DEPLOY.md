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

## 5. Government identity verification: DigiLocker (optional, needs approval)
Travellers can verify instantly with DigiLocker instead of waiting for a manual admin check. The whole flow is built and tested
(consent screen, one-time state, PKCE, name matching, one identity per account). It switches on when you add credentials:

1. Register your organisation as a DigiLocker **requester** through **API Setu** (apisetu.gov.in). This is a government approval
   process that can take time and may require a registered entity. I cannot do this for you.
2. When approved you receive a client id and secret and register `https://YOUR-VERCEL-ADDRESS/identity/callback` as the redirect URI.
3. Add to Render: `DIGILOCKER_CLIENT_ID`, `DIGILOCKER_CLIENT_SECRET`.
4. The three endpoint URLs default to DigiLocker's public OAuth2 paths, but they are controlled by the government. Confirm them against the
   current API Setu documentation and override with `DIGILOCKER_AUTHORIZE_URL`, `DIGILOCKER_TOKEN_URL`, `DIGILOCKER_USER_URL` if they differ.
5. Check the real response contains the fields we read (`name`, `digilockerid`). The client rejects a response without a verified name.

What is stored: the verified **name** and DigiLocker's pseudonymous **reference id**. ParcelShare never asks for, receives or stores an
Aadhaar number, document images or a date of birth. Do not add an Aadhaar-number field: private companies are restricted by law from
doing Aadhaar authentication themselves, and holding Aadhaar numbers creates serious liability.
A **driving licence** is verified through the same DigiLocker consent screen (the user chooses what to share); it needs no separate integration.
Without credentials, demo mode shows a clearly labelled stand-in consent screen so the flow can be demonstrated.

## Other settings
| Variable | Default | Meaning |
|---|---|---|
| `RELEASE_WINDOW_HOURS` | 48 | Dispute window before a delivered booking's payment auto-releases |
| `EVIDENCE_RETENTION_DAYS` | 180 | Photos of finished bookings are deleted after this long |

## Before real users
- Have the Terms of use reviewed by a lawyer or your institution's legal cell.
- Payments are simulated. Do not take real money until a real gateway is integrated.
- Back up the database (the free tier has no automatic backups).
