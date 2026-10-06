# Subscription Tracker

[![Build](https://github.com/mavusosesikho40-eng/PART_1/actions/workflows/build.yml/badge.svg)](https://github.com/mavusosesikho40-eng/PART_1/actions/workflows/build.yml)

Keep track of your subscriptions: what they cost, how often you pay, when the next payment is
due, and what you actually spend. One app for **Android**, **iPhone** and the **desktop**
(Windows, macOS, Linux), written once in Kotlin with
[Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/).

## Getting the app

Every change that reaches `master` and passes its checks is published on the
[**Releases**](https://github.com/mavusosesikho40-eng/PART_1/releases) page as `v1.0.<number>`:

- **Android:** on your phone, open the latest release and tap `SubscriptionTracker-….apk`.
  Android asks you to allow installing apps from your browser the first time. Each new release
  installs over the last one and keeps your data (see [Updates that keep your data](#updates-that-keep-your-data)).
- **Windows:** download `SubscriptionTracker-….msi` and run it. It adds Subscription Tracker to
  the Start menu; a newer installer updates it.
- **iPhone:** needs a Mac with Xcode; see [Building it yourself](#building-it-yourself).

## What it does

**The first time** you open it, a short setup asks which currency you pay in, whether you'd like a
monthly budget, and how you'd like to start: add your first subscription, import a CSV, restore a
backup, or just look around. (It doesn't appear again once there's data.)

Then four tabs (along the bottom on a phone, down the side in a wide window):

| Tab | What it shows |
| --- | --- |
| **Subscriptions** | A reminder when a free trial ends this week, your monthly spending against your budget, what's due in the next 7 days and your yearly total. Then every subscription, with search, a billing-cycle filter and sorting (next payment, name, cost per month, category). Tap one to edit, cancel or remove it; **Add** puts in a new one. |
| **Upcoming** | Every payment due in the next 7, 14, 30 or 90 days with the total, and your free trials, which you can keep or cancel. **Paid…** records what you actually paid for a payment due this week. |
| **Spending** | Monthly and yearly totals, the monthly budget, how much price changes in the last 12 months have added, a chart of what you actually spent in each of the last 12 months against your budget (tap a month to read it, or switch to a table), your recent payments, spending per category, exchange rates and the price history. |
| **Cancelled** | What cancelling has saved you, and your cancelled subscriptions, which you can restore or remove. |

The menu at the top right has **Import CSV…**, **Export CSV…**, **Back up all data…**,
**Restore from backup…**, **Sync…** and **Currency symbol…**.

- **Billing dates move on by themselves.** Once a payment date passes it moves to the next one
  (a monthly subscription due 1 Sep becomes 1 Oct), and one billed on the 31st goes back to the
  31st after a short month. A free trial whose end date has passed becomes a paid subscription.
- **Price changes.** Changing a subscription's cost records the change in your price history,
  unless you turn that off because you're just fixing a typo.
- **Other currencies.** Turn on *Billed in another currency* for something that charges in, say,
  US dollars, and enter the exchange rate (1 USD = R 18.25). Its amounts show in dollars
  ("USD 20.00"); totals, the budget, categories and savings are in your currency. Change the rate
  now and then on the Spending tab. (Rates are entered by you; the app doesn't fetch them.)
- **What you actually spend.** Each payment is recorded at the list price when its date passes.
  If you paid something different, use **Paid…** on the Upcoming tab, or tap the payment under
  *Recent payments* to correct it.
- **Reminders** (Android and iPhone). The day before each payment ("Netflix is due tomorrow"), and
  two days and one day before a free trial ends, at about 9 in the morning.
- **Home-screen widget** (Android). Long-press the home screen, choose *Widgets* and add
  *Subscriptions*: your monthly total and next payment.
- **Saving.** Every change is saved straight away, safely: the new version is written to a
  temporary file first, and the previous one is kept as `subscriptions.txt.bak`. If the app ever
  finds lines it can't read, it copies the file to `subscriptions.txt.unreadable` first.

## Your data

Everything is in one plain-text file, `subscriptions.txt`:

- **Android and iPhone:** in the app's own storage, included in the phone's backups. On iPhone you
  can also see it in the Files app under *On My iPhone → Subscriptions*.
- **Desktop:** in a `SubscriptionTracker` folder in your user folder. To use another file, start
  the app with its path as an argument.

**Back up all data…** saves a copy anywhere you like (e.g. Google Drive or iCloud Drive), and
**Restore from backup…** reads it back, e.g. on a new phone. Restore also reads the file the old
Java version of this app saved, so nothing from it is lost; the desktop app copies that file
over by itself if you start it from the folder the old app ran in.

The file has one subscription per line, tab-separated: ID, name, cost, cycle, next payment date
and category, then any of `TRIAL`, `CANCELLED=date`, `DAY=31` (billing day after a short month),
`NOTE=text`, `CUR=USD`, `PRICE=date:old:new[:OLDCYCLE:NEWCYCLE]` and `PAID=date:amount[:assumed]`.
`UID=` and `UPDATED=` identify each subscription and when it last changed, for syncing.
`BUDGET`, `CURRENCY` and `RATE` lines hold the budget, your currency symbol and exchange rates;
`SETTINGS` says when those last changed and `DELETED` lines remember removed subscriptions, so a
sync doesn't bring them back.

### Syncing between your phone and computer

**Sync…** keeps one list on all your devices through a single file in your Google Drive, iCloud
Drive or OneDrive. On the first device choose **Create a sync file** and save it in the drive; on
the others choose **Use an existing sync file** and pick that same file. (On a computer, install
the drive's own app, e.g. Google Drive for desktop, so its folder shows up among your files.)

The app syncs when it opens and a moment after each change. Changes are merged one subscription at
a time: if you edit Netflix on your phone and add Spotify on your computer, both survive. When the
same subscription was changed on two devices, the later change wins; payments recorded on either
are kept. **Stop syncing** leaves the file and your data as they are.

## Importing from a spreadsheet

Save your spreadsheet as CSV, then choose **Import CSV…** and pick the file. The first row must
name the columns; they can be in any order, and other columns are ignored.

| Column | Needed? | Also recognised as | Notes |
| --- | --- | --- | --- |
| Name | Yes | Subscription, Service | |
| Cost | Yes | Price, Amount | `199`, `R 1,299.00`, `$1 299.50`, `1299,50` and `1.299,50` all work. |
| Billing Cycle | No (Monthly) | Cycle, Billing, Frequency | Weekly, Monthly, Quarterly or Yearly (also Annual/Annually). |
| Next Payment | No (today) | Next Payment Date, Next Due, Due Date, Next Billing Date | `2026-10-01`, `2026/10/01` or `01/10/2026` (day first). |
| Category | No (Other) | | |
| Free Trial | No | Trial | Yes/No. |
| Note | No | Notes | |
| Currency | No (yours) | | A code like USD, for one billed in another currency. |

Files saved as UTF-8 or in the Windows character set (Excel's "CSV (Comma delimited)") both work,
and so do commas, semicolons or tabs between the columns. Rows that can't be read, or whose name
is already in your list, are skipped, and the app tells you which and why. **Export CSV…** writes
the same columns, plus monthly and yearly costs.

## Building it yourself

Open this folder in [Android Studio](https://developer.android.com/studio) (with the **Kotlin
Multiplatform** plugin) or IntelliJ IDEA, and let Gradle sync.

- **Android:** run the **composeApp** configuration on your phone (USB debugging on) or an
  emulator.
- **Desktop:** `./gradlew :composeApp:run`, or `./gradlew :composeApp:packageDistributionForCurrentOS`
  for an installer in `composeApp/build/compose/binaries/`.
- **iPhone:** on a Mac, open `iosApp/iosApp.xcodeproj` in Xcode, choose your team under
  **Signing & Capabilities** (a free Apple ID works) and run it on your iPhone. With a free
  Apple ID, apps you install yourself stop opening after 7 days until you run them from Xcode
  again; a paid developer account ($99 a year) makes that a year.

### Updates that keep your data

Android only installs a new version over the old one if both were signed with the same key;
otherwise you'd have to uninstall first, which deletes your data. So releases are signed with one
key kept in the repository's secrets (never in the code, since the repository is public). In
GitHub, **Settings → Secrets and variables → Actions** needs:

- `ANDROID_KEYSTORE_BASE64`: the key file, base64-encoded
- `ANDROID_KEYSTORE_PASSWORD`: its password (the key inside is called `subscriptions`)

Keep a copy of the key file somewhere safe: without it, future releases can't update the app.
Apps you build in Android Studio use its own debug key instead, so don't mix the two on one
phone (or back up first and restore after reinstalling).

## Checks

The [Build](.github/workflows/build.yml) workflow runs on every push and pull request:

- **Android:** the tests, the app, and screenshots of every screen (light and dark) and of the
  reminder notifications, taken on an Android emulator with sample data
  (`scripts/screenshots.sh`). They're under the run's *Artifacts* as **android-screenshots**.
- **iPhone:** the tests on the iPhone simulator, and the Xcode project.
- **Desktop:** the tests on the JVM, and every screen drawn at a desktop window's size and a
  phone's (**desktop-screenshots**).

## Project layout

```
composeApp/src/
  commonMain/kotlin/subscriptiontracker/mobile/
    data/        The logic: subscriptions, billing dates, budget, price changes, currencies,
                 payments, reminders, saving, CSV import and export, backups, sync
    ui/          The screens, shared by every platform
  commonTest/    Tests for the logic; they run on Android, iPhone and the desktop
  androidMain/   MainActivity, reminders (WorkManager), the widget, the manifest
  iosMain/       MainViewController, reminders (local notifications)
  desktopMain/   main() for the desktop app
  desktopTest/   Draws every screen to PNG files
  commonMain/composeResources/font/   DM Serif Display, the headline font
iosApp/          The Xcode project that wraps it for iPhone
scripts/         The Android emulator screenshots
```

Amounts are kept as whole cents and rounded half up, so totals are exact.

The headline font, [DM Serif Display](https://fonts.google.com/specimen/DM+Serif+Display), is
included under the SIL Open Font License (`licenses/OFL-DMSerifDisplay.txt`).
