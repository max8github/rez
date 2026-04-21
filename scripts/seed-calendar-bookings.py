#!/usr/bin/env python3

import json
import random
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta

BASE = "http://localhost:9000"


def post(path, payload):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{e.code} {e.reason} on {path}: {body}")


def get_json(path):
    with urllib.request.urlopen(BASE + path) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main():
    bot_token = f"bot:local-seed-{int(time.time())}"

    facility_payload = {
        "name": "Calendar Seed Club",
        "address": {"street": "Via Roma 1", "city": "Rome"},
        "timezone": "Europe/Rome",
        "botToken": bot_token,
    }
    facility_id = post("/facility/", facility_payload).strip().strip('"')
    print("FACILITY_ID =", facility_id)
    print("BOT_TOKEN   =", bot_token)

    court_names = ["Center Court", "Court 2"]
    for name in court_names:
        calendar_id = name.lower().replace(" ", "-") + "@example.test"
        resource_payload = {"name": name, "calendarId": calendar_id}
        resource_id = post(f"/facility/{facility_id}/resource", resource_payload).strip().strip('"')
        print("RESOURCE REQUESTED =", resource_id, name)

    print("Waiting for facility resourceIds to contain both courts...")

    for i in range(120):
        facility = get_json(f"/facility/{facility_id}")
        resource_ids = facility.get("resourceIds") or []
        print(f"  poll {i + 1:02d}: {len(resource_ids)} resourceIds")
        if len(resource_ids) == 2:
            print("Facility resourceIds are ready:", resource_ids)
            time.sleep(2.0)
            break
        time.sleep(0.5)
    else:
        raise RuntimeError("Facility did not finish registering both resources in time")

    players_pool = [
        "Alice", "Bob", "Max", "John", "Sara", "Leo", "Ana", "Tom",
        "Marta", "Luca", "Elena", "Paolo", "Sofia", "Marco", "Giulia", "Nico",
    ]

    random.seed(42)
    day_offsets = list(range(1, 13))
    hours = [9, 10, 11, 12, 17, 18, 19, 20]

    used = set()
    created = 0
    attempts = 0

    while created < 40 and attempts < 800:
        attempts += 1
        day_offset = random.choice(day_offsets)
        hour = random.choice(hours)

        dt = (datetime.now().replace(minute=0, second=0, microsecond=0) + timedelta(days=day_offset))
        dt = dt.replace(hour=hour)

        slot_key = dt.isoformat(timespec="seconds")
        court_index = random.choice([0, 1])

        if (court_index, slot_key) in used:
            continue
        used.add((court_index, slot_key))

        players = random.sample(players_pool, 2)
        reservation_id = f"seed{created + 1:02d}{random.randint(1000, 9999)}"

        payload = {
            "reservation": {
                "emails": players,
                "dateTime": slot_key,
            },
            "selection": [
                {
                    "id": facility_id,
                    "type": "FACILITY",
                }
            ],
            "recipientId": "local-seed-user",
        }

        try:
            post(f"/selection/{reservation_id}", payload)
            created += 1
            print(f"BOOKED {created:02d} {reservation_id} {slot_key} {players}")
            time.sleep(0.05)
        except Exception as e:
            print("FAILED", reservation_id, e)

    print()
    print(f"Submitted {created} booking requests.")
    print("Wait ~15 seconds for fulfillment/timeouts, then open:")
    print(f"{BASE}/calendar?facilityId={facility_id}")


if __name__ == "__main__":
    main()
