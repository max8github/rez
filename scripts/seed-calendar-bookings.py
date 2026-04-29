#!/usr/bin/env python3

import json
import random
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta
import os
import re

BASE = os.getenv("REZ_BASE_URL", f"http://localhost:{os.getenv('PORT', '9001')}")


def request(method, path, payload=None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        BASE + path,
        data=data,
        headers={"Content-Type": "application/json"},
        method=method,
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


def post(path, payload):
    return request("POST", path, payload)


def put(path, payload):
    return request("PUT", path, payload)


def slugify(value):
    value = re.sub(r"[^a-z0-9]+", "-", value.strip().lower()).strip("-")
    return value or "resource"


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

    court_names = ["Center Court", "Court 2", "Court 3", "Court 4"]
    resource_ids = []
    for name in court_names:
        resource_id = f"{slugify(name)}-{int(time.time())}"
        calendar_id = name.lower().replace(" ", "-") + "@example.test"
        resource_payload = {
            "resourceId": resource_id,
            "resourceName": name,
            "calendarId": calendar_id,
        }
        post(f"/resource/{resource_id}", resource_payload)
        put(
            f"/resource/{resource_id}/external-ref",
            {"externalRef": resource_id, "externalGroupRef": facility_id},
        )
        resource_ids.append(resource_id)
        print("RESOURCE READY =", resource_id, name)

    print("Waiting for both resources to become readable...")

    for i in range(120):
        ready = 0
        for resource_id in resource_ids:
            resource = get_json(f"/resource/{resource_id}")
            if resource.get("externalGroupRef") == facility_id:
                ready += 1
        print(f"  poll {i + 1:02d}: {ready}/{len(resource_ids)} resources ready")
        if ready == len(resource_ids):
            time.sleep(1.0)
            break
        time.sleep(0.5)
    else:
        raise RuntimeError("Resources did not finish attaching to the facility in time")

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
        court_index = random.randrange(len(resource_ids))

        if (court_index, slot_key) in used:
            continue
        used.add((court_index, slot_key))

        players = random.sample(players_pool, 2)
        reservation_id = f"seed{created + 1:02d}{random.randint(1000, 9999)}"

        payload = {
            "dateTime": slot_key,
            "emails": players,
            "resourceIds": [resource_ids[court_index]],
            "recipientId": "local-seed-user",
        }

        try:
            post("/bookings", {"reservationId": reservation_id, **payload})
            created += 1
            print(f"BOOKED {created:02d} {reservation_id} {slot_key} {players}")
            time.sleep(0.05)
        except Exception as e:
            print("FAILED", reservation_id, e)

    print()
    print(f"Submitted {created} booking requests.")
    print("Wait a few seconds for projections, then open:")
    print(f"{BASE}/calendar?facilityId={facility_id}")


if __name__ == "__main__":
    main()
