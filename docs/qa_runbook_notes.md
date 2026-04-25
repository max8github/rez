# Terminal 1: Rez
```shell
cd /Users/max/code/rez/reservation
mvn clean install

export OPENAI_API_KEY=...
mvn -pl reservation -am compile exec:java -Plocal
```

# Terminal 2: QA
## Create Facility And Courts
```shell
PORT=9001
BASE_URL="http://localhost:$PORT"

FACILITY_ID=$(curl -s -X POST "$BASE_URL/facility" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Club",
    "address": {"street": "Test St 1", "city": "12345 Test"},
    "timezone": "Europe/Berlin",
    "botToken": "bot:local-test",
    "adminUserIds": ["123456"]
  }')
echo "$FACILITY_ID"

COURT1="court-1"
curl -s -X POST "$BASE_URL/resource/$COURT1" \
  -H "Content-Type: application/json" \
  -d '{
    "resourceId": "court-1",
    "resourceName": "Court 1",
    "calendarId": "local-cal-1@group.calendar.google.com"
  }'
curl -s -X PUT "$BASE_URL/resource/$COURT1/external-ref" \
  -H "Content-Type: application/json" \
  -d "{
    \"externalRef\": \"$COURT1\",
    \"externalGroupRef\": \"$FACILITY_ID\"
  }"
echo "$COURT1"

COURT2="court-2"
curl -s -X POST "$BASE_URL/resource/$COURT2" \
  -H "Content-Type: application/json" \
  -d '{
    "resourceId": "court-2",
    "resourceName": "Court 2",
    "calendarId": "local-cal-2@group.calendar.google.com"
  }'
curl -s -X PUT "$BASE_URL/resource/$COURT2/external-ref" \
  -H "Content-Type: application/json" \
  -d "{
    \"externalRef\": \"$COURT2\",
    \"externalGroupRef\": \"$FACILITY_ID\"
  }"
echo "$COURT2"

# test
curl -s "$BASE_URL/facility/$FACILITY_ID"
curl -s "$BASE_URL/resource/$COURT1" | jq
curl -s "$BASE_URL/resource/$COURT2" | jq
```

## Create And Cancel Reservations Through AI Agent

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

curl -s "$BASE_URL/resource/$COURT1" | jq
curl -s "$BASE_URL/resource/$COURT2" | jq

curl -s "$BASE_URL/reservation-lookup/recipient/bot:local-test:123456/latest" | jq

# cancel:
curl -s -X POST "$BASE_URL/telegram/bot:local-test/webhook" \
  -H "Content-Type: application/json" \
  -d '{
    "message": {
      "message_id": 2,
      "from": {"id": 123456, "first_name": "Max", "username": "max"},
      "chat": {"id": 123456, "type": "private"},
      "text": "Cancel reservation 87c24cf9"
    }
  }'
```
