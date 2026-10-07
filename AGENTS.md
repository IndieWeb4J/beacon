# Project agent memory

Beacon is the Webmention service for Jacob's site: it **sends** webmentions for
outbound references in posts and **receives** webmentions for inbound mentions.
It owns webmention state; it does **not** own content (that is Bastion).

## Repository layout

Multi-module, mirroring Sigil:

- `beacon-client/` - published library (`dev.jacobandersen:beacon-client`): the
  producer-owned event schemas (`dev.jacobandersen.beacon.event.WebmentionEvent`,
  `WebmentionSubjects`, stream `WEBMENTION`, subjects `webmention.verified|changed|removed`)
  and API types (`dev.jacobandersen.beacon.api`), plus the shared
  `WebmentionInteraction` enum. Consumers (Bastion's projector) depend on it.
- `beacon-app/` - the Spring Boot server (`bootJar` -> `beacon.jar`).

## How it works

- **Event-driven**: consumes Bastion's `content.post.*` from the JetStream
  `CONTENT` stream with a durable consumer (`beacon.events.nats.*`). A per-post
  `version` high-water mark (`content_event_checkpoint`) guards against
  duplicates/reordering. On a rename (`previousUrl`) the old source URL's
  notifications are retracted first.
- **Send**: `WebmentionService.reconcile` derives mentionable URLs from the fat
  mf2 (`Mf2TextExtractor` + `UrlExtractor`), excludes own content
  (`ContentUrlService.isOwnContentUrl`), diffs against stored
  `webmention_notifications`, and sends; retries use exponential backoff and an
  endpoint cache. Jobs run via JobRunr.
- **Receive**: `POST /webmention` (form/multipart) validates the request, confirms
  the target is a currently-public post via `content-client` (`ContentReadClient`),
  stores a pending row and asynchronously verifies the source, emitting
  `webmention.verified`/`webmention.removed`.
- **Receive errors**: 202 for accepted, 400 for a bad request, 429 (with `Retry-After`) when
  the source is over its flood-control limit, and 500 via `WebmentionExceptionHandler` for
  anything unexpected. Verification is async and **only successful verifications are stored**:
  an unreachable source, or a source that never linked, is not persisted. A previously
  verified mention that returns 410 Gone or stops linking is retracted (mark deleted + emit
  `webmention.removed`, Webmention 3.2.4); a 404 is unreachable, not gone, and leaves any
  existing mention untouched. There are no out-of-band verification retries.
- **Discovery SSRF**: `WebmentionHttpClient.discoverWebmentionEndpoint` follows redirects
  manually, validating every hop with `SourceHostValidator` and capping the body, like the
  source fetcher.
- **No cross-service FK**: posts are referenced by the content service's post id.

## Build and test

- `./gradlew test` runs the suite (Testcontainers Postgres + NATS for integration
  tests); `./gradlew ktlintCheck` lints (`ktlintFormat` fixes).
- Config: `beacon.content.*` (own URL space), `content.client.*` (Bastion read
  API), `beacon.events.nats.*`, `beacon.webmention.*`. See `beacon-app/src/main/resources/application.yaml`.
- Jackson 3 (`tools.jackson.*`), not Jackson 2.
