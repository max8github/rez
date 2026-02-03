
# Akka 3 Migration Plan - Phased Approach


## Overview

Migrate the reservation project from Kalix SDK to Akka 3 using a **dependency-aware vertical approach** that enables testing at each phase.

## Migration Phases

### Phase 1: HTTP Entry Points (Enable Testing)

Convert external-facing HTTP handlers first so the system can accept requests during migration.

**Files to modify:**

1. `reservation/src/main/java/com/rezhub/reservation/actions/RezAction.java`
    - Convert `extends Action` → `@HttpEndpoint("/selection")`
    - Update HTTP annotations from Spring to Akka
2. `reservation/src/main/java/com/rezhub/reservation/actions/WebhookAction.java`
    - Convert `extends Action` → `@HttpEndpoint("/outwebhook")`
    - Replace deprecated `SideEffect` pattern
3. `reservation/src/main/java/com/rezhub/reservation/actions/TimerAction.java`
    - Convert `extends Action` → `extends TimedAction` with `@Component(id="...")`
    - Update exception handling

### Phase 2: Reservation Slice (Core Logic)

Migrate the central reservation entity and its supporting components.

**Files to modify:**

1. `reservation/src/main/java/com/rezhub/reservation/reservation/ReservationEntity.java`
    - Remove HTTP annotations (`@RequestMapping`, `@PostMapping`, etc.)
    - Convert 8 `@EventHandler` methods → single `applyEvent()` method
    - Replace `StatusCode.ErrorCode` with exception handling
2. `reservation/src/main/java/com/rezhub/reservation/reservation/ReservationState.java`
    - Fix lombok `@With` annotation issue
3. **NEW FILE**: `reservation/src/main/java/com/rezhub/reservation/api/ReservationEndpoint.java`
    - Create `@HttpEndpoint("/reservation")`
    - Extract HTTP methods from entity, use `ComponentClient` to call entity

### Phase 3: Resource Slice

Migrate resource entity and its complex consumer.

**Files to modify:**

1. `reservation/src/main/java/com/rezhub/reservation/resource/ResourceEntity.java`
    - Remove HTTP annotations
    - Convert 6 `@EventHandler` methods → `applyEvent()`
    - Replace `StatusCode.ErrorCode`
2. `reservation/src/main/java/com/rezhub/reservation/actions/ResourceAction.java`
    - Already has `@Consume.FromEventSourcedEntity` but extends wrong class
    - Convert to proper Consumer pattern
    - Fix timer cancellation in async chain (complex)
3. **NEW FILE**: `reservation/src/main/java/com/rezhub/reservation/api/ResourceEndpoint.java`
    - Extract HTTP methods from entity

### Phase 4: Facility Slice

Migrate facility entity and fix broadcast method.

**Files to modify:**

1. `reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityEntity.java`
    - Remove HTTP annotations
    - Convert 7 `@EventHandler` methods → `applyEvent()`
    - Replace `StatusCode.ErrorCode`
2. `reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityAction.java`
    - Fix `broadcast()` method signature to use new `componentClient.method().invoke()` API
    - This cascades to ReservationAction which calls it
3. `reservation/src/main/java/com/rezhub/reservation/customer/facility/FacilityState.java`
    - Fix lombok `@With` issue
4. **NEW FILE**: `reservation/src/main/java/com/rezhub/reservation/api/FacilityEndpoint.java`
    - Extract HTTP methods from entity

### Phase 5: User Entity

Simple migration with no dependencies.

**Files to modify:**

1. `reservation/src/main/java/com/rezhub/reservation/customer/user/UserEntity.java`
    - Change `extends ValueEntity` → `extends KeyValueEntity`
    - Replace `@TypeId` + `@Id` → `@Component(id="...")`
    - Remove HTTP annotations
2. **NEW FILE**: `reservation/src/main/java/com/rezhub/reservation/api/UserEndpoint.java`
    - Extract HTTP methods

### Phase 6: Views

Migrate read-only projections after entities are stable.

**Files to modify:**

1. `reservation/src/main/java/com/rezhub/reservation/view/FacilityByNameView.java`
    - Replace `@ViewId` → `@Component(id="...")`
    - Add nested `TableUpdater` class
    - Convert `UpdateEffect<T>` → `Effect<T>`
2. `reservation/src/main/java/com/rezhub/reservation/resource/ResourceView.java`
    - Same pattern as above
3. `reservation/src/main/java/com/rezhub/reservation/view/FacilityV.java`
    - Fix lombok `@With` issue

### Phase 7: Cleanup

Final touches and verification.

1. `reservation/src/main/java/com/rezhub/reservation/reservation/DelegatingServiceAction.java`
    - Verify Consumer pattern is correct
    - May need minor adjustments
2. Update any remaining imports and remove unused dependencies

## Verification Steps

After each phase:

1. Run `mvn compile` to verify compilation
2. Check for import errors and missing symbols
3. Commit checkpoint with descriptive message

Final verification:

1. `mvn clean compile` - Full clean build
2. `mvn test` - Run unit tests
3. Start the service locally and test HTTP endpoints

## Key API Transformations

| Before (Kalix) | After (Akka 3) |
| --- | --- |
| `@EventHandler` | `applyEvent(Event e)` with pattern matching |
| `StatusCode.ErrorCode.BAD_REQUEST` | `effects().error("message")` |
| `extends Action` + `@Subscribe` | `extends Consumer` + `@Consume.FromEventSourcedEntity` |
| `extends Action` + `@RequestMapping` | `@HttpEndpoint` class |
| `kalixClient.call(Entity::method).params(x)` | `componentClient.method(Entity::method).invoke(x)` |
| `Effect<T>` in Consumer | `Effect` (no type parameter) |
| `@ViewId` + `@Table` | `@Component(id="...")` + nested `TableUpdater` |

## Estimated Effort per Phase

| Phase | Components | Est. Time |
| --- | --- | --- |
| 1 | HTTP Entry Points | 1.5 hours |
| 2 | Reservation Slice | 1.5 hours |
| 3 | Resource Slice | 2 hours |
| 4 | Facility Slice | 1.5 hours |
| 5 | User Entity | 30 min |
| 6 | Views | 1 hour |
| 7 | Cleanup | 30 min |
| **Total** |  | **~8.5 hours** |

## Recommendation

Execute one phase at a time with commit checkpoints. This allows:

- Incremental progress tracking
- Easy rollback if issues arise
- Testing at each stage
- Clear git history of changes