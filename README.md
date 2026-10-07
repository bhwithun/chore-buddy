# Chore Buddy

Android home-screen widget for an LG WashTower and a dishwasher. Three donuts sit side by side: wash, dry, and dishes. A machine that is off, finished, delayed, or in an error stays gray. A running cycle fills from the top as time elapses, with the time left in the middle.

The app reads LG's official ThinQ Connect API. It does not ship an API key for your account. You create a personal access token and paste it into the app. The token stays in DataStore on the phone.

## Connect LG

1. Sign in at [connect-pat.lgthinq.com](https://connect-pat.lgthinq.com) with the same LG account as the ThinQ app.
2. Create a token that can view devices and view device status.
3. Install Chore Buddy, open **LG token**, paste the token, leave the country as `US` unless your account is somewhere else, and tap **Connect**.

WashTower washer and dryer show up as two devices on most accounts. If LG returns one WashTower device, the app uses it for both Wash and Dry. You can change the mapping before saving.

Until a token is saved, the app and the widget show sample cycles so you can place the widget.

## Widget

Home screen, long-press the wallpaper, Widgets, Chore Buddy. Or tap **Add home screen widget** in the app. The widget is a wide 4×2. Tap it to open the app.

From 8:00 a.m. to midnight, the phone asks LG every 10 minutes while Chore Buddy is open or the widget is on the home screen. Between those checks the rings count down from the last reading. When an estimated timer hits zero, it checks LG again. Outside that window, or with the app closed and the widget removed, it waits. Refresh still fetches immediately. Android can delay alarms while the phone is asleep.

## Build

```powershell
./gradlew testDebugUnitTest assembleDebug installDebug
```

Package: `com.brian.chorebuddy`. Kotlin, Jetpack Compose, Material 3, Glance, WorkManager, DataStore. Bootstrapped from the sibling `_template` project.
