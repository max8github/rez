# User Onboarding — Booking a Court via Telegram

This document describes how a new member (e.g. Mike) joins the booking system
and makes their first reservation. It also serves as the reference use case for
future provisioning improvements.

## Prerequisites (facility admin, one-time)

The admin must have already:
- Deployed the booking service with a valid bot token
- Registered the Telegram webhook with the service URL

No per-user setup is required on the server side — users are self-provisioned
on first contact.

---

## Steps for Mike

### 1. Install Telegram

Download and install Telegram on your phone or desktop from [telegram.org](https://telegram.org).
Create an account if you don't have one yet.

### 2. Find the booking bot

Search for **`@EtcEnBookingBot`** (replace with actual bot username) in Telegram,
or ask your facility admin for the link.

### 3. Start the conversation

Tap **Start** (or send `/start`). The bot will greet you and explain what it can do.

### 4. Book a court

Just write naturally, for example:

> "Book court 1 tomorrow at 6pm for Mike"

> "I'd like to reserve a court on Saturday morning"

The bot will confirm the booking and tell you which court and time slot was reserved.

### 5. Check the calendar

The facility's court calendar is available at:

> https://calendar.google.com/calendar/u/0/embed?ctz=Europe/Berlin&src=3d228lvsdmdjmj79662t8r1fh4@group.calendar.google.com&src=63hd39cd9ppt8tajp76vglt394@group.calendar.google.com&src=42cf1e8db6c37f2a7c8f02dbf9b6fc9d497008ecd92a30892ea7b1a380c8e130@group.calendar.google.com&src=2bba1d7802c29ab3a4455cadaebc68b0bf79370ac009b053664c9a2decb2ea1a@group.calendar.google.com

Your booking will appear there within a few seconds of the bot's confirmation.

---

## Notes

- **No account creation needed** — your Telegram identity is used directly.
- **Natural language** — you don't need commands or menus, just describe what you want.
- **One slot per booking** — each booking reserves a 1-hour slot.
- **Cancellations** — just ask the bot to cancel your booking.

---

## Future provisioning improvements (backlog)

- Admin UI to register known members by name/Telegram handle before first contact
- Per-user booking history and preferences
- Rescheduling via bot
- Facility-specific timezone and locale configuration
- Notification when a previously full slot becomes available
