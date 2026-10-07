# Chore Buddy — Agent Instructions

Pure Android app (Kotlin, Jetpack Compose, Material 3, Glance widget) bootstrapped from `_template`.

## Purpose

Home-screen widget with three donuts for an LG WashTower (wash and dry) and a dishwasher. Gray and dim when that machine is not running. The in-app screen uses the same donuts.

## Package

`com.brian.chorebuddy`

```
com.brian.chorebuddy/
├── ChoreBuddyApp.kt
├── MainActivity.kt
├── data/           # AppStorage, ThinQ Connect client, cycle parsing
├── navigation/
├── ui/components/  # ApplianceDonut
├── ui/screens/
├── ui/theme/       # Dark-only. Accent colors live in AppliancePalette
├── viewmodel/
├── widget/         # Glance widget + bitmap donuts
├── work/           # 10-minute LG alarm, local countdown between checks
└── util/
```

## LG ThinQ Connect

- Personal access token from https://connect-pat.lgthinq.com. Never commit a token.
- Public service key `ThinqApi.SERVICE_KEY` is LG's published SDK key, not an account secret.
- Country `US` uses `https://api-aic.lgthinq.com`.
- Headers follow LG's open SDK: `Authorization: Bearer`, `x-country`, `x-client-id`, `x-message-id`, `x-api-key`, `x-service-phase: OP`.
- `GET /devices` and `GET /devices/{id}/state`.
- Monthly energy is `GET /devices/energy/{id}/profile` then `GET /devices/energy/{id}/usage` with `period=MONTHLY` and `YYYYMM` dates. Values are watt-hours. Load them once per local day, and on manual Refresh. The token needs energy inquiry permission.
- A `DEVICE_WASHTOWER` may be one record with `washer` and `dryer` sections. Dedicated `DEVICE_WASHTOWER_WASHER` and `DEVICE_WASHTOWER_DRYER` records are preferred when both exist.
- Progress is elapsed / total from `timer.remainHour`, `remainMinute`, `totalHour`, `totalMinute`.
- Do not add Retrofit, Hilt, or Room.

## Widget

Glance `LaundryWidget` draws the three donuts with `DonutBitmapRenderer`. The sweep math is `donutSweepDegrees` so the widget and the Compose donut match. Refresh through `WidgetUpdater` after fetches.

Automatic refresh is `RefreshPlan` plus an exact alarm (`RefreshScheduler`). Window is 8:00 a.m. to midnight, device local time, and only while the app is started or a widget id is bound. LG is checked every 10 minutes. `projectCycle` counts the rings down from `fetchedAtEpochMs` between checks. The widget repaints on each minute boundary while a timer is running. A running timer whose estimate reaches zero triggers an LG check, including after midnight. Do not poll faster than 10 minutes just because a machine is running. `RefreshLaundryWorker` only exists to cancel the old 15-minute job.

## Theme

Dark-only. Wash `#5B8CFF`, dry `#E6A15C`, dishes `#3ECFB2`. Idle rings and labels stay gray.
