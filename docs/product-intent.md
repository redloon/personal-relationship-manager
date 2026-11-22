# Product Intent

## Current phase — November 2026

The capture/recall loop has demonstrated enough value to expand beyond the original pilot restrictions below. Prioritize forgotten-context search, frictionless shared capture, personal profiles/Home, portable backup/recovery, and explicit opt-in follow-ups. Simplicity means low user effort. Do not build conversation-preparation workflows, introduction views, relationship scores, or automatically generated outreach obligations. The original phase gates below are historical context; see decisions.md for current behavior.

## Core Bet

My memory is the bottleneck between how much I care about people and how well I show up for them. A tool that makes capturing and recalling personal details near-frictionless will close that gap — and staying in touch is an asymmetric habit: low effort, unlimited upside.

## Why It Might Work

Every past attempt at this died at the same point: capture friction. Obsidian vaults, Dex, note files — all required a separate "librarian" session I never did. My todo-list app works because capture is instant and I triage later. If this app matches that speed for personal relationship data, the core loop is: jot it in 30 seconds while it's fresh → reference it weeks later when I see that person → have a better conversation because I remember the details.

I've tried enough tools to know the *shape* of what doesn't work. That's useful signal, not wasted time.

## Why It Might Not

I have a pattern: I enjoy building tools more than using them. The flight tracker was fun to build, marginally useful to have. The risk is that this becomes a sophisticated procrastination tool — I organize data about friendships instead of investing in them. Related risks:

- **The guilt-and-snooze cycle.** Nudge notifications ("you haven't talked to Jake in 3 months") trigger the same shame spiral as my habits tracker. I snooze, feel worse, snooze again. Nudges are likely net-negative for me until I have a healthy relationship with the app itself.
- **The librarian trap.** If using the app requires tending, organizing, or grooming data beyond quick capture, I won't do it. Every past attempt proves this.
- **Scope as procrastination.** A 16-packet backlog with dependency graphs is engineering fun, not validation. The backlog should serve the product, not replace it.

## Phase 1 — Test the Core Bet

Build only what tests whether frictionless capture changes my behavior. Nothing else.

**In scope:**
- Quick Add capture (tile/shortcut) that's as fast as my todo inbox
- Contacts with freeform notes/facts attached to them
- Basic event logging tied to people
- **Data safety net**: a simple export or text-file backup so dogfooding data isn't lost to an in-progress app. Doesn't need to be polished — just recoverable.

**Out of scope:** everything else, including things that feel essential but aren't for validation.

**Timeline:** 3 weeks of real use before evaluating.

## Phase 2 — If Capture Sticks

Only enter this phase if the Phase 1 success signals are met.

- Pre-meeting review: open a person's notes before seeing them. Do this manually — no special UI needed. Track whether it changes conversations.
- Birthday reminders (fold in from the separate app I already use).
- Search, if the volume of notes makes it necessary.

## Phase 3 — Earned Complexity

Only if both capture and review demonstrably change behavior:

- Notification/reminder systems
- Export/import/backup automation
- Sync robustness, share targets, widgets
- Any of the product-experience seeds (relationship health, "losing touch" reviews, conversation prep, etc.)

## Stop Conditions

Any two of these sustained over 3 weeks = stop and reassess:

1. **Organizing more than capturing.** I open the app to tidy/browse more than to record or look someone up.
2. **Never referencing before a hangout.** I haven't looked up a person's notes before or after seeing them even once.
3. **Capture isn't happening.** Still too much friction, or I just don't think to do it.
4. **More time on the app than in relationships.** Building, tweaking, configuring > the time the app saves me.
5. **Guilt when I open it.** Same emotional signature as the habits tracker. The app should feel like a tool, not an obligation.

## Success Signals

Need at least 2 of these before expanding scope:

1. I've referenced a stored note in an actual conversation and it made the conversation better.
2. I've captured something within an hour of learning it, and it took under 30 seconds.
3. I've looked someone up before seeing them and felt more prepared.
4. Someone reacted positively to me remembering something I only knew because of the app.

That last one is the whole point: "wow, they took the time to remember."

## The "Should I Build This?" Litmus Test

Before building any new feature, answer these three questions:

1. **Does this help me capture or recall?** If it doesn't directly make it faster to get information in or easier to get it out when I need it, it's not core.
2. **Will I use this weekly, or is it satisfying to imagine having?** The flight tracker was fun to imagine. Be honest about which category this falls into.
3. **Am I building this because the app needs it, or because I want to build it?** Engineering fun is real but it's not product validation. If the answer is "I want to build it," park it.

If a feature fails any of these, it goes on the backlog for later — not now.

## What This Should Feel Like

Concrete human preferences and audit checks live in [usability principles](usability-principles.md). Use them alongside the product decisions when judging capture and recall changes.

The Greenfeld framing is right: staying in touch should require little effort but have unlimited upside. The app is a 30-second interaction, not a system to tend. Open, jot, close. Open, scan, go talk to the person. That's it.

If it ever feels like maintaining the app is itself a relationship I have to tend, something went wrong.
