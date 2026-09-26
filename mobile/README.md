# Subscription Tracker for Android and iPhone

The phone version of Subscription Tracker, built with
[Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) and
[Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/): the logic and the
screens are written once in Kotlin and run on both Android and iPhone.

It does what the desktop app does, laid out for a phone:

| Tab | What it shows |
| --- | --- |
| **Subscriptions** | A reminder when a free trial ends this week, your monthly spending against your budget, what's due in the next 7 days and your yearly total. Then every subscription, with search, a billing-cycle filter and sorting (next payment, name, cost per month, category). Tap one to edit, cancel or remove it; **Add** puts in a new one. |
| **Upcoming** | Every payment due in the next 7, 14, 30 or 90 days with the total, and your free trials, which you can keep or cancel. |
| **Spending** | Monthly and yearly totals, the monthly budget (**Set**/**Change**), how much price changes in the last 12 months have added, spending per category, and the price history. |
| **Cancelled** | What cancelling has saved you, and your cancelled subscriptions, which you can restore or remove. |

The menu at the top right has **Import CSV…**, **Export CSV…**, **Back up all data…**,
**Restore from backup…** and **Currency symbol…**. When you change a subscription's cost, it's
recorded as a price change unless you turn that off (for fixing a typo). Every change is saved
straight away.

**Reminders.** The phone reminds you the day before each payment ("Netflix is due tomorrow"),
and two days and one day before a free trial ends, at about 9 in the morning. The first time
you open the app it asks whether it may send notifications; you can change that later in the
phone's settings.

**Home-screen widget (Android).** Long-press the home screen, choose *Widgets* and add
*Subscriptions*: it shows what you spend a month and your next payment, and opens the app when
tapped.

**Backups.** *Back up all data…* saves everything (subscriptions, cancelled ones, price history,
budget and currency) to a file you can keep in Google Drive or iCloud Drive. *Restore from
backup…* reads it back, replacing what's on the phone, for example on a new phone.

## Moving your subscriptions to the phone

Everything, including price history, cancelled subscriptions, the budget and the currency:

1. Get the desktop app's `subscriptions.txt` onto your phone (email it to yourself, or use
   Google Drive, iCloud Drive or a cable). It's in the folder the desktop app runs from.
2. In the phone app, open the menu at the top right, choose **Restore from backup…** and pick it.

Or just the subscriptions themselves: **Export CSV…** in the desktop app, then **Import CSV…** on
the phone, which adds them to what's already there. A spreadsheet saved as CSV works too; see
[Importing from a spreadsheet](../README.md#importing-from-a-spreadsheet).

## Running it

You need [Android Studio](https://developer.android.com/studio) with the
**Kotlin Multiplatform** plugin (Settings → Plugins).

### Android

1. In Android Studio, **File → Open** and choose this `mobile` folder. Let Gradle sync.
2. Plug in your phone with USB debugging on (or start an emulator), choose the
   **composeApp** run configuration and press **Run**.

Or, without building it yourself: every push runs the **Mobile** workflow on GitHub, which
uploads the app as **subscription-tracker-android** under the run's *Artifacts*. Download it,
unzip it, copy `composeApp-debug.apk` to the phone and open it (Android asks you to allow
installing apps from that source). The run also uploads **android-screenshots**: each screen,
and the reminder notifications, from an Android emulator.

#### Updates that keep your data

Android only installs a new version over the old one if both were signed with the same key;
otherwise you'd have to uninstall first, which deletes your subscriptions. So the workflow signs
every build with one key, kept in the repository's secrets (never in the code, since the
repository is public). To set it up once, in GitHub go to **Settings → Secrets and variables →
Actions → New repository secret** and add:

- `ANDROID_KEYSTORE_BASE64`: the key file, base64-encoded
- `ANDROID_KEYSTORE_PASSWORD`: its password (the key inside is called `subscriptions`)

Keep a copy of the key file somewhere safe: without it, future builds can't update the app.
Builds you run from Android Studio use its own debug key, so don't mix the two on one phone
(or back up first and restore after reinstalling).

### iPhone

Building for iPhone needs a Mac with [Xcode](https://developer.apple.com/xcode/).

1. Open `iosApp/iosApp.xcodeproj` in Xcode (or pick the **iosApp** run configuration in Android
   Studio on the Mac).
2. Under **Signing & Capabilities**, choose your team (a free Apple ID works), or put your team ID
   in `iosApp/Configuration/Config.xcconfig`.
3. Choose your iPhone or a simulator and press **Run**.

With a free Apple ID, apps installed on your own iPhone stop opening after 7 days; running them
from Xcode again renews them. A paid Apple Developer account ($99 a year) makes that a year.

## Where the data is kept

On the phone, in the app's own storage: `subscriptions.txt`, in exactly the same format as the
desktop app's file, with a `.bak` copy of the previous save. It's included in the phone's normal
backups. On iPhone you can also see it in the **Files** app under *On My iPhone → Subscriptions*.

## Project layout

```
composeApp/src/
  commonMain/kotlin/subscriptiontracker/mobile/
    data/       The logic, shared by Android and iPhone: subscriptions, billing dates, budget,
                price changes, saving, CSV import and export (a port of the desktop app's Java)
    ui/         The screens, in Compose Multiplatform
  commonTest/   Tests for the logic; they run on Android (JVM) and on the iPhone simulator
  androidMain/  MainActivity, the Android manifest and icon
  iosMain/      MainViewController, which the iPhone app shows
iosApp/         The Xcode project that wraps it for iPhone
```

Amounts are kept as whole cents, and every rule (monthly equivalents, rounding, billing on the
31st, trials ending, savings) matches the desktop app; the tests check the same cases.

## Tests

- **Android Studio:** run the tests in `composeApp/src/commonTest`.
- **Command line:** `./gradlew :composeApp:testDebugUnitTest` (Android) or
  `./gradlew :composeApp:iosSimulatorArm64Test` (on a Mac).
