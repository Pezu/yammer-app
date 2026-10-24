# ADR-0001 — Guarded conditional UPDATEs for money-mutating writes

Status: accepted · Epic: #1 · Date: 2026-10-24

## Context

`OrderService.pay()` (FULL / PARTIAL / AMOUNT) reads a table's unpaid lines and then stamps
`payment_id` on them or splits them. The codebase has no locking of any kind (no `@Version`,
no pessimistic locks): two concurrent payments on the same table each read the same unpaid
lines and each create a SUCCESS payment and a fiscal receipt — a double charge (finding A2,
major). Settled lines also get mutated outside `pay()`: `OrderReportService.updateItems` and
`deleteOrder` edit unpaid quantities from the backoffice.

Two candidate fixes were argued by the pair:

1. **Per-order-point advisory lock** (`pg_advisory_xact_lock`) at the top of `pay()` — one
   line, serializes payments per table.
2. **Guarded conditional UPDATEs** — every settlement/split/quantity write carries its
   precondition in the WHERE clause and the caller treats 0 affected rows as a conflict.

## Decision

Guarded conditional UPDATEs, applied at the repository layer:

- settle: `UPDATE order_item SET payment_id = :p WHERE id = :id AND payment_id IS NULL`
- reduce: `UPDATE order_item SET quantity = :new WHERE id = :id AND quantity = :expected AND
  payment_id IS NULL` (same guard on the redistributing delete)
- any 0-row result throws 409 ("The bill changed — review and retry"), rolling back the
  payment row and split inserts with it.

`OrderReportService.updateItems` adopts the same guards.

## Consequences

- No write path — present or future — can settle a line twice or mutate a line a concurrent
  transaction already settled, regardless of whether it remembered to take a lock. This is
  what rejected the advisory lock: it only protects callers that acquire it, and
  `updateItems`/`deleteOrder` already write outside `pay()`.
- It is the established house pattern (the fiscal transitions in `PaymentRepository` work
  exactly this way), so reviewers check one idiom.
- The losing request gets a clean, user-explainable 409 instead of waiting on a lock.
- Cost: the plan/apply code in the three pay modes must check affected-row counts instead of
  trusting entity saves; slightly more code than one lock line, in exchange for coverage of
  all writers.
