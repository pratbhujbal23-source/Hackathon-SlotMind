# SlotMind

Reactive appointment re-scheduling engine for the CareBridge hackathon.

## Run

### Backend

```powershell
cd backend
mvn spring-boot:run
```

The API is available at `http://localhost:8080`. Seed data is loaded automatically. Trigger the demo with:

```powershell
Invoke-RestMethod -Method Patch -Uri http://localhost:8080/providers/PRV-001/status `
  -ContentType 'application/json' -Body '{"status":"OFFLINE"}'
```

### Frontend

```powershell
cd frontend
npm install
npm run dev
```

The dashboard is available at `http://localhost:5173`.

## LLM configuration

The LLM integration is intentionally backend-only and configured through environment variables:

```text
LLM_BASE_URL
LLM_API_KEY
LLM_MODEL
```

Never commit API keys, cookies, or bearer tokens. The LLM is called only by the backend.

The scheduling strategy is runtime-switchable:

```powershell
$env:SCHEDULING_STRATEGY="rule" # default, deterministic
# or
$env:SCHEDULING_STRATEGY="ai"
$env:LLM_API_KEY="your-server-side-key"
mvn spring-boot:run
```

The AI strategy sends appointment context, trigger reason, and validated available-slot candidates to the OpenAI-compatible `/chat/completions` endpoint. If the gateway fails or returns an invalid slot, the rule-based strategy is used.
