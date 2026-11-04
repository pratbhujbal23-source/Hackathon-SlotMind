# SlotMind

## AI-assisted appointment re-scheduling engine

SlotMind is a reactive clinic scheduling engine built for the CareBridge hackathon. When a provider becomes unavailable or a booked slot is cancelled, SlotMind identifies affected appointments, generates replacement recommendations, and presents them to the front desk for approval.

The system is intentionally focused on the re-scheduling loop:

```text
Provider or slot disruption
          ↓
Affected appointments detected
          ↓
Rule-based or AI recommendations generated asynchronously
          ↓
Front desk reviews the recommendation
          ↓
Coordinator accepts or rejects
          ↓
Appointment and slots are updated transactionally
```

## Features

- Spring Boot 3 backend with JPA and an H2 database
- React 18/Vite front-desk dashboard
- Appointment, provider, slot, and suggestion domain model
- Explicit lifecycle states
- Pluggable scheduling strategy contract
- Rule-based scheduling fallback
- OpenAI-compatible LLM integration through LiteLLM
- Runtime switching between rule-based and AI strategies
- Automatic provider-offline and slot-cancelled recovery
- Duplicate pending suggestion prevention
- Transactional suggestion acceptance
- Replacement-slot validation to prevent double-booking
- Seed data for a complete live walkthrough

## Technology stack

| Area | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.3, Spring Web, Spring Data JPA |
| Database | H2 in-memory database |
| Frontend | React 18, Vite |
| AI integration | OpenAI-compatible chat completions API |
| Build tools | Maven, npm |

## Project structure

```text
SlotMind/
├── backend/
│   ├── src/main/java/com/slotmind/
│   │   ├── api/          REST controllers and DTOs
│   │   ├── config/       Seed data, CORS, strategy configuration
│   │   ├── domain/       JPA entities and state enums
│   │   ├── repository/   Spring Data repositories
│   │   └── service/      Scheduling strategies and orchestration
│   └── pom.xml
├── frontend/
│   ├── src/              React dashboard
│   ├── package.json
│   └── vite.config.js
└── README.md
```

## Prerequisites

- JDK 17 or newer
- Maven
- Node.js and npm

Verify the Java installation:

```powershell
java -version
javac -version
mvn -version
```

`javac` must be available because Maven compiles the backend source.

## Run locally

### 1. Start the backend

Open a PowerShell terminal:

```powershell
cd C:\Hackathon-Prat\backend

$env:JAVA_HOME="C:\Users\poweroot\.jdk\jdk-17.0.18"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:SCHEDULING_STRATEGY="rule"

mvn spring-boot:run
```

The backend runs at:

```text
http://localhost:8080
```

Seed data is loaded automatically when the application starts.

### 2. Start the frontend

Open a second PowerShell terminal:

```powershell
cd C:\Hackathon-Prat\frontend
npm.cmd install
npm.cmd run dev
```

Open the dashboard:

```text
http://localhost:5173
```

## Enable AI scheduling

The LLM is called only by the backend. Never place the API key in React code, frontend environment files, source control, or screenshots.

```powershell
cd C:\Hackathon-Prat\backend

$env:JAVA_HOME="C:\Users\poweroot\.jdk\jdk-17.0.18"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:SCHEDULING_STRATEGY="ai"
$env:LLM_BASE_URL="https://litellm-qc.zycus.net/v1"
$env:LLM_MODEL="qwen-cursor"
$env:LLM_API_KEY="your-server-side-api-key"

mvn spring-boot:run
```

The AI strategy sends appointment context, trigger reason, provider information, and validated available-slot candidates to `/chat/completions`.

If the LLM is unavailable, returns invalid JSON, or recommends an invalid slot, SlotMind falls back to the rule-based strategy.

## Scheduling strategies

Both strategies implement the same contract:

```text
Appointment + trigger context + available candidates
    → ordered scheduling recommendations
```

### Rule-based strategy

Recommendations are ordered by:

1. Nearest open slot with the same provider
2. Nearest open slot with a matching specialty
3. Nearest valid alternate-provider slot

For a provider-offline event, the offline provider is excluded.

### AI strategy

The AI receives:

- Patient and appointment details
- Appointment type and urgency context
- Original provider and specialty
- Trigger reason
- Available slots and provider roster

It returns a recommended slot, optional alternate provider, confidence, and staff-friendly reasoning. The backend validates every returned identifier against the database before storing a suggestion.

## API reference

### Create an appointment

```http
POST /appointments
```

```json
{
  "patientName": "Maria Lopez",
  "appointmentType": "ROUTINE",
  "providerId": "PRV-002",
  "slotId": "SLT-004"
}
```

The selected slot must be `OPEN` and belong to the selected provider. A successful booking changes:

```text
slot: OPEN → BOOKED
appointment: CONFIRMED
```

### List appointments

```http
GET /appointments
GET /appointments?status=RESCHEDULE_PENDING
```

### Mark a provider offline

```http
PATCH /providers/{providerId}/status
```

```json
{
  "status": "OFFLINE"
}
```

The endpoint returns immediately with `202 Accepted`. Suggestions are generated asynchronously for all confirmed appointments assigned to that provider.

### Cancel a slot

```http
PATCH /slots/{slotId}/status
```

```json
{
  "status": "CANCELLED"
}
```

The appointment on that slot receives an asynchronous suggestion with trigger reason `SLOT_CANCELLED`.

### Generate a manual suggestion

```http
POST /appointments/{appointmentId}/suggest
```

### List suggestions

```http
GET /suggestions
GET /suggestions?status=PENDING
```

### Accept or reject a suggestion

```http
PATCH /suggestions/{suggestionId}
```

```json
{
  "status": "ACCEPTED"
}
```

Acceptance is transactional. The backend verifies that the suggestion is still pending and the replacement slot is still open before updating the appointment and both slots. If another coordinator already took the slot, the request returns `409 Conflict` instead of double-booking.

## Demo walkthrough

### 1. Start the application

Start the backend and frontend using the commands above.

### 2. Trigger a provider disruption

```powershell
Invoke-WebRequest `
  -Method Patch `
  -Uri "http://localhost:8080/providers/PRV-001/status" `
  -ContentType "application/json" `
  -Body '{"status":"OFFLINE"}'
```

### 3. View generated suggestions

```powershell
Start-Sleep -Seconds 3

Invoke-RestMethod `
  -Uri "http://localhost:8080/suggestions?status=PENDING" |
  ConvertTo-Json -Depth 5
```

### 4. Review in the dashboard

Open `http://localhost:5173` and review:

- Affected appointment
- Recommended slot
- Alternate provider
- Confidence
- Trigger reason
- Staff-friendly reasoning

### 5. Accept a recommendation

Click **Accept recommendation**. The appointment is moved to the recommended provider and slot, and the original slot is released.

## Seed data

The backend seeds five providers, six slots, and four appointments. The primary demo provider is:

```text
PRV-001 · Dr. Sarah Chen
```

The provider-offline walkthrough affects:

```text
APT-001
APT-002
```

## Security

- Keep LLM credentials server-side.
- Use environment variables or a secret manager.
- Never commit `.env` files, API keys, cookies, or bearer tokens.
- The repository includes `.env.example` files containing placeholders only.
- Rotate any credential that has been exposed publicly.

## Development commands

Backend compile check:

```powershell
cd backend
mvn test
```

Frontend tests:

```powershell
cd frontend
npm.cmd test
```

Frontend production build:

```powershell
cd frontend
npm.cmd run build
```

## Future extensions

- Room constraint strategy
- Patient portal
- Waitlist automation
- Notifications
- Persistent production database
- Server-Sent Events for live suggestion updates
- Provider load and day-view scheduling
