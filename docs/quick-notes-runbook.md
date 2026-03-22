This is my quick file before the demo that keeps commands to copy/paste.

For cloud deployment steps see [docs/deployment.md](deployment.md).

# CLI - LOCAL

Start local (stub calendar + notifier, no external API calls):

```shell
cd /Users/max/code/rez/reservation/reservation
mvn compile exec:java -Plocal
```

## Provision facility and courts

Create facility — returns a bare UUID (the `f_` prefix is added internally):

```shell
FACILITY_ID=$(curl -s -X POST http://localhost:9000/facility \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Club",
    "address": {"street": "Test St 1", "city": "12345 Test"},
    "timezone": "Europe/Berlin",
    "botToken": "bot:local-test",
    "adminUserIds": ["123456"]
  }')
echo "Facility ID: $FACILITY_ID"
```

Create courts — each returns a bare UUID for the resource:

```shell
COURT1=$(curl -s -X POST http://localhost:9000/facility/$FACILITY_ID/resource \
  -H "Content-Type: application/json" \
  -d '{"name": "Court 1", "calendarId": "local-cal-1@group.calendar.google.com"}')
echo "Court 1 ID: $COURT1"

COURT2=$(curl -s -X POST http://localhost:9000/facility/$FACILITY_ID/resource \
  -H "Content-Type: application/json" \
  -d '{"name": "Court 2", "calendarId": "local-cal-2@group.calendar.google.com"}')
echo "Court 2 ID: $COURT2"
```

Verify facility state:

```shell
curl -s http://localhost:9000/facility/$FACILITY_ID
```

## Book with Telegram

The bot token in the path must match what was stored on the facility:

```shell
curl -s -X POST "http://localhost:9000/telegram/bot:local-test/webhook" \
  -H "Content-Type: application/json" \
  -d '{
    "message": {
      "message_id": 1,
      "from": {"id": 123456, "first_name": "Max", "username": "max"},
      "chat": {"id": 123456, "type": "private"},
      "text": "Book a court tomorrow at 10am for Max and Anna"
    }
  }'
```

Wrong/unknown bot token (should log a warning and return 200 with no action):

```shell
curl -s -X POST "http://localhost:9000/telegram/bot:unknown/webhook" \
  -H "Content-Type: application/json" \
  -d '{
    "message": {
      "message_id": 2,
      "from": {"id": 999, "first_name": "Ghost", "username": "ghost"},
      "chat": {"id": 999, "type": "private"},
      "text": "Book something"
    }
  }'
```

## Book with Matrix (still uses facilityId directly)

```shell
curl -s -X POST http://localhost:9000/matrix/message \
  -H "Content-Type: application/json" \
  -d "{\"facility_id\":\"$FACILITY_ID\",\"sender\":\"@max:local\",\"sender_name\":\"Max\",\"message\":\"Book a court tomorrow at 10am for Max and Anna\"}"
```
The above will produce, say, reservation `7bcdf6d0`.

### Cancel the reservation

```shell
curl -s -X POST "http://localhost:9000/telegram/bot:local-test/webhook" \
  -H "Content-Type: application/json" \
  -d '{
    "message": {
      "message_id": 2,
      "from": {"id": 123456, "first_name": "Max", "username": "max"},
      "chat": {"id": 123456, "type": "private"},
      "text": "Cancel reservation 7bcdf6d0"
    }
  }'
```

## Check bookings

```shell
# All reservations for the facility
curl -s http://localhost:9000/facility/$FACILITY_ID/reservations

# Resource view (calendarId should be present)
curl -s http://localhost:9000/resource/$COURT1
```
