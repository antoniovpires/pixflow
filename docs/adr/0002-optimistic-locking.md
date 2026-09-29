# ADR-0002: Optimistic Locking for Account Balance Updates

## Status
Accepted

## Context

Optimistic locking consists in trusting the row's version (a commonly used DB column) to make sure that the row is being updated with its correct values. Thus validating if any changes were made while the first update was doing math or any other code side calculations before updating it.

Given that DB locks generally take time and pessimistic locking is based on locking the row as soon as it is read, then optimistic locking would save time and connections.

Maybe if this was a payment gateway for a person or company that gets loads of transactions in a short period of time, like betting websites or gaming in general, then pessimistic could make more sense because optimistic could cause retry overload.

## Decision

All things considered we should stick with optimistic locking due to the fact that since we are handling with "personal" accounts, concurrent operations will be an exception, not the rule. Speaking of which, if a balance update returns 0 updated rows we must retry it immediately by selecting the new values on the database and recalculating the correct values so that both concurrent operations are successfully accomplished.

We should also add a contigency in case this enters a loop. So, adding a MAX_ATTEMPTS of value 5 should be reasonable to fix this issue. If it hits MAX_ATTEMPTS we may drop the transaction and signal the user that something went wrong.

## Consequences

- Faster balance updating processes.
- Less DB overhead due to how optimistic locking works.