# ADR-0001: Modular Monolith instead of Microservices

## Status
Accepted

## Context

PixFlow is a portfolio project built by a single developer, with no
independent teams, no existing production traffic, and no measured scaling
bottleneck to justify splitting the system into separately deployable
services. Microservices exist to solve organizational and operational
problems: independent deployment cadence per team, independent scaling of
components under different load profiles, and fault isolation between
services owned by different teams. None of those forces are present here.

What *is* present is the need to demonstrate architectural reasoning to a
reviewer, and to build a system with clear domain boundaries (Account, PIX
Key, Transaction, Ledger, Idempotency, Event Processing, Notifications,
Auth, Observability) that could plausibly be extracted into services later
if those forces ever materialized (e.g. Transaction Processing needing to
scale independently of Account Management under real load).

The alternative — building microservices from the start — would require
solving distributed transactions, network-call failure handling, and
service discovery/orchestration before the core domain logic (financial
correctness, concurrency, idempotency) even exists. That would spend
project time on infrastructure plumbing instead of the concepts the
project exists to demonstrate.

## Decision

We will build PixFlow as a single Spring Boot deployable — a "modular
monolith" — organized into packages with explicit domain boundaries
(one package per domain: `account`, `pixkey`, `transaction`, `ledger`,
`idempotency`, `event`, `notification`, `auth`, `observability`).

Each module will:
- Own its own persistence (its own JPA entities/repositories), not shared entities reached into from other modules.
- Expose behavior to other modules only through a small internal interface/service class, not by other modules reaching directly into its repository or entities.
- Communicate with other modules either through direct in-process calls (for synchronous needs) or through Kafka domain events (for asynchronous needs) — the same two communication shapes a microservice split would eventually need, just without the network in between.

This means a later extraction to a real microservice, if ever justified,
would mean replacing an in-process call or in-process event publish with
a network call or a message broker publish — a boundary that already
exists in the code, not a boundary we'd have to discover by untangling
shared state.

## Consequences

**Gains:**
- One process to run, debug, and deploy locally — no service discovery, no distributed tracing needed to follow a request across services, no network partition scenarios to design around yet.
- Transactions that touch multiple domains (e.g. debit one account, credit another) can use a single database transaction with real ACID guarantees, instead of a distributed saga/compensation pattern.
- Faster iteration: refactoring across module boundaries is a compiler error, not a coordinated multi-service deployment.

**Costs / what we give up:**
- No independent scaling — if Transaction Processing needs 10x the compute of Account Management under load, we can't scale it alone; the whole app scales together.
- No independent deployability — a change to Notifications requires redeploying the whole application, not just that module.
- A bug or resource leak in one module (e.g. a blocking call in Notifications) can degrade the whole process, since there's no process boundary between modules.
- Module boundaries are enforced by convention and code review, not by a network boundary — it's possible to accidentally violate them (e.g. reach into another module's repository) unless we're disciplined, or add tooling (e.g. ArchUnit tests) later to enforce it.
