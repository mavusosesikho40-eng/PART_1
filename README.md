# Subscription Tracker

A Java console app for keeping track of your subscriptions — what they cost,
how often you pay, and when the next payment is due.

```
=== Subscription Tracker ===
Payments due in the next 7 days:
  Spotify                     59.99  2026-09-27 (tomorrow)
  Netflix                    199.00  2026-10-01 (in 5 days)
  Total due: 258.99

1. View all subscriptions
2. Add a subscription
3. Edit a subscription
4. Remove a subscription
5. Upcoming payments
6. Spending summary
7. Search by category
8. Export to CSV
9. Sort subscriptions
0. Exit
```

## Features

| Option | What it does |
| --- | --- |
| 1. View all subscriptions | Table of every subscription, soonest payment first. |
| 2. Add a subscription | Name, cost, billing cycle (weekly, monthly, quarterly or yearly), next payment date (`YYYY-MM-DD`) and category. Invalid answers are asked again; a blank name cancels. |
| 3. Edit a subscription | Change any field; press Enter to keep the current value. |
| 4. Remove a subscription | Asks for confirmation first. |
| 5. Upcoming payments | Payments due in the next N days (30 by default) and their total. |
| 6. Spending summary | Monthly and yearly totals, plus monthly spend per category with percentages. |
| 7. Search by category | Finds subscriptions whose category contains your search text, ignoring case, with their totals. |
| 8. Export to CSV | Writes everything to a CSV file (`subscriptions.csv` by default) that Excel or Google Sheets can open. |
| 9. Sort subscriptions | Sort by cost per month, next payment date, name, category, billing cycle or ID, in either direction. |

A few things happen automatically:

- **Reminders on startup** — payments due in the next 7 days are shown when the app opens.
- **Payment dates roll forward** — once a payment date has passed, it moves to the next one
  (e.g. a monthly subscription due 2026-09-01 becomes 2026-10-01).
- **Saving** — every change is saved straight away.
- **Fair cost comparisons** — totals and the cost sort convert every subscription to a monthly
  amount, so a 2,400 yearly plan counts as 200 a month.

## Requirements

- Java 21 or newer
- [Apache NetBeans](https://netbeans.apache.org/) (or Apache Ant) to build the project

## Running the app

### In NetBeans

1. **File → Open Project** and choose this folder.
2. Press **Run** (F6).

The app runs in NetBeans' Output window; type your answers there.

### From the command line

Build the jar with **Clean and Build** in NetBeans (or `ant jar`), then:

```sh
java -jar dist/SubscriptionTracker.jar
```

## Where your data is saved

Subscriptions are saved to `subscriptions.txt` in the folder the app is run from (the project
folder when you run it from NetBeans). To use a different file, pass its path as an argument:

```sh
java -jar dist/SubscriptionTracker.jar my-subscriptions.txt
```

The file is plain text with one subscription per line, tab-separated:

```
ID    Name    Cost    Cycle    Next payment    Category
```

`subscriptions.txt` and `subscriptions.csv` are listed in `.gitignore`, so your personal data is
never committed.

## Running the tests

The tests are JUnit 4 tests in `test/subscriptiontracker/`.

- **NetBeans:** **Run → Test Project** (Alt+F6). If NetBeans reports a missing JUnit or Hamcrest
  library, right-click the project and choose **Resolve Project Problems** to download it.
- **Ant:** `ant test` (point `libs.junit_4.classpath` and `libs.hamcrest.classpath` at the
  JUnit 4 and Hamcrest jars if NetBeans isn't installed).

## Project layout

```
src/subscriptiontracker/
  SubscriptionTracker.java   Console menu and main() — the app's entry point
  SubscriptionManager.java   The list of subscriptions: add, remove, search, sort, totals
  Subscription.java          One subscription and its payment-date roll-forward
  BillingCycle.java          Weekly / monthly / quarterly / yearly, with cost conversions
  SubscriptionStorage.java   Saving to and loading from subscriptions.txt
  CsvExporter.java           CSV export
test/subscriptiontracker/    JUnit tests for each class above
```
