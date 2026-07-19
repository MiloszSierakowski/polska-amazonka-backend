# link-checker-worker

Required environment variable (use the same secret in the Spring backend):

```text
LINK_CHECKER_WORKER_TOKEN=change-me
```

Playwright HTTP worker used by the backend scheduled link validation cron.

## Endpoints

- `GET /health` → `{ "status": "UP" }`
- `POST /check` → `{ "url": "https://..." }`

Response:

```json
{
  "status": "WORKING | BROKEN | UNCERTAIN | BLOCKED",
  "reason": "...",
  "finalUrl": "...",
  "httpStatus": 200,
  "checkedAt": "..."
}
```

## Local run

```bash
npm install
npx playwright install chromium
npm start
```

Default port: `3001`.

## Tests

```bash
npm test
```

Tests do not call real shop URLs.
