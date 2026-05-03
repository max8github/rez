# Booking Sequence Diagrams

Two outside-in flows showing how booking requests enter Rez and how outcomes are routed back to the origin system.

These diagrams intentionally hide the internal reservation competition and locking mechanics inside Rez core. They focus on:
- entrypoints and orchestration around Rez core
- what is submitted into Rez
- what comes back out and how it reaches the caller

---

## 1 — Telegram court reservation

```mermaid
sequenceDiagram
    actor User
    participant TG as Telegram Bot API
    participant TE as TelegramEndpoint
    participant FV as FacilityByBotTokenView
    participant BA as BookingAgent (LLM)
    participant BT as BookingTools
    participant BAS as BookingAppServiceImpl
    participant BCR as BookingContextResolver
    participant CD as CourtDirectory
    participant RGW as ReservationGateway
    participant CORE as Rez Core
    participant DSA as DelegatingServiceAction
    participant NS as NotificationSender (TelegramNotifier)

    User->>TG: "Book court tomorrow at 10"
    TG->>TE: POST /{botToken}/webhook

    TE->>FV: getByBotToken(botToken)
    FV-->>TE: facilityId, timezone

    Note over TE: builds OriginRequestContext<br/>origin="telegram"<br/>recipientId = botToken:chatId

    TE--)BA: chat(AgentRequest{origin, text})  [invokeAsync]
    TE-->>TG: 200 OK immediately

    Note over BA: LLM decides to call bookCourt tool
    BA->>BT: bookCourt(facilityId, dateTime, players, recipientId)
    BT->>BAS: book(origin, intent)
    BAS->>BCR: resolve(origin)
    BCR-->>BAS: bookingDomain="courts", facilityId, timezone
    BAS->>CD: resolveScope(facilityId)
    CD-->>BAS: candidate resourceIds
    BAS->>RGW: submit(reservationId, recipientId, resourceIds, dateTime)
    RGW->>CORE: init reservation
    CORE-->>RGW: ReservationHandle(reservationId)
    RGW-->>BAS: ReservationHandle(reservationId)
    BAS-->>BT: "BOOKING_SUBMITTED:abc123"
    BT-->>BA: "BOOKING_SUBMITTED:abc123"
    Note over BA: LLM replies "__SILENT_BOOKING_ACK__"
    BA-->>TE: "__SILENT_BOOKING_ACK__"
    Note over TE: isInterimBookingAcknowledgement() → true<br/>Telegram reply suppressed

    Note over CORE: runs async booking internally<br/>against candidate resources

    alt Court available
        CORE-->>DSA: booking fulfilled(reservationId, resourceId, recipientId)
        DSA->>NS: send(recipientId=botToken:chatId, "🎾 Court booked…")
        NS->>TG: POST bot/sendMessage(chatId, text)
        TG->>User: "🎾 Court 1 – Thu 10 May, 10:00 ✅"
    else No court available
        CORE-->>DSA: booking rejected / search exhausted(reservationId, recipientId)
        DSA->>NS: send(recipientId=botToken:chatId, "Sorry, no court available…")
        NS->>TG: POST bot/sendMessage(chatId, text)
        TG->>User: "Sorry, no court was available. Please try a different time."
    end
```

Note:
- The inbound interaction surface is Telegram, but the current tool/orchestration path rebuilds the booking request before reservation submission. So "entered via Telegram" and "submitted into Rez core with the same origin tag" are not exactly the same thing in today's code.

---

## 2 — Hit app supplier booking

```mermaid
sequenceDiagram
    actor P as Player (Mobile App)
    participant SE as SessionEndpoint
    participant SW as SessionWorkflow
    participant RC as RezClient
    participant BE as Rez: BookingEndpoint
    participant CORE as Rez Core
    participant DSA as Rez: DelegatingServiceAction
    participant ROP as ReservationOutcomeProducer
    participant RBC as RezBookingConsumer (hit-backend)
    participant CA as CreditAccountEntity
    participant NS as NotificationService (APNs/FCM)

    P->>SE: POST /sessions/ {initiatorId, respondentId, scheduledAt, …}
    Note over SE: loads players, device tokens,<br/>and supplierRezResourceId
    SE->>SW: startRequest(StartRequestCommand{…, supplierRezResourceId, initiatorDeviceToken, …})
    SE-->>P: 200 {sessionId}

    Note over SW: transitions → fireRezBookingStep
    SW->>RC: book(reservationId=sessionId+"-supplier", {supplierRezResourceId}, scheduledAt)
    RC->>BE: POST /bookings {reservationId, resourceIds, recipientId=sessionId, originSystem="hit"}
    BE->>CORE: init reservation(resourceIds, dateTime, recipientId, originSystem="hit")
    CORE-->>BE: status=COLLECTING
    BE-->>RC: 200 {reservationId, status=COLLECTING}
    Note over SW: pauses — waiting for async outcome

    Note over CORE: runs async booking internally<br/>against supplier resource(s)
    Note over DSA: skips user notifications<br/>when originSystem="hit"

    alt Supplier available
        CORE-->>ROP: fulfilled(reservationId, resourceId, originSystem="hit")
        ROP-)RBC: ReservationOutcomeEvent.Fulfilled → "reservation-outcomes" stream
        RBC->>SW: rezBookingConfirmed(reservationId, resourceId)
        Note over SW: transitions → placeCreditHoldStep
        SW->>CA: placeHold(sessionId, holdAmount)
        CA-->>SW: Done
        Note over SW: transitions → notifyPartiesStep
        SW->>NS: sendToToken(initiatorDeviceToken, "Session booked")
        SW->>NS: sendToToken(respondentDeviceToken, "New session booked")
        NS-->>P: Push notification ✅
        Note over SW: pauses in BOOKED state

    else Supplier unavailable
        CORE-->>ROP: rejected / search exhausted(reservationId, originSystem="hit")
        ROP-)RBC: ReservationOutcomeEvent.Rejected → "reservation-outcomes" stream
        RBC->>SW: rezBookingRejected(reservationId)
        Note over SW: transitions → rezRejectedStep
        SW->>NS: sendToToken(initiatorDeviceToken, "Booking unavailable")
        NS-->>P: Push notification ✅  [BUG-001 fixed]
        Note over SW: status → REJECTED, workflow ends
    end
```

---

## B-047 change map

| Step | Repo | Component | Change |
|------|------|-----------|--------|
| a | rez | `BookingEndpoint.BookingRequest` | add `String originSystem` (nullable) |
| a | rez | `ReservationEntity.Init` | add `String originSystem` |
| a | rez | `ReservationState` | store `originSystem` |
| a | rez | `ReservationEvent` variants | carry `originSystem` in Fulfilled, SearchExhausted, Rejected |
| a | rez | `ReservationOutcomeEvent` | add `String originSystem` to Fulfilled and Rejected |
| a | rez | `ReservationOutcomeProducer` | propagate `originSystem` into outcome events |
| a | rez | `DelegatingServiceAction` | skip if `originSystem != null && !originSystem.equals("telegram")` |
| b | hit-backend | `RezClient` | pass `originSystem = "hit"` |
| c | hit-backend | `RezOutcomeEvent` | add `String originSystem` |
| d | hit-backend | `RezBookingConsumer` | skip if `originSystem != null && !originSystem.equals("hit")` |
| BUG-001 | hit-backend | `SessionWorkflow.rezRejectedStep` | send push notification before `thenEnd()` |
