# ParcelShare

A peer-to-peer parcel-sharing marketplace: people who need a small parcel moved between cities are matched with
travellers (bus / train / car) who are already making that journey and have spare bag space.

The platform is **only** a trust, matching, payment-state and coordination layer. It never touches a parcel.

## Tech stack (all free)

| Layer | Choice |
|---|---|
| Backend | Java 21 (runs on 21+), Spring Boot 3.5, Spring Security (JWT), Spring Data JPA, Flyway, Lombok |
| Database | H2 file DB for local dev (zero install, PostgreSQL mode). PostgreSQL for deployment (Neon / Supabase free tier) |
| Frontend | React 19 + TypeScript + Vite, Tailwind CSS 4, React Router, TanStack Query, `qrcode.react` |
| Payments | `PaymentGateway` interface with a mock implementation (no real money). Razorpay can be added as a second implementation |
| Tests | JUnit 5, full-stack HTTP tests against in-memory H2 |

## Run it locally

Needs: JDK 21+ and Node 20+. No Maven or database install required (Maven wrapper is included).

```bash
# 1. Backend (http://localhost:8090). SEED_DEMO adds demo accounts + trips.
cd backend
SEED_DEMO=true ./mvnw spring-boot:run          # Windows PowerShell: $env:SEED_DEMO='true'; .\mvnw.cmd spring-boot:run

# 2. Frontend (http://localhost:5180)
cd frontend
npm install
npm run dev
```

Demo logins (only when `SEED_DEMO=true`, password `demo12345`):

| Account | Role |
|---|---|
| `ananya@demo.local` | Sender |
| `rahul@demo.local`, `priya@demo.local` | Verified travellers (with trips) |
| `admin@parcelshare.local` / `ChangeMe123!` | Admin (dev default, set `ADMIN_PASSWORD` to change) |

Run the tests: `cd backend && ./mvnw test` (uses an in-memory H2 database).

To run the same tests against real PostgreSQL (verified passing), start a throwaway database and point the tests at it:

```bash
docker run -d --name pg-test -e POSTGRES_PASSWORD=testpw -e POSTGRES_DB=parcelshare -p 55432:5432 postgres:16-alpine
TEST_DB_URL=jdbc:postgresql://localhost:55432/parcelshare TEST_DB_USER=postgres TEST_DB_PASSWORD=testpw ./mvnw test
docker rm -f pg-test
```

### Demo mode

`SEED_DEMO=true` also turns on demo mode (or set `DEMO_MODE=true` on its own). On a booking page you get a
**"Simulate next step"** button that plays whichever party needs to act next (accept, handover, journey, arrival,
delivery, payment release). It calls the same service methods as the real endpoints, so the codes, rules and
tamper-evident event log are real. It makes a one-person live demo possible. The endpoint does not exist when demo
mode is off, and it must never be enabled in production.

## What is built

- Accounts, JWT auth, suspension that takes effect immediately, in-memory rate limiting on `/auth` and `/public`
- Trips (with an atomic, concurrency-safe capacity reservation) and parcels (with a category / weight / value / keyword policy engine)
- Rule-based matching: hard SQL constraints, then a 0-100 score with a visible breakdown
- Booking state machine (`REQUESTED -> ACCEPTED -> HANDED_OVER -> IN_TRANSIT -> DELIVERED`, plus cancel / reject / failed delivery / dispute)
- Digital handover (single-use 6-digit code + QR, 15-minute expiry, 5 attempts, traveller must attest they inspected the contents)
- Digital delivery (recipient code, derived by HMAC so nothing secret is stored; recipient needs no account)
- **Hash-chained, append-only event log** per booking with a "Verify" button that detects tampering
- Payment state machine + double-entry ledger (`HELD -> RELEASED | REFUNDED | PARTIALLY_RELEASED`, dispute hold), configurable commission
- Ratings with Bayesian smoothing, reliability score, disputes with admin resolution (release / refund / partial)
- Admin: verification queue, restricted-parcel review, disputes with evidence timeline, users, bookings, audit log
- Scheduled jobs: auto-release after the dispute window, expire unanswered requests, expire old listings
- **Photo evidence**: contents / delivery / damage photos, re-encoded on the server (strips EXIF/GPS), stored in the
  database, and each photo's SHA-256 is written into the event chain. "Verify photos" detects a swapped image
- **Email notifications** for every booking milestone plus the signup verification link (`/verify`). Any SMTP provider works
- **Camera QR scanning** on the handover screen (the code can still be typed)
- **Admin policy editor**: add / edit / disable parcel categories and blocked words live; every change is audit-logged
- Public **Terms** and **What you can send** pages (the latter reads the live category rules)
- **Traveller offers**: a traveller can offer to carry a matching parcel; the sender is notified and still chooses and pays
- **In-app notifications** (the bell, refreshed every 15 s) alongside the emails
- **Trip cancellation** with automatic refunds, **forgot / reset password**, change password, profile editing, and the reviews people left you
- **Premium UI**: dark glass design system, scroll-driven 3D landing page (Three.js: a parcel flying through a city network, a QR
  handover gate, a hash chain and an arrival burst), 3D parcel on the login pages, tilt cards, glowing booking progress track,
  responsive down to phones. All 3D is generated in code (no model or texture downloads) and loads only on the screens that use it
- **Identity verification** with DigiLocker (activates when API Setu credentials are added; a clearly labelled demo version works now). Stores only a verified name and reference id, never an Aadhaar number. See DEPLOY.md
- **Smooth navigation**: every screen is its own lazily loaded chunk, warmed in the background when the browser is idle; page, toast and menu transitions use Motion
- **Motion preference**: "Calm" (default when the OS asks for reduced motion: the story still follows your scroll, ambient spinning is turned
  right down) or "Full". A toggle at the bottom-left of every page switches it and remembers the choice

## Email setup (optional, free)

Without any setup, emails are only printed in the backend log. To send real mail, create a free account with an SMTP
provider (for example Brevo, 300 emails/day free) and set these environment variables on the backend:

```
SPRING_MAIL_HOST=smtp-relay.brevo.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<your SMTP login>
SPRING_MAIL_PASSWORD=<your SMTP key>
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
MAIL_FROM=<a sender address you have verified with the provider>
FRONTEND_URL=https://your-frontend-url
```

Also set `REQUIRE_EMAIL_VERIFICATION=true` once email works, so new accounts must confirm their address before they can
post trips or parcels.

## Not built yet (next steps)

Phone OTP, in-app chat, real Razorpay payments, PWA/push notifications, account deletion / data export, an admin view of all
photos across bookings, automatic photo storage clean-up for old bookings (photos live in the database, so keep an eye on the free
tier's size). Password changes do not yet sign out other devices (tokens are stateless and expire after 12 hours).

## Neon database (already linked)

This folder is linked to a Neon project (`.neon`, connection details in the git-ignored `.env.local`). Neon's
connection string is `postgresql://user:password@host/db`; this app wants it split into `DB_URL`
(`jdbc:postgresql://host/db?sslmode=require`), `DB_USER` and `DB_PASSWORD`. The first start applies the migrations.
Use the **unpooled** connection string (Flyway takes database locks). The very first start is slow (about a minute)
because of the network round trips. Neon's `neon deploy` / `neon.ts` features (serverless functions) are not used.

## Free deployment path (verify current free-tier limits before relying on them)

1. Database: create a free PostgreSQL on Neon or Supabase. Set `DB_URL=jdbc:postgresql://HOST/DB`, `DB_USER`, `DB_PASSWORD`.
2. Backend: Oracle Cloud Always Free VM (best resources), or Render / Koyeb free web service (sleeps when idle).
   Set `JWT_SECRET` (32+ random characters), `ADMIN_PASSWORD`, `CORS_ORIGINS=https://your-frontend-url`, `RELEASE_WINDOW_HOURS`.
   Do **not** set `SEED_DEMO`.
3. Frontend: `VITE_API_URL=https://your-backend-url npm run build`, then host `frontend/dist` on Vercel, Netlify or Cloudflare Pages.

## Safety notes

- Flights are deliberately excluded (airlines and security prohibit carrying items for strangers).
- The platform does not insure goods. Keep declared-value caps low and the disclaimer visible.
- ID checks are manual in the MVP; do not store ID images. If you collect personal data, plan for India's DPDP Act (consent, purpose limitation, deletion).
- Get a lawyer or your institution's legal cell to review the Terms of Service before any public launch.
