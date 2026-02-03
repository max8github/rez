# Akka 3 Migration Plan

This document tracks the migration of the `reservation` project from Kalix SDK to Akka 3.

## Overview

The reservation system was originally built with Kalix SDK 1.3.3. Kalix has been consolidated into Akka 3, which has significant API changes. This document describes the migration strategy and progress.

## Key API Changes (Kalix → Akka 3)

### POM Structure
- **Before**: Used `spring-boot-starter-parent` with Kalix dependencies
- **After**: Use `io.akka:akka-javasdk-parent:3.5.13` as parent POM

### Application Entry Point
- **Before**: `Main.java` with `@SpringBootApplication` and `SpringApplication.run()`
- **After**: No Main class needed; use `@Setup` with `ServiceSetup` for custom initialization

### Event Sourced Entities
- **Before**: `@TypeId("...")` + `@Id("...")` annotations
- **After**: `@Component(id = "...")` annotation
- **Before**: `@EventHandler` annotation on each event handler method
- **After**: Single `applyEvent(Event)` method using pattern matching
- **Before**: HTTP annotations (`@PostMapping`, `@GetMapping`) on entity
- **After**: Separate `@HttpEndpoint` classes using `ComponentClient`

### Consumers (formerly event-subscribing Actions)
- **Before**: `extends Action` with `@Subscribe.EventSourcedEntity`
- **After**: `extends Consumer` with `@Consume.FromEventSourcedEntity`
- **Before**: `Effect<T>` with type parameter
- **After**: `Effect` without type parameter
- **Before**: `effects().forward(deferredCall)` or `effects().asyncEffect(future)`
- **After**: Call components directly with `componentClient.forEventSourcedEntity(id).method(Entity::method).invoke(params)`, return `effects().done()`

### HTTP Actions → HttpEndpoints
- **Before**: `extends Action` with `@RequestMapping`
- **After**: `@HttpEndpoint("/path")` class with `@Get`, `@Post`, etc. from `akka.javasdk.annotations.http.*`

### Timed Actions
- **Before**: `extends Action` with timer scheduling
- **After**: `extends TimedAction` with `@Component(id = "...")`

### Views
- **Before**: `@ViewId("...")` + `@Table("...")` + `extends View<RowType>`
- **After**: `@Component(id = "...")` + `extends View` with nested `TableUpdater<RowType>` class
- **Before**: `@Subscribe.EventSourcedEntity(EntityClass.class)` at method level
- **After**: `@Consume.FromEventSourcedEntity(EntityClass.class)` on nested `TableUpdater` class
- **Before**: `UpdateEffect<RowType>` return type
- **After**: `Effect<RowType>` return type in `TableUpdater`

### Key Value Entities
- **Before**: `@TypeId("...")` + `@Id("...")` + `extends ValueEntity<State>`
- **After**: `@Component(id = "...")` + `extends KeyValueEntity<State>`

### Component Client API
- **Before**: `kalixClient.forEventSourcedEntity(id).call(Entity::method).params(command)`
- **After**: `componentClient.forEventSourcedEntity(id).method(Entity::method).invoke(command)`

### Removed/Changed Classes
- `StatusCode` → Use `HttpResponses` for HTTP status codes
- `SideEffect` → Different pattern in Akka 3
- `DeferredCallResponseException` → Handle differently

---

## Migration Progress

### Phase 1: POM Restructure ✅ DONE
- [x] Update parent POM to use `akka-javasdk-parent`
- [x] Update all module POMs to reference new parent (`reservation-parent`)
- [x] Add Spring dependencies where needed for DI
- [x] Add lombok dependency where needed

### Phase 2: Entry Point ✅ DONE
- [x] Remove `Main.java`
- [x] Create `Bootstrap.java` with `@Setup` and `ServiceSetup`

### Phase 3: Consumers ⏳ IN PROGRESS
- [x] `ReservationAction` → Consumer pattern
- [ ] `ResourceAction` → Consumer pattern
- [ ] `FacilityAction` → Consumer pattern + update `broadcast()` signature
- [ ] `DelegatingServiceAction` → Consumer pattern (already partially done)

### Phase 4: HTTP Actions → Endpoints ⏳ TODO
- [ ] `RezAction` → HttpEndpoint
- [ ] `WebhookAction` → HttpEndpoint
- [ ] `TimerAction` → TimedAction

### Phase 5: Entities ⏳ TODO
- [ ] `ReservationEntity`:
  - [ ] Remove HTTP annotations
  - [ ] Replace `@EventHandler` with `applyEvent()`
  - [ ] Replace `StatusCode` usage
- [ ] `ResourceEntity`:
  - [ ] Remove HTTP annotations
  - [ ] Replace `@EventHandler` with `applyEvent()`
  - [ ] Replace `StatusCode` usage
- [ ] `FacilityEntity`:
  - [ ] Remove HTTP annotations
  - [ ] Replace `@EventHandler` with `applyEvent()`
  - [ ] Replace `StatusCode` usage
- [ ] `UserEntity`:
  - [ ] Convert `ValueEntity` → `KeyValueEntity`
  - [ ] Update annotations

### Phase 6: HTTP Endpoints (Extract from Entities) ⏳ TODO
- [ ] Create `ReservationEndpoint` for reservation HTTP operations
- [ ] Create `ResourceEndpoint` for resource HTTP operations
- [ ] Create `FacilityEndpoint` for facility HTTP operations
- [ ] Create `UserEndpoint` for user HTTP operations

### Phase 7: Views ⏳ TODO
- [ ] `FacilityByNameView`:
  - [ ] Change to `@Component(id="...")`
  - [ ] Add nested `TableUpdater` class
  - [ ] Update event handlers
- [ ] `ResourceView`:
  - [ ] Change to `@Component(id="...")`
  - [ ] Add nested `TableUpdater` class
  - [ ] Update event handlers

### Phase 8: State Classes ⏳ TODO
- [ ] `ReservationState` - Fix lombok `@With` issue
- [ ] `FacilityState` - Fix lombok `@With` issue
- [ ] `FacilityV` - Fix lombok `@With` issue

### Phase 9: Testing & Verification ⏳ TODO
- [ ] Update test classes for new APIs
- [ ] Verify compilation
- [ ] Run tests

---

## Files Reference

### Entities (need EventHandler → applyEvent conversion + HTTP removal)
- `reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java`
- `reservation/src/main/java/com/rezhub/reservation/resource/ResourceEntity.java`
- `reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityEntity.java`
- `reservation/src/main/java/com/rezhub/reservation/customer/user/UserEntity.java`

### Actions (need Consumer/Endpoint/TimedAction conversion)
- `reservation/src/main/java/com/rezhub/reservation/actions/ReservationAction.java` ✅ Done
- `reservation/src/main/java/com/rezhub/reservation/actions/ResourceAction.java`
- `reservation/src/main/java/com/rezhub/reservation/actions/RezAction.java`
- `reservation/src/main/java/com/rezhub/reservation/actions/WebhookAction.java`
- `reservation/src/main/java/com/rezhub/reservation/actions/TimerAction.java`
- `reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityAction.java`
- `reservation/src/main/java/com/rezhub/reservation/reservation/DelegatingServiceAction.java`

### Views (need @Component + TableUpdater pattern)
- `reservation/src/main/java/com/rezhub/reservation/view/FacilityByNameView.java`
- `reservation/src/main/java/com/rezhub/reservation/resource/ResourceView.java`

### State Classes (need lombok @With fix)
- `reservation/src/main/java/com/rezhub/reservation/reservation/ReservationState.java`
- `reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityState.java`
- `reservation/src/main/java/com/rezhub/reservation/view/FacilityV.java`

---

## Reference Documentation

The Akka 3 documentation is available in `/Users/max/code/rez/akka-context/`:
- `sdk/ai-coding-assistant-guidelines.html.md` - Coding conventions
- `sdk/event-sourced-entities.html.md` - Entity patterns
- `sdk/consuming-producing.html.md` - Consumer patterns
- `sdk/views.html.md` - View patterns
- `sdk/http-endpoints.html.md` - HTTP Endpoint patterns
- `sdk/timed-actions.html.md` - Timed Action patterns
- `sdk/setup-and-dependency-injection.html.md` - DI and Setup

Sample projects for reference:
- `/Users/max/code/rez/shopping-cart-quickstart/` - Basic Event Sourced Entity + Endpoint
- `/Users/max/code/rez/helloworld-agent/` - Simple Akka 3 project structure
- `/Users/max/code/rez/multi-agent/` - Complex example with multiple components
