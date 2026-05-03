# Booking Sequence Diagrams

Two end-to-end flows showing how booking requests enter Rez and how outcomes are routed back to the origin system.

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
    participant CBW as CourtBookingWorkflow
    participant RGW as ReservationGatewayAkka
    participant RE as ReservationEntity
    participant Rx as ResourceEntity ×N
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
    BT->>BAS: book(origin{recipientId=botToken:chatId}, intent)
    BAS->>CBW: book(origin, context, intent)
    CBW->>RGW: submit(reservationId, recipientId=botToken:chatId, resourceIds, dateTime)
    RGW->>RE: init(reservation, resourceIds, recipientId=botToken:chatId)
    RE-->>RGW: Done
    RGW-->>CBW: ReservationHandle(reservationId)
    CBW-->>BT: "BOOKING_SUBMITTED:abc123"
    BT-->>BA: "BOOKING_SUBMITTED:abc123"
    Note over BA: LLM replies "__SILENT_BOOKING_ACK__"
    BA-->>TE: "__SILENT_BOOKING_ACK__"
    Note over TE: isInterimBookingAcknowledgement() → true<br/>Telegram reply suppressed

    Note over RE,Rx: async fan-out (Akka event-driven)
    RE->>Rx: check availability at dateTime
    Rx-->>RE: available / unavailable

    alt Court available → ReservationEvent.Fulfilled
        RE->>RE: persist Fulfilled(reservationId, resourceId, recipientId=botToken:chatId)
        DSA->>RE: [consumes Fulfilled event]
        DSA->>NS: send(recipientId=botToken:chatId, "🎾 Court booked…")
        NS->>TG: POST bot/sendMessage(chatId, text)
        TG->>User: "🎾 Court 1 – Thu 10 May, 10:00 ✅"
    else No court available → ReservationEvent.SearchExhausted
        RE->>RE: persist SearchExhausted(reservationId, recipientId=botToken:chatId)
        DSA->>RE: [consumes SearchExhausted event]
        DSA->>NS: send(recipientId=botToken:chatId, "Sorry, no court available…")
        NS->>TG: POST bot/sendMessage(chatId, text)
        TG->>User: "Sorry, no court was available. Please try a different time."
    end
```

---

## 2 — Hit app supplier booking

```mermaid
sequenceDiagram
    actor P as Player (Mobile App)
    participant SE as SessionEndpoint
    participant SW as SessionWorkflow
    participant RC as RezClient
    participant BE as Rez: BookingEndpoint
    participant RE as ReservationEntity
    participant Rx as ResourceEntity (supplier)
    participant DSA as Rez: DelegatingServiceAction
    participant ROP as ReservationOutcomeProducer
    participant RBC as RezBookingConsumer (hit-backend)
    participant CA as CreditAccountEntity
    participant NS as NotificationService (APNs/FCM)

    P->>SE: POST /sessions/ {initiatorId, respondentId, scheduledAt, …}
    SE->>SW: startRequest(StartRequestCommand{…, supplierRezResourceId, initiatorDeviceToken, …})
    SW-->>SE: sessionId  [status: REQUESTED]
    SE-->>P: 200 {sessionId}

    Note over SW: transitions → fireRezBookingStep
    SW->>RC: book(reservationId=sessionId+"-supplier", {supplierRezResourceId}, scheduledAt)
    RC->>BE: POST /bookings {reservationId, resourceIds, recipientId=sessionId, originSystem="hit"}
    BE->>RE: init(reservation, resourceIds, recipientId=sessionId, originSystem="hit")
    RE-->>BE: Done
    BE-->>RC: 200
    Note over SW: pauses — waiting for async outcome

    Note over RE,Rx: async fan-out (Akka event-driven)
    RE->>Rx: check availability at scheduledAt
    Rx-->>RE: available / unavailable

    Note over DSA: B-047: DelegatingServiceAction skips<br/>when originSystem="hit" (not a Telegram booking)

    alt Supplier available → ReservationEvent.Fulfilled
        RE->>RE: persist Fulfilled(reservationId, resourceId, recipientId, originSystem="hit")
        ROP->>RE: [consumes Fulfilled]
        ROP-)RBC: ReservationOutcomeEvent.Fulfilled{originSystem="hit"} → "reservation-outcomes" stream
        RBC->>SW: rezBookingConfirmed(reservationId, resourceId)
        Note over SW: transitions → placeCreditHoldStep
        SW->>CA: placeHold(sessionId, holdAmount)
        CA-->>SW: Done
        Note over SW: transitions → notifyPartiesStep
        SW->>NS: sendToToken(initiatorDeviceToken, "Session booked")
        SW->>NS: sendToToken(respondentDeviceToken, "New session booked")
        NS-->>P: Push notification ✅
        Note over SW: pauses in BOOKED state

    else Supplier unavailable → ReservationEvent.SearchExhausted
        RE->>RE: persist SearchExhausted(reservationId, recipientId, originSystem="hit")
        ROP->>RE: [consumes SearchExhausted]
        ROP-)RBC: ReservationOutcomeEvent.Rejected{originSystem="hit"} → "reservation-outcomes" stream
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
