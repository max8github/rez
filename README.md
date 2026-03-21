# Summary

This is a sophisticated event-sourced distributed reservation system that:

1. Accepts natural language booking requests via Twist chat
2. Broadcasts availability queries across hierarchical facility/resource structures
3. Intelligently selects resources using FSM-based orchestration with automatic fallback
4. Synchronizes with Google Calendar and sends confirmations back to Twist
5. Handles timeouts, rejections, and cancellations gracefully

See [docs/deployment.md](docs/deployment.md) for Akka Cloud deployment instructions.

The architecture showcases advanced Kalix patterns:
- Event Sourcing for audit trail and replay
- Saga orchestration across multiple entities
- Async fan-out with polymorphic dispatch
- Plugin architecture via SPIs
- CQRS with Views for queries
