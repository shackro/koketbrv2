# Kokwet content control and backend map

## Current architecture

The site is a React/Vite application backed by FastAPI and SQLite. Staff sign-in,
story publishing, culture moderation, impact content, and roadmap content already
use backend endpoints. Do not copy the Supabase/Next.js examples in the supplied
notes into this application: they do not match its runtime, and a frontend
environment variable is not a safe place for an administrator secret.

FastAPI is the application/API write boundary; SQLite stores structured content,
permissions, and uploaded media. The trusted local `backend/create_superadmin.py`
command is the separate operator-only bootstrap path for the first Super Admin
account. Editable media fields accept HTTPS URLs or uploaded files. Uploaded
assets receive generated IDs, are type- and size-checked, and are served through
public `/api/media/{id}` responses; only authorized staff can upload them.
Public GET endpoints return published content only. Staff write endpoints use
the existing bearer-token session and check permissions on the server.

## Page-to-data inventory

| Page / component | Human-editable display data | Current source | Target owner |
| --- | --- | --- | --- |
| Global header, menu, footer | WhatsApp number/message; navigation labels, routes and visibility; footer copy; logo variants | SQLite-backed site-content document, rendered by `SiteLayout.jsx` | Global site settings; admin |
| Home: hero media | Video source, poster, backdrop photograph, shield artwork | SQLite-backed site-content document and uploaded-media table, rendered by `HeroSection.jsx` | Each video/image field accepts an HTTPS URL or direct upload; admin |
| Home: shield | Node words, meanings, groups, descriptions, accents; rotating photo cards, labels, captions and interval | SQLite-backed site-content document and uploaded-media table | Every editable shield image supports URL or upload; admin |
| Home: story strip | Voice-note title, optional caption, required cover and recording, creator, publish state and priority | SQLite `festival_stories`; public API featured limit is 10; audio served through a published-story endpoint | Authorized staff can record or upload audio, preview, replace/remove it, and use an uploaded cover or URL; at most 10 featured |
| Home: community spotlight | Mission copy/photos/points; visual labels; role cards; pathway cards and destinations; section headings; enterprise cards | SQLite-backed site-content document and uploaded-media table | Every editable image supports URL or upload; admin |
| Home: event board and promotion | Event title, category, venue, date, details, external link, main-event flag | SQLite event records | SQLite event records; admin |
| Home: artist lineup | Artist name, style, image and order | SQLite-backed site-content document and uploaded-media table; legacy browser-local settings removed | Artist images support URL or upload; admin |
| Home: enterprise showcase | Venture title, summary and image; section heading | SQLite-backed site-content document and uploaded-media table | Enterprise images support URL or upload; admin |
| Home: passes/countdown | Pass name, price, stock, note, availability and main event | SQLite pass catalog; Daraja STK, amount-verified callback and ten-minute inventory reservation | Admin manages catalog; visitors receive tickets only after confirmed payment |
| Home: join CTA | Heading, supporting text, cohort descriptions and onboarding labels | SQLite-backed site-content document; member profiles stored in SQLite | Homepage content; admin |
| Open Hearth | Anonymous post/voice composer with recording and audio-file upload, 24-hour feed, featured voice, Live Lounge (current external stream, topic chat and always-available public chat), Nine Houses | SQLite culture posts plus live sessions/messages; feed limit is 20 | Visitors can record or upload a voice note, preview/remove it, and publish it with optional text |
| Nine Houses | House name, anchor, heritage, dialect, achievement, ordering | SQLite-backed profiles rendered from the shared content API | Nine ordered SQLite house profiles; admin |
| House voice viewer | Up to three attributed weekly community nominations per selected house | SQLite weekly selections; server-side cap is three | Weekly community nominations remain separate from the 48-hour staff-curated slots |
| House voice stories | Three audio-first voice-note slots per house, with optional caption and cover; text-only notes allowed; expires 48 hours after publish/update | SQLite `house_voice_stories`; public expiry filtering; rolling 48-hour publish/update cap of five per house/slot; staff event audit; read marks stored in visitor browser | Small circles with short titles sit directly before History & Heritage; tapping opens a reading/listening window and Mark as read action. Admins manage all houses and assigned staff manage their houses; authorized staff can record/upload, preview, replace/remove audio, and use a cover upload or URL |
| Impact | Hero, pathways, report and allocations, goals | SQLite JSON document; admin API and staff editor already exist | Keep schema/API; admin |
| Roadmap | Heading, copy, phases, milestones and statuses | SQLite JSON document; admin API and staff editor already exist | Keep schema/API; admin |
| Services | Page heading, service-card copy/icons, mentorship prompt and illustrative naming guide; attribution requires explicit consent | SQLite-backed site-content document | Page content; admin |
| Marketplace | Heading and pass/event display | Persistent SQLite event/pass records and site-content copy | Persistent event/pass records and page copy; admin |
| Staff and artist settings | Accounts, roles, stories, moderation, featured voices, delegated ticket scanner access and site content | Existing staff API plus SQLite-backed site-content document | Admin control room; scanner access granted per staff account |
| Festival gate | One-time validation of paid ticket QR codes; camera scanning and manual token fallback | Ticket token stored with each paid order; atomic staff-only scan API | Admins and explicitly selected staff; repeat scans are rejected |
| Live Lounge | External TikTok/Instagram stream, topic and host; topic chat and public lounge messages | SQLite live sessions/messages, embedded in `/culture#live-lounge`; legacy `/live` links redirect there | Staff manage streams and chat in the Open Hearth control; visitor chat remains available between streams |

## Permissions

* **Super Admin:** one protected, server-seeded identity with all admin control
  room capabilities. It is created through the interactive backend bootstrap or
  the private backend environment seed, uses the standard hashed-password and
  server-session flow, and is never assignable through staff invitations.
  Successful writes made with its session are recorded without request bodies
  or credentials and are visible only to the Super Admin. Staff endpoints cannot
  disable, demote, or delete this identity. This is not a database-purge or
  provider-secret management role.
* **Admin:** complete control of site settings, all page content/media, event and
  ticket configuration, staff access, moderation, house profiles/voices/stories,
  ticket scanning, and live sessions.
* **Moderator:** Open Hearth/community moderation and the existing moderator
  content workflows; no staff-account, global-settings, or catalog management.
* **Creator:** only publishing/story and explicitly delegated capabilities.
  House voices and house voice stories require an assigned house; live management
  and ticket scanning require their corresponding assigned capabilities.
* **Delegated staff:** the administrator grants capabilities per account. A
  staff member may publish/manage homepage stories (their own unless separately
  granted broader access) and/or manage house voice stories only for assigned
  houses.
  The admin may also delegate live-session/chat management and ticket scanning
  explicitly. At the event, selected moderators can be granted scanner access
  individually; there is no 20-30 account cap.
  Story display and house voice selection are capped server-side at 10 and 3
  respectively. The Open Hearth public feed displays at most 20 posts.
* **Other staff permissions:** preserve existing moderator/creator behavior
  unless an administrator changes their account permissions. Client-side UI
  hiding is convenience only; all enforcement belongs in FastAPI.
* **Visitors:** can post community stories, listen to featured voices, and join
  the active topic chat or always-open public lounge inside the Open Hearth.
  Live video remains hosted on TikTok/Instagram; Kokwet provides the session
  link and moderated text interaction, not video ingest.

The unified control room is served by `/login` and `/dashboard`; `/staff` is
kept as a compatibility redirect. Both routes use the same React workspace,
which presents modules based on the resolved staff permissions. Route visibility
is only a UI convenience: protected API calls still require a valid server-side
session and the matching role/capability. No client-stored role or secret grants
access.

## Open Hearth experience

The page separates three ways to participate: post a short-lived community
voice, join the persistent public text lounge, or enter a host's topic chat
while a TikTok/Instagram stream is active. The live room is embedded below the
composer/feed, with quick links at the top; starting a stream is kept in the
staff Open Hearth control, separated into Live Lounge/chat and Community posts
views. The homepage may still
surface a Live shortcut, but it opens the embedded Open Hearth room. This keeps
anonymous posting, curated house voices, and live discussion in one community
destination without suggesting Kokwet hosts the external video.

## Backend shape

Keep the existing single FastAPI service, SQLite database and bearer-token staff
sessions. Extend the existing modules instead of adding Supabase or a parallel
authentication system:

* `publishing.py`: staff permission records, story ordering/caps, house voice
  selection, staff management.
* `culture.py`: Open Hearth display cap, public posts, reports and moderation.
* `impact.py` and `roadmap.py`: retain their validated JSON document APIs.
* `site_content.py`: global settings, homepage content/media, house profiles,
  artist lineup, Services/Marketplace copy and other page content.
* `media.py`: authenticated staff uploads, generated media IDs, content checks,
  SQLite persistence, public delivery and byte-range responses for video.
* `catalog.py`: persistent event/pass inventory, Daraja ticket checkout and
  callbacks, ticket retrieval, reservation expiry, and atomic one-use gate scans.
* `members.py`: durable community profiles and compatible registration routes.
* `live.py`: live sessions and moderated chat messages.
* Public read routes return published content. Staff write routes require a
  valid session and an explicit role/capability check.
* SQLite writes that affect multiple records (such as ticket inventory and
  publishing a live story with its live session) use transactions. Site media
  references are validated as HTTPS URLs or safe same-site paths. Direct uploads
  are restricted to supported image, video, and audio formats, size limits, and
  generated IDs; never accept arbitrary filesystem paths from the browser.
* Live chat starts with persisted, rate-limited messages and short-interval
  refresh using this single-instance deployment. If the service later runs
  multiple workers, move message fan-out to a shared broker before relying on
  process-local WebSocket state.

## Delivery status and remaining work

The SQLite content/catalog APIs, shared public content fetch, homepage controls,
house profiles, per-account story/house/live permissions, event/pass editor,
Impact/Roadmap editors, and external-stream live companion/control pages are
implemented. The community spotlight, including its mission points, role cards,
pathways, images, labels and destinations, is now editable in the staff content
editor.

Remaining gaps identified in the page inventory:

1. Move remaining Open Hearth page copy and any other still-human-maintained
   page microcopy into staff-managed content.
2. Audit profile, mentorship and feedback submissions for durable backend
   storage where those flows need staff follow-up.
3. Run broader end-to-end checks for visitor, admin and each delegated-staff
   capability; the frontend production build and live public content endpoint
   have been verified.

The story, Open Hearth and house-voice limits above are display/featured limits,
not destructive database-retention limits. Existing Open Hearth expiry and
moderation rules remain in force.
