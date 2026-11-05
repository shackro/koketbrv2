# Kokwet Festival Platform

A mobile-first Kokwet Festival experience built with React, Tailwind CSS, and FastAPI.

## Deploying with Vercel and Render

The repository includes a Vercel static-site configuration at `vercel.json` and a Render Blueprint at `render.yaml`.

### 1. Deploy the API on Render

1. Push this repository to a private or public GitHub repository, then create a Render Blueprint from it and review the `kokwet-api` service before applying it. Automatic deploys are disabled in the Blueprint so changes can be reviewed and manually deployed.
2. Set `DATABASE_URL` to the Render PostgreSQL internal connection URL. The Blueprint also sets `APP_ENV=production` and `KOKWET_REQUIRE_DATABASE_URL=true`; the backend refuses to start without PostgreSQL and initializes its dedicated `kokwet_app` schema. Never expose the database URL in Vercel or frontend code.
3. If you have an existing SQLite database to preserve, stop writes and follow the one-time SQLite-to-PostgreSQL copy in [POSTGRESQL.md](./backend/POSTGRESQL.md). The migration requires an empty destination schema and verifies copied rows before committing. Supabase data is not copied automatically; plan and validate that migration separately if those records are still needed.
4. Configure `TICKET_ENCRYPTION_KEY` and `KOKWET_STAFF_ACTIVATION_SECRET` only in Render's environment. The ticket key must match the key used to encrypt any ticket records being migrated. Do not restore key files or database files from old repository snapshots; if they were exposed, rotate affected credentials and re-encrypt retained ticket data before cutover.
5. Set `KOKWET_CORS_ORIGINS` to the exact comma-separated HTTPS origins used by the Vercel site and any custom domain, with no trailing slash (for example, `https://kokwet.example,https://www.kokwet.example`).
6. Set `KOKWET_PUBLIC_SITE_URL` to the canonical Vercel/custom-domain HTTPS origin. Set strong, random, persistent values for `CULTURE_IP_HASH_SECRET` and `CULTURE_WEBHOOK_SECRET`. Keep all secrets only in Render's environment settings.
7. The Blueprint starts in `MPESA_ENV=sandbox`. Add your sandbox consumer key, consumer secret, shortcode, and passkey in Render's environment settings. Set `MPESA_CALLBACK_URL` to the public Render API origin ending in `/api/v1/culture/webhook/extend-lifespan`. The ticket callback path is derived by the backend; `/api/v1/tickets/callback` is not this app's route. Confirm the HTTPS callback is externally reachable before testing Daraja. Keep sandbox enabled until test transactions and callback delivery have been confirmed.
8. To enable ticket emails, configure the `KOKWET_SMTP_*` settings in Render. Email links use `KOKWET_PUBLIC_SITE_URL`.
9. Bootstrap the Super Admin once after the database migration using Render's service shell and `python create_superadmin.py`; do not commit an admin password or store it in Vercel.

The Render start command is `uvicorn main:app --host 0.0.0.0 --port $PORT --proxy-headers`; Render supplies `$PORT`, so do not hardcode a port or use `--reload`. The production backend uses the configured PostgreSQL database and does not require a Render persistent disk.

Render's health check is `GET /api/health` and now verifies database connectivity. After migration and deploy, confirm it passes and check the service logs for startup errors. Test public reads, staff login, media persistence, sandbox STK push, and Daraja callback before changing `MPESA_ENV` to production.

### 2. Deploy the frontend on Vercel

1. Import the same repository into Vercel and leave the project Root Directory at the repository root. `vercel.json` installs and builds the Vite app from `frontend/`, publishes `frontend/dist`, and rewrites page routes to `index.html` for React Router deep links.
2. Set the Vercel environment variable `VITE_API_URL` to the Render API origin with `/api` appended, for example `https://kokwet-api.onrender.com/api`. Do not put backend secrets in Vercel; variables prefixed with `VITE_` are included in browser code.
3. Deploy a Preview first. Verify direct navigation and refresh on `/marketplace`, `/culture`, `/tickets/{order_id}`, `/login`, and `/dashboard`, then set the canonical custom domain and add that exact origin to Render's `KOKWET_CORS_ORIGINS`.
4. Keep `VITE_ANDROID_COMPANION_STORE_URL` unset until the companion has a verified public store listing.

The Vercel and Render configuration can be prepared from this repository, but provider accounts, domain/DNS ownership, production credentials, and final payment/email delivery still need to be configured and verified in those providers. Never copy `backend/.env` into Vercel or commit it.

## Open Hearth, stories and staff publishing

The `/culture` page provides an anonymous community feed for text and voice notes. Posts are limited to 1,000 characters, 2 MB of audio, and three posts per network address per 24 hours. Posts expire after 24 hours unless a successful KSh 30 M-Pesa callback preserves them for seven days. Expired records are hidden immediately and purged periodically.

Visitors may opt into the featured-voice review when they post. The first 100 nominations each Nairobi calendar day are accepted, with one nomination per network address; the existing three-post daily limit still applies. The submitter chooses a house, may add a display name and dialect, and must agree that their post/audio can be shown if selected. Moderators select one Voice of the Day and exactly five Voices of the Week for each house. Weekly selections are preserved for seven days. Only selected voices appear in the featured areas.

The Open Hearth also contains the **Live Lounge**: an always-available public chat with text, topic tags and validated voice notes, plus a separate topic chat when a staff member starts an external TikTok or Instagram stream. Messages refresh every few seconds; voice notes are limited to 2 MB and two minutes and stored in the configured application database. The room offers watch-first and chat-first layouts, and opens the official stream in a new tab because Kokwet does not host or embed live video. The homepage Stories strip links directly to the public lounge; it does not duplicate the Voice of the Day feature. The Open Hearth page has quick links to post, browse community voices, and join the lounge. The legacy `/live` URL redirects to the embedded lounge. Admins and moderators manage Open Hearth post/chat moderation, while staff with the delegated live permission can start and end topics in the same **Open Hearth control** panel.

Each house can have up to three staff-curated voice-note slots. Audio is primary; a short caption and cover image are optional, and the staff editor supports direct microphone recording or audio upload, preview, replacement, and removal. A cover may be uploaded or supplied as an HTTPS URL (one source per note). Text-only notes remain supported. Compact circles before History & Heritage open a reading/listening window, and visitors can mark notes as read in that browser. Notes expire 48 hours after their latest publish and can be changed up to five times per slot in any rolling 48-hour period. Publish/update/removal events record the actor and time. Admins can manage all houses; staff can manage only houses assigned to their account in **House voice stories**. The separately curated **Voices of the Week** section appears above **Meet the Nine Houses**.

Published festival voice notes appear as tap-to-open social-style circles in the narrow strip between the homepage navigation and festival hero. New notes require a cover image and voice recording; a short caption is optional. Staff with story-publishing permission can record directly in the browser or upload audio, preview/replace/remove recordings, use either an uploaded cover or HTTPS image URL, and control publishing/order from `/dashboard`. Visitors can also record or upload an audio note in the Open Hearth composer.

The admin **Site content** editor supports both HTTPS URLs and direct uploads for editable logos, homepage images, shield images, artist/enterprise photos, and hero videos. Uploads are stored in the configured application database with generated IDs, strict file-type checks, and limits of 5 MB per image, 30 MB per video, and 2 MB per audio file.

## Android floating chat companion

The Android companion in `mobile/android` uses Android's explicit **Display over other apps** permission to keep the Kokwet live-chat WebView above TikTok and other apps. Visitors can open the Android recommendation from the embedded live chat; the site never attempts to detect the foreground app. The Android app requires an HTTPS site address, and users start/stop the overlay themselves.

Open `mobile/android` in Android Studio or build a local debug APK with `cd mobile/android && ./gradlew assembleDebug`. Install `app/build/outputs/apk/debug/app-debug.apk` on an Android 8.0+ device. This debug APK is for development/sideload testing only; it is not signed for Play Store release. Before public distribution, create a protected release signing key, test the signed release on supported Android devices, complete Google Play's foreground-service and overlay declarations, and publish the app. Set frontend build variable `VITE_ANDROID_COMPANION_STORE_URL` to the verified Google Play listing URL only after that release is live; until then the site explicitly says the app is not published.

- **Admin:** manage staff access and all stories, moderate the Open Hearth, and curate voices.
- **Moderator:** manage stories, moderate community posts, and curate voices.
- **Creator:** publish and manage their own festival stories.

New homepage stories require a cover image and voice recording. Staff may upload JPEG, PNG or WebP covers up to 5 MB, or provide an HTTPS image URL; recordings accept MP3, M4A, OGG, WAV or WebM up to 2 MB.

## Impact page and reporting

The public `/impact` page loads its introduction, contribution pathways, resource-reporting state, and goals from `GET /api/impact`. Admins and moderators can edit this content in the **Impact page** tab at `/staff`; creator accounts cannot access the editor. Changes are stored in the `impact_content` table in the configured application database.

Resource reporting has three states: unpublished, proposed, or verified. Proposed figures are explicitly labeled as proposals, not actual expenditure. A verified report must include a reporting period and source, and its allocation percentages must total 100. An optional supporting link must use HTTP or HTTPS. Do not publish financial figures unless the team has supporting evidence.

## Roadmap page

The public `/roadmap` page loads its copy, ordered phases, milestones, public status labels, and feedback panel text from `GET /api/roadmap`. Admins can update all roadmap page content in the **Roadmap** tab at `/staff`; moderators and creators cannot edit it. Content is stored in the `roadmap_content` table in the configured application database. Roadmap phases display status rather than unsupported completion percentages; only mark a phase complete when the team has confirmed that status.

There is no public staff registration and no demo staff password. Provision the first administrator using `KOKWET_STAFF_ADMIN_EMAIL` and `KOKWET_STAFF_ADMIN_PASSWORD` in the backend environment. The password must have at least 12 characters. On startup, the account is created only if that email does not already exist. Staff invitation PINs are HMAC-protected with the required, private `KOKWET_STAFF_ACTIVATION_SECRET` of at least 32 characters. This key is environment-only; the backend does not create or read a local key file. To preserve the previous generated 32-byte `staff-activation.key`, configure its hexadecimal contents with a `hex:` prefix; changing the underlying secret invalidates pending invitation codes. Super Admin and regular admins issue role-scoped, six-digit, 24-hour activation PINs in **Staff Access** and share each privately; the PIN is only displayed once and only its keyed hash is stored. Invited staff activate at `/activate` with their phone, code, name, email and a password of at least 12 characters, then sign in using their phone number (email sign-in remains available for existing accounts). Activation codes are single-use, rate-limited and revocable. SMS/WhatsApp delivery is not automated; the admin can open a prefilled WhatsApp message. Staff sessions expire after 12 hours; disabling an account or changing its password revokes its active sessions.

## Cryptographic one-time ticket QR

Paid tickets use versioned `KWT1.` QR payloads authenticated and encrypted with AES-256-GCM. The QR contains only a ticket row identifier and a cryptographically random nonce; it does not expose the buyer phone number or pass details. The backend stores a SHA-256 lookup hash and an AES-GCM-protected copy for displaying the QR again after payment. A gate scan must reach the backend: the server authenticates the QR, then atomically changes a `Paid` ticket to `Used` only once and records the scan time and staff account. Concurrent scans cannot both grant entry; a replay is reported as already scanned with the original time. Existing ticket QR codes are migrated without changing their printed value.

Ticket QR encryption uses the required `TICKET_ENCRYPTION_KEY`, a 64-character hex string decoding to 32 bytes. `KOKWET_TICKET_ENCRYPTION_KEY` is accepted only as a compatibility alias when the canonical variable is unset. The backend validates this secret before initializing database stores and does not create or read a local key file. For local development, set both `TICKET_ENCRYPTION_KEY` and `KOKWET_STAFF_ACTIVATION_SECRET` in the ignored `backend/.env` file used by the documented Uvicorn command. Every backend instance serving the same database must use the same ticket key. If migrating an existing database, convert the existing `ticket-encryption.key` file's 32 bytes to hex and configure that exact value; a different key prevents QR recovery and validation. Do not rotate or regenerate it without a ticket-key migration. Scanning requires a live connection to the authoritative ledger; do not allow offline admission based on a stale local copy, because independent offline scanners cannot prevent the same QR being accepted at multiple gates.

Create **Super Admin 1** interactively, Django-style, from the backend directory:

```bash
python create_superadmin.py
```

The command reads `backend/.env` for local database settings, prompts for a unique email and Kenyan phone number, and reads the password without echoing it (minimum 16 characters, with confirmation). It stores a PBKDF2 hash in the configured application database and prints the login URL and account reference, never the password or a root key. It refuses to create a second Super Admin or reuse another account's email/phone. Alternatively, bootstrap the account on startup with backend-only `KOKWET_SUPER_ADMIN_EMAIL`, `KOKWET_SUPER_ADMIN_PHONE`, and `KOKWET_SUPER_ADMIN_PASSWORD` variables set together. The environment password is used only when creating the account, not to overwrite an existing account on later restarts. A signed-in Super Admin can rotate its password from Staff Access; doing so revokes its sessions and requires signing in again. Successful Super Admin write requests are recorded in the **System audit** control-room tab without request bodies or secrets. The Super Admin cannot be disabled, demoted, or deleted through the staff API. This role does not provide database purging or provider-secret editing. It does not use a frontend root key or an invitation.

The unified control room uses `/login` for sign-in and `/dashboard` for the role-gated workspace; `/staff` remains a compatibility redirect. After sign-in, the API resolves the staff identity and permissions from the server-side session. The dashboard only shows the modules allowed for that account, while FastAPI independently enforces permissions on every protected endpoint. Kokwet uses its existing opaque bearer-session tokens, not frontend role claims or JWTs. The frontend also serves the staff sign-in directly at `/` when the hostname starts with `staff.`. To use a staff subdomain in production, configure DNS/proxy routing to the frontend, set `KOKWET_CORS_ORIGINS` to a comma-separated list containing both the public site and staff-site origins, and build the frontend with its API URL configured. The repository does not contain deployment DNS/proxy configuration.

The hierarchy includes **Super Admin**, **admin**, **moderator**, and **creator**, with delegated capabilities such as live-session control and ticket scanning checked server-side. Super Admin inherits the existing global control-room capabilities, protects its bootstrap identity from staff-account operations, and can inspect its successful change audit. Ordinary staff invitations cannot create this role, and no frontend root key or destructive database-control panel exists.

Local development and the isolated end-to-end tests use SQLite by default at `backend/data/kokwet.sqlite3`. In production, set `DATABASE_URL` to the private PostgreSQL connection URL; this is required by the Render Blueprint and all application features use that database, including media storage. Set `CULTURE_IP_HASH_SECRET` to a private, persistent random secret so rate-limit hashes remain stable across restarts. Keep the staff activation secret stable: changing it invalidates any invitation codes that have not yet been redeemed.

Legacy admin moderation is also available through `GET /api/v1/admin/culture/posts` and `PATCH /api/v1/admin/culture/post/{post_id}/moderate`; these endpoints require the `X-Admin-Key` header and a private `KOKWET_ADMIN_API_KEY`. The `/staff` workspace uses staff identities and role permissions instead.

M-Pesa post preservation and ticket checkout use the same Daraja configuration: `MPESA_ENV` (`sandbox` or `production`), `MPESA_CONSUMER_KEY`, `MPESA_CONSUMER_SECRET`, `MPESA_SHORTCODE`, `MPESA_PASSKEY`, `MPESA_CALLBACK_URL` (a public HTTPS URL ending in `/api/v1/culture/webhook/extend-lifespan`), and `CULTURE_WEBHOOK_SECRET`. Post-preservation callbacks use that configured path; ticket callbacks use the same HTTPS host with `/api/v1/tickets/webhook/mpesa`. Both callback URLs receive the secret as a query parameter for authentication. Without these values, payment requests are unavailable and no post or ticket is marked paid.

## Run

```bash
cd backend
python -m pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

The backend loads `backend/.env` automatically for local development. Copy your local environment values there; never commit real credentials. Render continues to use its configured environment variables.

In a second terminal:

```bash
cd frontend
npm install
npm run dev -- --host 0.0.0.0
```

Open `http://localhost:5173` on the development machine, or use that machine's LAN address (for example, `http://192.168.0.101:5173`) on another device on the same network. The frontend targets the API at the same hostname on port `8000`, and local development allows private-network origins on the Vite port. If you set `KOKWET_CORS_ORIGINS`, include each frontend origin you use. Allow inbound TCP ports `5173` and `8000` through the development machine's firewall if another device cannot connect. API docs are at `http://localhost:8000/docs`.

Festival passes can be selected in the shared responsive ticket drawer on the home page and marketplace. The drawer supports multiple pass tiers, totals the server-priced order, and submits it to `POST /api/checkout/cart`; the existing `POST /api/checkout` single-pass endpoint remains available. A successful checkout sends an M-Pesa STK prompt and reserves inventory for ten minutes. Tickets and gate-entry tokens are available only after the amount-matched M-Pesa callback succeeds. Visitors can retrieve the order status using its unguessable order reference at `GET /api/v1/tickets/orders/{order_id}`.

Ticket prices and availability come from the backend pass catalog. Keep the dates and prices there aligned with the official event information; the drawer displays the main Kokwet Festival event date from the activities endpoint. After payment, each ticket displays a QR code containing its private token. New QR payloads use AES-GCM authenticated encryption and include a keyed fingerprint of the normalized M-Pesa purchaser phone; the raw phone is not placed in the QR. Gate validation checks that binding against the ticket record before atomically marking the ticket used, and displays only the purchaser phone's last four digits for an optional human check. Earlier version-1 encrypted QRs remain valid. An admin can grant selected moderators the **Scan and validate paid festival tickets** permission in Staff Access. Authorized staff use the **Gate scanning** tab to scan the QR with a phone camera or enter the token manually; a camera requires HTTPS and browser permission. Each paid token is atomically accepted once, and repeats are marked duplicate. Because a QR is still a bearer credential, a purchaser can share or screenshot it; the phone check helps staff detect mismatches but cannot make transfers impossible or prove a person's legal identity. The admin gate panel also lists ticket payments that require reconciliation against Daraja.

After payment confirmation, buyers can enter an email to receive a private link to their tickets, or open the same ticket wallet at `/tickets/{order_id}`. The wallet supports printing/saving as PDF and downloading each QR as SVG; anyone with the unguessable order link can view its tickets, so it must be kept private. Email delivery uses an authenticated SMTP server with STARTTLS. Configure backend-only `KOKWET_PUBLIC_SITE_URL` (the public HTTPS site origin), `KOKWET_SMTP_HOST`, `KOKWET_SMTP_PORT` (normally `587`), `KOKWET_SMTP_USERNAME`, `KOKWET_SMTP_PASSWORD`, and `KOKWET_SMTP_FROM_EMAIL` in `backend/.env`; restart the API after changing them. The email action is shown only after the payment callback confirms the order as paid.

Run the isolated end-to-end journey from the backend directory with `python test_e2e_journey.py`. It creates a temporary SQLite database, mocks the Daraja STK request, exercises the real payment callbacks and staff permission checks, and makes no live payment or production-database changes. A successful mock run is not a substitute for a Safaricom sandbox-to-production verification before launch.
