# Task 3 – Media upload

## Goal
Let the frontend upload an image or video to a local `media` directory and reference it from a post
through the existing `imageUrl` field.

## Before / after

| Before | After |
|--------|-------|
| Posts only accept a pasted `imageUrl` | `POST /api/media` stores a file and returns `{ "mediaUrl": "/media/<uuid>.<ext>" }` (201) |
| No file storage | Files are saved to `media.base-path` (default `./media`, created on demand) |
| Nothing served from disk | `/media/**` is mapped to that directory by `MediaWebConfig` |
| Composer has a URL input only | Composer also has a file picker; the returned URL is put into `imageUrl`; videos render in a `<video>` tag |

## Design
- `media.application.MediaStorageService`: validates (non-empty, extension whitelist
  `jpg jpeg png gif webp mp4 mov webm`), names the file `<IdGenerator UUID>.<ext>` (original filename is never
  used on disk, so no path traversal), creates the directory if needed and returns the public URL path.
- `media.api.rest.MediaController`: `POST /api/media`, multipart field `file`. It sits under `/api/**`, so it
  already requires a logged-in user (401 otherwise).
- `infrastructure.config.MediaWebConfig`: `/media/** -> file:<media.base-path>/`.
- `application.yaml`: `media.base-path=media`, `spring.servlet.multipart.max-file-size` and
  `max-request-size` = 200MB. `RestExceptionHandler` maps `MaxUploadSizeExceededException` to 413.
- Frontend: `uploadMedia` in `api.js`, file input in the composer, `/media` added to the Vite proxy.
- Note: `/media/**` is served by the web filter chain, so viewing a file needs the login cookie (the SPA sends it).

## How to extend
Add an extension to `ALLOWED_EXTENSIONS`, or replace `MediaStorageService` with an S3-backed one that returns
an absolute URL; the controller and frontend stay unchanged.

## Files
- New: `media/application/MediaStorageService.java`, `media/api/rest/MediaController.java`,
  `media/api/rest/MediaResponse.java`, `infrastructure/config/MediaWebConfig.java`
- Changed: `application.yaml`, `RestExceptionHandler.java`, `frontend/src/{App.vue,api.js}`, `frontend/vite.config.js`, `.gitignore`
- Tests: `MediaStorageServiceTest` (unit), `MediaIntegrationTest` (upload, fetch back, 401 anonymous, 400 bad extension)
