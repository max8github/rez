# ReservationEntity State Machine

```mermaid
stateDiagram-v2
    direction LR
    [*] --> INIT
    INIT --> COLLECTING : Initiated
    COLLECTING --> COLLECTING : (no)
    COLLECTING --> SELECTING : (yes)
    COLLECTING --> UNAVAILABLE : Rejected
    SELECTING --> SELECTING : (yes/no)
    SELECTING --> FULFILLED : Accepted
    SELECTING --> COLLECTING : Rejected
    FULFILLED --> CANCELLED
```
