This is my quick file before the demo that keeps commands to copy/paste.

For cloud deployment steps see [docs/deployment.md](deployment.md).

# CLI - LOCAL

Start local (stub calendar + notifier, no external API calls):

```shell
export REZ_PORT=9001
cd /Users/max/code/rez/reservation/reservation
mvn compile exec:java -Plocal
```

Set the local base URL once for all commands below:

```shell
PORT="${REZ_PORT:-9001}"
BASE_URL="http://localhost:$PORT"
```

## Provision facility and courts

Create facility — returns a bare UUID:

```shell
FACILITY_ID=$(curl -s -X POST "$BASE_URL/facility" \
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

Create courts in two steps — create the resource, then attach it to the facility via `external-ref`:

```shell
COURT1="court-1"
curl -s -X POST "$BASE_URL/resource/$COURT1" \
  -H "Content-Type: application/json" \
  -d '{
    "resourceId": "court-1",
    "resourceName": "Court 1",
    "calendarId": ""
  }'
curl -s -X PUT "$BASE_URL/resource/$COURT1/external-ref" \
  -H "Content-Type: application/json" \
  -d "{
    \"externalRef\": \"$COURT1\",
    \"externalGroupRef\": \"$FACILITY_ID\"
  }"
echo "Court 1 ID: $COURT1"

COURT2="court-2"
curl -s -X POST "$BASE_URL/resource/$COURT2" \
  -H "Content-Type: application/json" \
  -d '{
    "resourceId": "court-2",
    "resourceName": "Court 2",
    "calendarId": ""
  }'
curl -s -X PUT "$BASE_URL/resource/$COURT2/external-ref" \
  -H "Content-Type: application/json" \
  -d "{
    \"externalRef\": \"$COURT2\",
    \"externalGroupRef\": \"$FACILITY_ID\"
  }"
echo "Court 2 ID: $COURT2"
```

`calendarId` is optional metadata. Leave it empty unless you explicitly want Rez to keep a Google Calendar identifier for reservation-detail links.

Verify facility and resource state:

```shell
curl -s "$BASE_URL/facility/$FACILITY_ID"
curl -s "$BASE_URL/resource/$COURT1"
curl -s "$BASE_URL/resource/$COURT2"
```

## Book with Telegram

The bot token in the path must match what was stored on the facility:

```shell
curl -s -X POST "$BASE_URL/telegram/bot:local-test/webhook" \
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
curl -s -X POST "$BASE_URL/telegram/bot:unknown/webhook" \
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
curl -s -X POST "$BASE_URL/matrix/message" \
  -H "Content-Type: application/json" \
  -d "{\"facility_id\":\"$FACILITY_ID\",\"sender\":\"@max:local\",\"sender_name\":\"Max\",\"message\":\"Book a court tomorrow at 10am for Max and Anna\"}"
```

### Cancel the reservation

```shell
curl -s "$BASE_URL/reservation-lookup/recipient/bot:local-test:123456/latest"

curl -s -X POST "$BASE_URL/telegram/bot:local-test/webhook" \
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
curl -s "$BASE_URL/bookings/7bcdf6d0"
curl -s "$BASE_URL/reservation-lookup/recipient/bot:local-test:123456/latest"
curl -s "$BASE_URL/resource/$COURT1"
```
