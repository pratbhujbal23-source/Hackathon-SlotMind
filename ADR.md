# Architecture Decision Record

## ADR-001: Strategy pattern for scheduling

**Context:** Scheduling recommendations must support deterministic rules today and an LLM advisor without changing HTTP or asynchronous callers.

**Options:** Put logic in controllers, use conditional service branches, or expose one strategy contract with implementations.

**Decision:** `SchedulingStrategy` is the application seam. `RuleBasedSchedulingStrategy` and `AiSchedulingStrategy` both return ordered recommendations. `SCHEDULING_STRATEGY=rule|ai` selects the implementation at startup.

**Tradeoffs:** Runtime switching is simple and testable, but changing the strategy currently requires restarting the backend. Both strategies must maintain the same validation contract.

## ADR-002: Asynchronous agentic recovery

**Context:** Provider-offline and slot-cancelled events should return immediately while suggestions are generated in the background.

**Decision:** Status endpoints update the disruption transactionally and dispatch recovery work through the named `rescheduleExecutor`. Pending suggestions are idempotent for an appointment and trigger reason.

**Tradeoffs:** The UI briefly shows a pending appointment while work is running. A production deployment would add a durable queue and retry/dead-letter handling; the executor is sufficient for this sprint.

## ADR-003: AI is an advisor, not the source of truth

**Context:** An LLM can return malformed or stale scheduling decisions.

**Decision:** The backend supplies live candidate slots and provider context, accepts only returned slot IDs from that candidate map, rechecks that the slot is still `OPEN` before persistence, and falls back to the rule strategy on transport, authentication, parsing, or validation failures. Suggestions persist `RULE`, `AI`, or `AI_FALLBACK` as their source.

**Tradeoffs:** AI recommendations are constrained to candidates discovered by the database, which reduces hallucination risk but limits the model to the scheduling options the backend provides.

## ADR-004: Transactional approval

**Context:** Multiple coordinators may approve suggestions concurrently.

**Decision:** Accepting a suggestion runs in one transaction, verifies `PENDING` and `OPEN` state, books the replacement slot, releases the old slot, updates the appointment, and accepts the suggestion. A stale or already-booked replacement returns a conflict instead of double-booking.

**Tradeoffs:** A coordinator may need to review a replacement after a conflict. This is preferable to silently overwriting another coordinator's booking.

## ADR-005: Deliberate sprint exclusions

**Context:** The five-hour sprint must demonstrate the reactive rescheduling loop.

**Decision:** Patient portals, billing, waitlist auto-fill, room constraints, durable messaging, and SSE are excluded. The strategy seam is intentionally available for a future `RoomConstraintStrategy`, but no room behavior is implemented in this sprint.

**Tradeoffs:** The current UI polls and uses H2 seed data. These choices keep the walkthrough reliable while leaving clear extension points for a production version.
