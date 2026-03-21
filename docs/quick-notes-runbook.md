This is my quick file before the demo that keeps commands to copy/paste.

For cloud deployment steps see [docs/deployment.md](deployment.md).

# CLI - LOCAL

Provision facility and courts for local test

```shell
cd /Users/max/code/rez/reservation/reservation
mvn exec:java -Plocal
```

```shell
curl -s -X POST http://localhost:9000/facility/test \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Club","address":{"street":"Test St 1","city":"12345 Test"}}'

curl -s -X POST http://localhost:9000/facility/test/resource/court-1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Court 1"}'

curl -s -X POST http://localhost:9000/facility/test/resource/court-2 \
  -H "Content-Type: application/json" \
  -d '{"name":"Court 2"}'
```

Then use f_test as the facility_id in the agent messages (that's what gets stored internally).

Book with Matrix:
```shell
curl -s -X POST http://localhost:9000/matrix/message \
  -H "Content-Type: application/json" \
  -d '{"facility_id":"f_test","sender":"@max:local","sender_name":"Max","message":"Book a court tomorrow at 10am for Max and Anna"}'
```

Check the court was booked:
```shell
curl -s http://localhost:9000/facility/test

curl -s http://localhost:9000/resource/r_court-1


curl -s http://localhost:9000/facility/f_test/reservations

curl -s http://localhost:9000/facility/f_test/resource/r_court-1/reservations

```

Book with Telegram:
```shell
curl -s -X POST http://localhost:9000/telegram/webhook \
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