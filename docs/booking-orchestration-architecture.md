# Booking Orchestration Architecture

## Purpose

This document captures the target architecture for how Rez should accept booking requests from different user-facing entrypoints, resolve the business context needed for a booking, and call the generic reservation engine.

It is meant as a handoff/design document for future refactoring.

## Motivation

This architecture is motivated by the need to support multiple kinds of booking flows without making the generic reservation engine depend on one specific surrounding domain model.

The concrete drivers are:
- the same reservation core should support multiple interaction surfaces, such as Telegram, Twist, mobile app, and web app
- different booking domains need different orchestration, for example court booking versus coach booking
- some booking flows depend on facility metadata, while others do not
- user/member metadata may affect booking behavior, eligibility, and participant identity
- future composite requests, such as coach + court, require deterministic saga-style orchestration
- facility and user ownership may move to another service, while Rez should remain the generic booking engine

In short:
- Rez should stay generic about what a bookable resource is
- the orchestration layer should absorb domain-specific lookup and workflow differences
- the channel/transport layer should remain thin

## Terminology

### Transport

A transport is the technical mechanism by which a request reaches the system.

Examples:
- Telegram webhook HTTP POST
- Twist webhook HTTP POST
- mobile app HTTP request
- web app HTTP request
- future internal RPC call

Transport is a delivery concern. It says how the request arrived, not what the booking means.

### Interaction Surface / Origin

In this document:
- `interaction surface` is the prose term
- `origin` is the code term

They mean the same thing: the user-facing source through which the request is expressed.

The word `channel` is a common synonym for the same idea, but this document intentionally uses `interaction surface` / `origin` instead.

Examples:
- Telegram chat with a bot
- Twist thread
- mobile app booking screen
- web app booking form

An interaction surface is closer to product/user experience than transport, but still not the booking domain itself.

A single transport often carries a single interaction surface, but the concepts are not identical:
- Telegram webhook is a transport endpoint
- Telegram chat is the interaction surface

### Booking Domain / Use Case

The booking domain is the business meaning of the request.

Examples:
- court booking
- coach booking
- coach + court booking
- future hotel room booking
- future meeting room booking

This is the level at which orchestration differs.

### Reservation Core

The reservation core is the generic booking engine inside Rez.

It should only care about:
- candidate `resourceIds`
- requested `dateTime`
- participants / reservation payload
- recipient / callback identity
- booking state and locking

It should not care whether a resource is:
- a tennis court
- a coach
- a hotel room
- a flight seat

## Current Situation

Today the code path from Telegram to booking is approximately:

1. `TelegramEndpoint.onUpdate()` receives the webhook update.
2. It resolves `botToken -> facilityId, timezone` via `FacilityByBotTokenView`.
3. It invokes `BookingAgent.chat(...)`.
4. The agent uses `BookingService` tools.
5. `BookingService.bookCourt(...)` calls `FacilityEntity.getFacility()` to load `resourceIds` and timezone-related data.
6. `BookingService` initializes `ReservationEntity` with the candidate `resourceIds`.
7. `ReservationEntity` and `ResourceEntity` perform the generic booking competition and lock acquisition.
8. `DelegatingServiceAction` sends outcome notifications through `NotificationSender`.

This works, but it mixes concerns:
- origin handling
- AI intent extraction
- facility/user lookup
- booking orchestration
- generic reservation engine

It also couples booking orchestration too directly to `FacilityEntity`, which is a problem if facility and user concepts move to another service.

## Architectural Goal

The goal is to separate four concerns:

1. interaction-surface adapters
2. AI intent extraction
3. deterministic booking orchestration
4. generic reservation engine

The reservation engine should remain generic.

The orchestration layer should resolve the business context needed to prepare a reservation request, including external metadata from other services.

## Target Layering

### 1. Interaction-Surface Adapter Layer

Examples:
- `TelegramEndpoint`
- `TwistEndpoint`
- future mobile/web endpoint

Responsibilities:
- parse incoming payloads
- identify sender and conversation
- collect interaction-surface-specific metadata
- call an interaction-surface-agnostic conversation/orchestration entrypoint

Non-responsibilities:
- business booking rules
- facility/user lookup policy
- reservation locking/orchestration

Suggested neutral input model:

```java
public record OriginRequestContext(
    String origin,
    String senderExternalId,
    String senderDisplayName,
    String recipientId,
    String conversationId,
    Map<String, String> attributes
) {}
```

Examples:
- Telegram:
  - `origin = "telegram"`
  - `senderExternalId = from.id`
  - `recipientId = botToken:chatId`
  - `attributes["botToken"] = botToken`
- mobile app:
  - `origin = "mobile"`
  - `senderExternalId = authenticated user id`
  - `recipientId = push destination or null`

### 2. AI Intent Layer

Example:
- `BookingAgent`

Responsibilities:
- interpret user intent
- extract structured arguments
- ask follow-up questions when needed
- call tools with structured parameters

Non-responsibilities:
- direct facility/user service choreography
- deterministic business workflow execution
- compensation logic
- resource candidate resolution

The agent should act as a smart parser/dialog manager, not as the owner of booking orchestration.

## Session Concepts

The word "session" can mean two different things in this architecture. They should not be conflated.

### Agent Session

An agent session is the conversational memory associated with a user interaction thread on a given interaction surface.

Examples:
- one Telegram chat with one bot
- one Twist thread
- one mobile in-app conversation

This is where the LLM remembers:
- earlier user messages
- clarifications already asked
- prior conversational context

In the current code, the Akka Agent session id used by `TelegramEndpoint` is this kind of session.

### Booking Session Context

A booking session context is deterministic application data associated with the current interaction.

Examples:
- booking domain
- scope id
- timezone
- sender identity
- recipient identity
- possibly lightweight routing metadata

This is not conversational memory. It is application context that the orchestration layer may need in order to execute tools deterministically.

Suggested shape:

```java
public record BookingSessionContext(
    String bookingDomain,
    String scopeId,
    String timezone,
    String senderExternalId,
    String recipientId,
    Map<String, String> attributes
) {}
```

### Relationship Between The Two

These two concepts may share the same lookup key, but they are different things:
- agent session = conversational state for the model
- booking session context = deterministic state for the application layer

The agent session id can be used as the key for application-side lookup if a lookup is needed.

### Important Recommendation

Do not introduce a distributed ephemeral store by default just to avoid passing a few parameters.

Prefer these options in order:

1. Put stable routing facts directly in the tool input or in the agent message context.
2. Re-resolve cheap deterministic context on each tool call through a resolver/gateway.
3. Only introduce a separate application-side session context store if repeated external resolution becomes materially expensive or the tool surface becomes unreasonably noisy.

In other words:
- conversational memory naturally belongs in the agent session
- deterministic booking context should only become separately stored state if there is a clear operational reason

### 3. Booking Orchestration Layer

This is the deterministic application layer.

Responsibilities:
- resolve business context from external services
- resolve candidate reservation resources
- apply deterministic booking rules
- dispatch to the correct workflow/use-case handler
- run composite booking workflows or sagas when needed
- call the generic reservation engine with explicit `resourceIds`

This layer is where facility metadata and user/member metadata belong.

Examples of metadata this layer may need:
- facility timezone
- facility policies
- candidate court resources
- user/member id
- membership status
- canonical player full name
- coach eligibility / coach catalog data
- location/radius filters

### 4. Reservation Core Layer

Examples:
- `BookingEndpoint`
- `ReservationEntity`
- `ResourceEntity`
- `ReservationAction`
- `ResourceAction`

Responsibilities:
- accept explicit candidate `resourceIds`
- fan out availability checks
- select / reserve a resource
- persist booking outcome
- manage resource locking
- manage cancellation

Non-responsibilities:
- facility lookup
- user lookup
- origin-specific replies
- AI interpretation
- cross-domain business search

## Key Design Principle

The booking domain-specific question is:

> How do we resolve the candidate reservation resources and booking context for this user intent?

That question must be answered before the reservation core is invoked.

For courts, this may depend on:
- bot token or facility id
- facility timezone
- facility membership policy
- user/member data
- facility resource set

For coaches, this may depend on:
- requested coach name or filters
- user/member data
- coach catalog/profile service
- geography / radius filters
- coach availability resource set

For coach + court, this becomes a composite workflow.

## Proposed Core Interfaces

### Booking Context Resolution

```java
public record BookingContext(
    String bookingDomain,
    String scopeId,
    String timezone,
    Map<String, String> attributes
) {}
```

`scopeId` is intentionally generic.

Examples:
- court booking: facility id
- coach booking: coach pool id, club id, or organization id
- future domains: another catalog/group identifier

```java
public interface BookingContextResolver {
    BookingContext resolve(OriginRequestContext origin);
}
```

This resolves the initial context for a request based on origin / interaction-surface information.

Examples:
- Telegram bot token -> `bookingDomain = courts`, `scopeId = facilityId`
- another Telegram bot token -> `bookingDomain = coaches`, `scopeId = clubId`
- web route `/courts/...` -> `bookingDomain = courts`
- mobile app coach section -> `bookingDomain = coaches`

### Agent Intent Input

```java
public record BookingIntent(
    BookingAction action,
    LocalDateTime dateTime,
    Integer durationMinutes,
    List<String> participantNames,
    List<String> requestedSubjects,
    String reservationId,
    Map<String, String> attributes
) {}
```

`requestedSubjects` is intentionally generic.

Examples:
- specific court names
- specific coach names
- room names
- tags or filters represented as text

### Reservation Gateway

```java
public record ReservationSubmission(
    String recipientId,
    String timezone,
    LocalDateTime dateTime,
    List<String> participants,
    Set<String> resourceIds
) {}
```

```java
public interface ReservationGateway {
    BookingSubmission submit(ReservationSubmission submission);
    CancelResult cancel(String reservationId);
    ReservationDetails get(String reservationId);
}
```

This hides the current Akka reservation core behind a stable application-facing interface.

### Workflow / Use Case Handlers

```java
public interface BookingWorkflow {
    String domain();

    AvailabilityResult checkAvailability(
        OriginRequestContext origin,
        BookingContext context,
        BookingIntent intent);

    BookingSubmission book(
        OriginRequestContext origin,
        BookingContext context,
        BookingIntent intent);

    CancelResult cancel(
        OriginRequestContext origin,
        BookingContext context,
        CancelIntent intent);
}
```

Implementations could include:
- `CourtBookingWorkflow`
- `CoachBookingWorkflow`
- `CoachAndCourtBookingWorkflow`

### Deterministic Application Service

```java
public interface BookingApplicationService {
    AvailabilityResult checkAvailability(OriginRequestContext origin, BookingIntent intent);
    BookingSubmission book(OriginRequestContext origin, BookingIntent intent);
    CancelResult cancel(OriginRequestContext origin, CancelIntent intent);
}
```

This service should:
1. resolve `BookingContext`
2. select the correct `BookingWorkflow`
3. delegate the operation

## Domain-Specific Directory Interfaces

These interfaces represent data lookups outside the generic reservation core.

### Courts

```java
public record CourtBookingScope(
    String facilityId,
    String timezone,
    Set<String> reservationResourceIds,
    Map<String, String> attributes
) {}
```

```java
public interface CourtDirectory {
    CourtBookingScope resolveScope(OriginRequestContext origin, BookingContext context, BookingIntent intent);
}
```

This is where the orchestration layer can use facility-service and user/member-service.

It may look up:
- facility metadata
- candidate court resources
- membership policy
- member identity and status

### Coaches

```java
public record CoachBookingScope(
    String timezone,
    Set<String> candidateCoachResourceIds,
    Map<String, String> attributes
) {}
```

```java
public interface CoachDirectory {
    CoachBookingScope resolveScope(OriginRequestContext origin, BookingContext context, BookingIntent intent);
}
```

This is where the orchestration layer can use user/profile/catalog services to find eligible coach resources.

It may look up:
- named coach matches
- coaches in a radius
- club/organization coach pools
- coach metadata relevant to booking

### Users / Members

```java
public record MemberContext(
    String memberId,
    String displayName,
    Map<String, String> attributes
) {}
```

```java
public interface MemberDirectory {
    Optional<MemberContext> resolve(BookingContext context, OriginRequestContext origin);
}
```

This may be used by court and coach workflows alike.

Examples:
- resolve membership status
- resolve canonical full name
- resolve club member id
- enforce member-only booking rules

## Court Booking Workflow

Target flow:

1. interaction-surface adapter builds `OriginRequestContext`
2. `BookingContextResolver` resolves `bookingDomain = courts`
3. agent extracts intent and calls a high-level booking tool
4. `BookingApplicationService` dispatches to `CourtBookingWorkflow`
5. `CourtBookingWorkflow` calls:
   - `CourtDirectory`
   - `MemberDirectory` if needed
6. workflow builds a `ReservationSubmission`
7. workflow calls `ReservationGateway.submit(...)`
8. reservation core performs booking competition and locking
9. outcome is delivered through notifier / response path

This means that the current direct dependency from `BookingService` to `FacilityEntity` should disappear.

## Coach Booking Workflow

Target flow:

1. interaction-surface adapter builds `OriginRequestContext`
2. `BookingContextResolver` resolves `bookingDomain = coaches`
3. agent extracts intent and calls a high-level booking tool
4. `BookingApplicationService` dispatches to `CoachBookingWorkflow`
5. `CoachBookingWorkflow` resolves candidate coach resources via `CoachDirectory`
6. workflow builds a `ReservationSubmission`
7. workflow calls `ReservationGateway.submit(...)`

No facility is required in this model.

A coach is simply another reservation resource whose descriptive metadata lives outside the reservation core.

## Composite Workflow: Coach + Court

A request such as:

> book me a coach available within 10 km of Townville on Sunday at 10am and book a court for me

should be handled as a composite deterministic workflow, not as a chain of low-level tool calls orchestrated by the LLM.

### Why

The LLM should not own:
- booking ordering policy
- compensation
- retry logic
- partial failure handling

That logic must be deterministic and testable.

### Target Model

Use a dedicated workflow such as:
- `CoachAndCourtBookingWorkflow`

It can:
1. resolve user/member context
2. resolve candidate coach resources
3. resolve candidate court resources
4. choose booking order according to policy
5. submit first reservation
6. wait for outcome
7. submit second reservation
8. compensate the first reservation if the second fails
9. return a combined result

This is a saga-style workflow at the application/orchestration layer.

The reservation core itself remains unchanged.

## Current Code Mapping

### Keep generic and mostly unchanged

- `reservation/reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java`
- `reservation/reservation/src/main/java/com/rezhub/reservation/resource/ResourceEntity.java`
- `reservation/reservation/src/main/java/com/rezhub/reservation/actions/ReservationAction.java`
- `reservation/reservation/src/main/java/com/rezhub/reservation/actions/ResourceAction.java`
- `reservation/reservation/src/main/java/com/rezhub/reservation/api/BookingEndpoint.java`

### Shrink / re-scope

- `TelegramEndpoint`
  - should become a thin adapter that builds `OriginRequestContext`
- `TwistEndpoint`
  - same treatment
- `BookingAgent`
  - should remain an intent/dialog layer, not a workflow owner

### Split current `BookingService`

Current `BookingService` mixes:
- agent tool surface
- facility/timezone lookup
- reservation submission
- date parsing helpers

Target split:
- `BookingTools`
  - agent-facing `@FunctionTool` methods
- `BookingApplicationServiceImpl`
  - deterministic orchestration and workflow dispatch
- `ReservationGatewayAkka`
  - wrapper around current reservation entities / endpoint
- domain-specific workflows
  - `CourtBookingWorkflow`
  - `CoachBookingWorkflow`
  - later `CoachAndCourtBookingWorkflow`
- supporting directories
  - `CourtDirectoryAkka` now, remote facility-service client later
  - `MemberDirectory...`
  - `CoachDirectory...`

## Migration Sequence

### Phase 1: Introduce stable orchestration interfaces

Add:
- `OriginRequestContext`
- `BookingContext`
- `BookingContextResolver`
- `ReservationGateway`
- `BookingApplicationService`
- `BookingWorkflow`

No behavior change yet.

### Phase 2: Wrap current reservation core

Implement `ReservationGatewayAkka` over current Akka entities / endpoints.

The goal is to make the reservation core an explicit dependency of the application layer.

### Phase 3: Split current `BookingService`

Create:
- `BookingTools`
- `BookingApplicationServiceImpl`

Move all deterministic facility/resource lookup logic out of agent-facing tool methods.

### Phase 4: Introduce `CourtBookingWorkflow`

Move current court-specific logic behind:
- `CourtDirectory`
- `MemberDirectory`
- `CourtBookingWorkflow`

Initially these can still read local Akka views/entities.

### Phase 5: Thin the interaction-surface adapters

Change `TelegramEndpoint` and `TwistEndpoint` so they only:
- parse payloads
- create `OriginRequestContext`
- call the conversation/application entrypoint

### Phase 6: Externalize facility/user ownership

Replace local Akka implementations of:
- `CourtDirectory`
- `MemberDirectory`

with service clients to the extracted facility/user service.

At that point, the reservation core should not depend directly on `FacilityEntity` or `UserEntity`.

### Phase 7: Add coach workflows

Add:
- `CoachBookingWorkflow`
- `CoachDirectory`

Later add:
- `CoachAndCourtBookingWorkflow`

## Practical Consequences

### `UserEntity`

`UserEntity` is not part of reservation correctness.

It can move out of Rez without affecting the reservation core.

User/member lookups should occur in the orchestration layer.

### `FacilityEntity`

`FacilityEntity` is currently still in the AI/courts orchestration path, but it should no longer be a direct dependency of the generic booking flow.

It should be hidden behind a directory/gateway abstraction and later moved behind a service boundary.

### Generic Nature of `reservation.Resource`

This design preserves the generic meaning of a reservation resource.

A reservation resource may be:
- a court
- a coach
- a room
- a seat
- any other bookable unit

External metadata can remain in the owning bounded context.

Rez only needs the booking-relevant representation and stable identifiers.

## Open Questions

1. Should a single service own both facility and user/member metadata, or should those remain separate services?
2. How should `BookingContextResolver` map Telegram bots or mobile app areas to booking domains?
3. What canonical data should `MemberDirectory` return for booking rules and human display?
4. What should be the exact ordering/compensation policy in `CoachAndCourtBookingWorkflow`?
5. Should notification sending remain inside Rez during migration, or also move into a separate orchestration service?

## Recommendation Summary

The recommended target architecture is:

- interaction-surface adapters handle transport/origin concerns only
- the agent handles intent extraction and dialogue only
- the booking orchestration layer handles deterministic business composition
- the reservation core remains a generic booking engine over explicit resource ids

That architecture supports:
- courts
- coaches
- composite coach + court booking
- Telegram
- Twist
- mobile app
- web app
- future externalization of facility/user ownership
