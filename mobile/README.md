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

The menu at the top right has **Import CSV…**, **Export CSV…** and **Currency symbol…**.
When you change a subscription's cost, it's recorded as a price change unless you turn that off
(for fixing a typo). Every change is saved straight away.

## Moving your subscriptions to the phone

1. In the desktop app, click **Export CSV…** and save `subscriptions.csv`.
2. Get the file onto your phone (email it to yourself, or use Google Drive, iCloud Drive or a cable).
3. In the phone app, open the menu at the top right, choose **Import CSV…** and pick the file.

Everything comes across: names, costs, billing cycles, next payment dates, categories, free trials
and notes. (Price history, cancelled subscriptions, the budget and the currency symbol aren't in
the CSV; set the budget and symbol again on the phone.) A spreadsheet saved as CSV works too; see
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
installing apps from that source).

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
