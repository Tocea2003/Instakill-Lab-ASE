# Task 6 – "Make My Post Funnier" with a local LLM (Lab 7)

## Goal
Let users turn the caption they are writing into a funnier version. A small model (`google/gemma-3-270m`) runs locally in LM Studio on port 12345 and exposes the OpenAI-compatible `POST /v1/chat/completions` API. Instakill wraps it in `POST /api/funnyfy`, and both "Create Post" screens (the Vue composer and the Thymeleaf `/posts/new` page) get a **Make It Funnier** button.

## LM Studio setup (steps 1–6)
1. Install LM Studio from https://lmstudio.ai.
2. Developer tab → Server Settings → **Server Port = 12345**.
3. Download the model: `lms get google/gemma-3-270m` (a larger model gives better jokes if your machine can run it).
4. Start the server (Status: Running, or Ctrl+R) with the model loaded.
5. and 6. Check the models endpoint and send one chat completion, either by hand with the curl commands from the assignment or with the helper script:
   ```bash
   ./scripts/check-local-llm.sh              # macOS / Linux / Git Bash
   .\scripts\check-local-llm.ps1             # Windows PowerShell
   ```
   The script fails with a hint if the server is down or the model is not listed.

## Before / after

| | Before | After |
|---|---|---|
| REST API | No text-rewrite endpoint | `POST /api/funnyfy {"text"}` → `{"text"}` (login required) |
| LLM access | None | `TextRewriter` port → `OpenAiChatTextRewriter` adapter (Spring `RestClient`) |
| Configuration | – | `instakill.llm.*` in `application.yaml`; URL from `LOCAL_LLM_URL` |
| Upstream errors | – | 503 (server not running), 504 (timeout), 502 (HTTP error / bad answer) as `ProblemDetail` |
| Vue composer | Textarea + Post | **Make It Funnier** button with spinner, toast "Your post just got funnier 😎", Undo, error toast |
| Thymeleaf `/posts/new` | Textarea + Publish | Same button, toast and Undo (`static/js/funnyfy.js`) |

## Design
- **New bounded context `funnyfy`** with the usual hexagonal layout:
  - `domain/TextRewriter`: the port (`String rewrite(String)`). Nothing else in the app knows an LLM is involved.
  - `application/FunnyfyService`: rejects blank text, calls the port, then tidies the answer. Small models like to wrap the answer in quotes, so a pair of quotes around the whole answer is removed (quotes inside the text are kept). The result is capped at 1000 characters, the `Post.content` limit, without cutting an emoji in half, so the funnier text can always be posted. An empty answer is a 502. There is no database access, so the service is not `@Transactional`.
  - `infrastructure/OpenAiChatTextRewriter`: the **Adapter** to the OpenAI chat completions API. It sends `{model, messages:[system, user], temperature, max_tokens, stream:false}` and reads `choices[0].message.content`, following https://platform.openai.com/docs/api-reference/chat/create.
  - `infrastructure/LocalLlmProperties`: `@ConfigurationProperties("instakill.llm")` record, validated (`url`, `model` and `system-prompt` are required).
  - `api/rest/FunnyfyRestController` with `FunnyfyRequest` (`@NotBlank @Size(max = 1000)`) and `FunnyfyResponse`.
- **Graceful errors.** The adapter turns every failure into an `InstakillException`, and `RestExceptionHandler` maps them to RFC 7807 responses:

  | What happened | Exception | HTTP |
  |---|---|---|
  | Connection refused (LM Studio not running) | `UpstreamUnavailableException` | 503 |
  | Connect or read timeout | `UpstreamTimeoutException` | 504 |
  | LM Studio answered 4xx/5xx (e.g. model not loaded) | `BadGatewayException` | 502 |
  | Body is not JSON, has no `choices`, or the text is empty | `BadGatewayException` | 502 |

  Timeouts are set on the HTTP client (`connect-timeout` 2 s, `read-timeout` 60 s by default), so a hung model never blocks a request thread forever. Spring wraps a timeout differently depending on whether it happens while connecting or while reading the body, so the adapter checks the cause chain for `SocketTimeoutException`. The detail messages are written for the user ("Is the LM Studio server running?"); the URL and the upstream body only go to the log.
- **Content-Length, not chunked.** `RestClient` streams the JSON body with chunked encoding by default, and not every OpenAI-compatible server accepts that. The request factory is wrapped in `BufferingClientHttpRequestFactory` so the request carries a `Content-Length`. A real-socket test covers this.
- **Security.** `/api/funnyfy` is under the existing stateless `/api/**` chain, so it needs the `ACCESS_TOKEN` cookie. Anonymous visitors cannot use the local model.
- **Frontend behaviour.** Clicking the button disables it and the textarea and shows a spinner inside the button. It sends the current text to `/api/funnyfy` and replaces the textarea content with the answer. A toast says "Your post just got funnier 😎" and offers **Undo**. Undo is shown only while the text is still exactly the funnier version, so it never wipes edits made afterwards. On error, the text is left unchanged and a red toast shows the server's `detail`; it hides itself after a few seconds.

## Configuration
```yaml
instakill:
  llm:
    url: ${LOCAL_LLM_URL:http://localhost:12345/v1/chat/completions}
    model: ${LOCAL_LLM_MODEL:google/gemma-3-270m}
    system-prompt: >-
      Always reply with the user's message, but rewritten in a more funny style.
      Reply with the rewritten message only: no introduction, no explanation, no quotes.
    temperature: 0.7
    max-tokens: 256
    connect-timeout: 2s
    read-timeout: ${LOCAL_LLM_TIMEOUT:60s}
```
The Java code has no URL in it. Point the backend at a different server with an environment variable:
```bash
LOCAL_LLM_URL=http://192.168.1.20:12345/v1/chat/completions ./gradlew bootRun     # bash
$env:LOCAL_LLM_URL="http://localhost:1234/v1/chat/completions"; .\gradlew bootRun   # PowerShell
```
The test profile points the URL at `127.0.0.1:1`, where nothing listens, so the test suite never needs a model.

## How to extend
- **Another provider** (OpenAI, Ollama, llama.cpp server): if it speaks `/v1/chat/completions`, only `LOCAL_LLM_URL` and `LOCAL_LLM_MODEL` change. If it speaks something else, write a new `TextRewriter` and mark it `@Primary`. `FunnyfyService` and the controller do not change. `FunnyfyServiceTest.anyTextRewriterPlugsInWithoutChangingTheService` shows this.
- **Other rewrite styles** ("more formal", "add hashtags"): add a `style` field to the request and pick the system prompt from it; the adapter already takes the prompt from configuration.
- **Caching or rate limiting**: wrap the adapter in a decorator that also implements `TextRewriter`.

## Try it
```bash
# LM Studio running on :12345 with google/gemma-3-270m loaded
cd backend && ./gradlew bootRun
cd frontend && npm run dev     # http://localhost:5173, log in as alice / password
```
Type a caption, click **Make It Funnier**, and watch the request arrive in the LM Studio server log. `backend/api.http` has a ready-made `POST /api/funnyfy` request. Stop the LM Studio server and click again to see the "not reachable" toast.

## Files
**New:** `funnyfy/domain/TextRewriter`, `funnyfy/application/FunnyfyService`, `funnyfy/infrastructure/OpenAiChatTextRewriter`, `funnyfy/infrastructure/LocalLlmProperties`, `funnyfy/api/rest/FunnyfyRestController`, `FunnyfyRequest`, `FunnyfyResponse`, `common/error/BadGatewayException`, `UpstreamUnavailableException`, `UpstreamTimeoutException`, `static/js/funnyfy.js`, `scripts/check-local-llm.sh`, `scripts/check-local-llm.ps1`.

**Changed:** `api/rest/RestExceptionHandler` (502/503/504), `application.yaml`, `application-test.yaml`, `templates/post_create.html`, `static/css/main.css`, `frontend/src/App.vue`, `frontend/src/api.js`, `backend/api.http`, `README.md`.

**Tests:** `FunnyfyServiceTest` (cleanup, validation, 1000-char cap, plug-in rewriter), `OpenAiChatTextRewriterTest` (request shape with `MockRestServiceServer`; 404/500, empty and non-JSON bodies → 502; real sockets for Content-Length, connection refused → 503, silent server → 504), `FunnyfyIntegrationTest` (real wiring: 401 without login, 422 for blank text, 503 ProblemDetail when the LLM is down).
