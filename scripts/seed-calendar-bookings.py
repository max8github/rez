#!/usr/bin/env python3

import argparse
import json
import os
import random
import re
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta

DEFAULT_BASE = os.getenv("REZ_BASE_URL", f"http://localhost:{os.getenv('PORT', '9001')}")


def request(base_url, method, path, payload=None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        base_url + path,
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


def get_json(base_url, path):
    with urllib.request.urlopen(base_url + path) as resp:
        return json.loads(resp.read().decode("utf-8"))


def post(base_url, path, payload):
    return request(base_url, "POST", path, payload)


def put(base_url, path, payload):
    return request(base_url, "PUT", path, payload)


def slugify(value):
    value = re.sub(r"[^a-z0-9]+", "-", value.strip().lower()).strip("-")
    return value or "resource"


def parse_args():
    parser = argparse.ArgumentParser(
        description="Seed Rez calendar bookings, optionally against an existing facility."
    )
    parser.add_argument(
        "--host",
        default=DEFAULT_BASE,
        help="Rez base URL, e.g. http://localhost:9001 or https://rez.example.com",
    )
    parser.add_argument(
        "--facility-id",
        help="Existing facility ID to seed. If omitted, the script provisions a fresh facility with 4 courts.",
    )
    return parser.parse_args()


def provision_facility(base_url):
    bot_token = f"bot:local-seed-{int(time.time())}"
    facility_payload = {
        "name": "Calendar Seed Club",
        "address": {"street": "Via Roma 1", "city": "Rome"},
        "timezone": "Europe/Rome",
        "botToken": bot_token,
    }
    facility_id = post(base_url, "/facility/", facility_payload).strip().strip('"')
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
        post(base_url, f"/resource/{resource_id}", resource_payload)
        put(
            base_url,
            f"/resource/{resource_id}/external-ref",
            {"externalRef": resource_id, "externalGroupRef": facility_id},
        )
        resource_ids.append(resource_id)
        print("RESOURCE READY =", resource_id, name)
        time.sleep(1)

    print("Waiting for resources to become readable...")

    for i in range(120):
        ready = 0
        for resource_id in resource_ids:
            resource = get_json(base_url, f"/resource/{resource_id}")
            if resource.get("externalGroupRef") == facility_id:
                ready += 1
        print(f"  poll {i + 1:02d}: {ready}/{len(resource_ids)} resources ready")
        if ready == len(resource_ids):
            time.sleep(1.0)
            return facility_id, resource_ids
        time.sleep(0.5)

    raise RuntimeError("Resources did not finish attaching to the facility in time")


def load_existing_facility_resources(base_url, facility_id):
    resources = get_json(base_url, f"/api/calendar/resources?facilityId={facility_id}")
    resource_ids = [resource["resourceId"] for resource in resources]
    if not resource_ids:
        raise RuntimeError(f"Facility {facility_id} has no resources to seed")

    print("FACILITY_ID =", facility_id)
    print("USING EXISTING RESOURCES:")
    for resource in resources:
        print("RESOURCE READY =", resource["resourceId"], resource["resourceName"])
    return resource_ids


def main():
    args = parse_args()
    base_url = args.host.rstrip("/")

    if args.facility_id:
        facility_id = args.facility_id
        resource_ids = load_existing_facility_resources(base_url, facility_id)
    else:
        facility_id, resource_ids = provision_facility(base_url)

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
            post(base_url, "/bookings", {"reservationId": reservation_id, **payload})
            created += 1
            print(f"BOOKED {created:02d} {reservation_id} {slot_key} {players}")
            time.sleep(0.05)
        except Exception as e:
            print("FAILED", reservation_id, e)

    print()
    print(f"Submitted {created} booking requests.")
    print("Wait a few seconds for projections, then open:")
    print(f"{base_url}/calendar?facilityId={facility_id}")


if __name__ == "__main__":
    main()
