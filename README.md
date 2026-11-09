# CampusConnect

A native Android app that brings BMSCE events, fests and placement opportunities into one place, with separate experiences for students and admins.

Semester-end course project at BMSCE, May 2026. It runs from Android Studio and hasn't been deployed or published to the Play Store. An earlier team version is in [CampusConnectProject](https://github.com/BamboozleYou/CampusConnectProject).

**Contents:** [Features](#features) · [Tech stack](#tech-stack) · [How it's organised](#how-its-organised) · [Run it](#run-it) · [Limitations](#limitations)

## Features

**Students**
- Sign up and log in
- Browse events and opportunities in separate tabs
- Search and filter the feed
- Bookmark events and view saved ones
- Register for events and give feedback

**Admins**
- Post new events
- See registrations on a dashboard chart

## Tech stack

| Area | Tools |
|---|---|
| Language | Kotlin |
| Backend | Firebase Auth, Cloud Firestore, Firebase Storage |
| UI | Material Components, Glide, Shimmer, MPAndroidChart |
| Android | Min SDK 24, target SDK 36 |

## How it's organised

```text
app/src/main/java/com/mad/cc_campusconnect/
├── MainActivity.kt          Feed, tabs, search and admin dashboard
├── LoginActivity.kt         Log in
├── SignupActivity.kt        Sign up
├── AddEventActivity.kt      Admin event creation
├── EventDetailActivity.kt   Event details and registration
├── SavedEventsActivity.kt   Bookmarked events
├── adapters/                List rendering
├── feedback/                Feedback sheet
├── models/                  Event and User
└── utils/                   User repository and helpers
```

**Firestore collections:** `users`, `events`, `opportunities`, `feedback`, and `registrations` under each event.

Admin access comes from each user's Firestore record: either the `admin` flag or a `role` of `admin`.

## Run it

1. Clone the repo and open it in Android Studio.
2. Create your own Firebase project with Auth and Firestore enabled.
3. Replace `app/google-services.json` with your project's file.
4. Run on an emulator or a device with Android 7.0 or later.

## Limitations

- There are no real automated tests yet, only the template test files.
- Firestore security rules aren't in this repo.
