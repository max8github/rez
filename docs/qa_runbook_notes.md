
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
FACILITY_ID=$(curl -s -X POST http://localhost:9000/facility \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Club",
    "address": {"street": "Test St 1", "city": "12345 Test"},
    "timezone": "Europe/Berlin",
    "botToken": "bot:local-test",
    "adminUserIds": ["123456"]
  }')
echo "$FACILITY_ID"

COURT1=$(curl -s -X POST http://localhost:9000/facility/$FACILITY_ID/resource \
  -H "Content-Type: application/json" \
  -d '{"name": "Court 1", "calendarId": "local-cal-1@group.calendar.google.com"}')
echo "$COURT1"

COURT2=$(curl -s -X POST http://localhost:9000/facility/$FACILITY_ID/resource \
  -H "Content-Type: application/json" \
  -d '{"name": "Court 2", "calendarId": "local-cal-2@group.calendar.google.com"}')
echo "$COURT2"

#test
curl -s http://localhost:9000/facility/$FACILITY_ID
curl -s http://localhost:9000/resource/$COURT1 | jq
```

## Create and Cancel Reservations through AI Agent

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

curl -s http://localhost:9000/resource/$COURT1 | jq

curl -s http://localhost:9000/resource/$COURT2 | jq


# cancel:
curl -s -X POST "http://localhost:9000/telegram/bot:local-test/webhook" \
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