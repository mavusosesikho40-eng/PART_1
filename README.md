# Subscription Tracker

[![Tests](https://github.com/mavusosesikho40-eng/PART_1/actions/workflows/tests.yml/badge.svg)](https://github.com/mavusosesikho40-eng/PART_1/actions/workflows/tests.yml)

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
3. Edit, cancel or remove a subscription
4. Upcoming payments and free trials
5. Search, filter and sort
6. Spending, budget and savings
7. Import or export CSV
0. Exit
```

Options 3 to 7 open a short sub-menu; press Enter on a blank line to go back.

## Features

| Menu | What it does |
| --- | --- |
| **1. View all subscriptions** | Table of every subscription, soonest payment first, with any notes listed underneath. |
| **2. Add a subscription** | Name, cost, billing cycle (weekly, monthly, quarterly or yearly), whether it's a free trial, next payment date (`YYYY-MM-DD`), category and an optional note (e.g. which account or card it's on). For a free trial, the date is when the trial ends and the first charge is taken. Invalid answers are asked again; a blank name cancels. |
| **3. Edit, cancel or remove** → Edit | Change any field, including whether it's a free trial; press Enter to keep the current value (for the note, `-` removes it). If you change the cost, you're asked whether the price really changed (it's added to the price history) or you're just fixing a mistake. |
| &nbsp;&nbsp;&nbsp;→ Cancel | Moves a subscription to your cancelled list instead of deleting it, and shows what you'll save per month and per year. |
| &nbsp;&nbsp;&nbsp;→ Remove permanently | Deletes it for good (asks for confirmation first). |
| **4. Upcoming payments and free trials** → Upcoming payments | Payments due in the next N days (30 by default) and their total. |
| &nbsp;&nbsp;&nbsp;→ Free trials | Lists your free trials, soonest ending first, with how long is left and what they'll cost afterwards. |
| **5. Search, filter and sort** → Search by category | Finds subscriptions whose category contains your search text, ignoring case, with their totals. |
| &nbsp;&nbsp;&nbsp;→ Filter by billing cycle | Shows only weekly, monthly, quarterly or yearly subscriptions, with how many there are and what they cost per month and per year. |
| &nbsp;&nbsp;&nbsp;→ Sort | Sort by cost per month, next payment date, name, category, billing cycle or ID, in either direction. |
| **6. Spending, budget and savings** → Spending summary | Monthly and yearly totals, plus monthly spend per category with percentages. |
| &nbsp;&nbsp;&nbsp;→ Monthly budget | Set, change or remove a monthly spending limit (enter 0 to remove it). |
| &nbsp;&nbsp;&nbsp;→ Cancelled subscriptions and savings | Your cancelled subscriptions, what each has saved you so far (the payments you would have made since cancelling), and your total savings. You can restore one here if you sign up again. |
| &nbsp;&nbsp;&nbsp;→ Price changes | Every recorded price change, newest first (e.g. `169.00 -> 199.00 (+30.00, +18%)`, or per month if the billing cycle changed too: `199.00 a month -> 2,000.00 a year (-32.33 a month, -16%)`), and how much price changes in the last 12 months have added to your monthly and yearly spending. |
| &nbsp;&nbsp;&nbsp;→ Currency symbol | Show every amount with a symbol, e.g. `R 199.00` or `$199.00` (symbols ending in a letter get a space). Enter `-` to remove it. The CSV export always uses plain numbers. |
| **7. Import or export CSV** → Export | Writes everything to a CSV file (`subscriptions.csv` by default) that Excel or Google Sheets can open. |
| &nbsp;&nbsp;&nbsp;→ Import | Adds subscriptions from a CSV file: one exported by the app, or a spreadsheet saved as CSV. See [Importing from a spreadsheet](#importing-from-a-spreadsheet). |

A few things happen automatically:

- **Reminders on startup** — payments due in the next 7 days are shown when the app opens.
- **Payment dates roll forward** — once a payment date has passed, it moves to the next one
  (e.g. a monthly subscription due 2026-09-01 becomes 2026-10-01). Subscriptions billed late in the
  month keep their day: one billed on the 31st is due 28 Feb, then 31 Mar again.
- **Free-trial reminders** — trials ending in the next 7 days are called out at startup, with what
  you'll be charged unless you cancel. Trials are marked "(trial)" in lists, and once the end date
  has passed a trial becomes a normal paid subscription.
- **Cancelled subscriptions stay out of the way** — they're left out of every list, total, sort,
  search, reminder, the budget and the CSV export, but kept in the data file for your savings.
- **Budget warnings** — if you've set a monthly budget, you're warned at startup and after adding
  or editing a subscription when your subscriptions cost 90% or more of it, or go over it. The
  spending summary also shows how much of the budget you've used.
- **Saving** — every change is saved straight away.
- **Fair cost comparisons** — totals and the cost sort convert every subscription to a monthly
  amount, so a 2,400 yearly plan counts as 200 a month.

## Importing from a spreadsheet

Save your spreadsheet as CSV, then choose **7. Import or export CSV → Import** and type the file's
name. The first row must name the columns; they can be in any order, and other columns are
ignored.

| Column | Needed? | Also recognised as | Notes |
| --- | --- | --- | --- |
| Name | Yes | Subscription, Service | |
| Cost | Yes | Price, Amount | `199`, `R 1,299.00`, `$1 299.50` and `1299,50` all work. |
| Billing Cycle | No (Monthly) | Cycle, Billing, Frequency | Weekly, Monthly, Quarterly or Yearly (also Annual/Annually). |
| Next Payment | No (today) | Next Payment Date, Next Due, Due Date, Next Billing Date | `2026-10-01`, `2026/10/01` or `01/10/2026` (day first). |
| Category | No (Other) | | |
| Free Trial | No | Trial | Yes/No. |
| Note | No | Notes | |

Files saved as UTF-8 or in the Windows character set (Excel's "CSV (Comma delimited)") both work,
so accents and symbols like "€" come through. Files separated by commas, semicolons (as Excel saves them with some regional settings) or tabs
all work. Rows that can't be read, or whose name is already in your list, are skipped, and the app
tells you which rows and why; everything else is imported.

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

Free trials have `TRIAL` as an extra column at the end of the line, cancelled subscriptions
have `CANCELLED=` followed by the date they were cancelled, and each recorded price change adds a
`PRICE=date:old:new` column, e.g. `PRICE=2026-09-26:169.00:199.00` (with the old and new billing
cycle added, e.g. `:MONTHLY:YEARLY`, if the plan changed at the same time). A note is saved as a
`NOTE=` column, and a `DAY=31` column remembers the billing day while a short month has moved
the date earlier. If you've set a monthly
budget, it's saved on the first line as `BUDGET` followed by the amount, and a currency symbol is
saved on a `CURRENCY` line.

### Keeping your data safe

Saving never leaves a half-written file behind, even if the app is closed or the computer loses
power in the middle of a save. The new contents are written to a temporary file first, and only
once that's safely on disk does it replace `subscriptions.txt`.

- **`subscriptions.txt.bak`** — the previous version, kept every time the app saves. If something
  goes wrong, rename it to `subscriptions.txt` to go back one save.
- **Edited it in Notepad?** The file is normally saved as UTF-8, but if it's been saved in the Windows
  character set (Notepad's "ANSI"), the app still reads it, accents and all, and saves it back as UTF-8.
  If the file can't be read at all, the app warns you and won't save over it.
- **`subscriptions.txt.unreadable`** — if the app ever finds lines it can't read when it starts, it
  warns you and copies the file exactly as it was here before saving anything, so those lines
  aren't lost.

`subscriptions.txt`, its backup copies and `subscriptions.csv` are listed in `.gitignore`, so your
personal data is never committed.

## Running the tests

The tests are JUnit 4 tests in `test/subscriptiontracker/`.

They also run automatically on GitHub for every pull request and every push to `master`
(see `.github/workflows/tests.yml`); the result shows as a check on the pull request.

- **NetBeans:** **Run → Test Project** (Alt+F6). If NetBeans reports a missing JUnit or Hamcrest
  library, right-click the project and choose **Resolve Project Problems** to download it.
- **Ant:** `ant test` (point `libs.junit_4.classpath` and `libs.hamcrest.classpath` at the
  JUnit 4 and Hamcrest jars if NetBeans isn't installed).

## Project layout

```
src/subscriptiontracker/
  SubscriptionTracker.java   main() and the main menu; loads and saves the data file
  ManageScreen.java          View, add, edit, cancel and remove subscriptions
  UpcomingScreen.java        Upcoming payments, free trials and the startup reminders
  FindScreen.java            Search, filter and sort
  MoneyScreen.java           Spending summary, monthly budget, cancelled subscriptions and savings, price changes
  Console.java               Reading answers: prompts, amounts, dates, yes/no and sub-menus
  Display.java               Shared formatting: amounts, "in 3 days", the subscription table
  SubscriptionManager.java   The list of subscriptions: add, remove, search, sort, totals, budget
  Subscription.java          One subscription: payment-date roll-forward, trials, cancelling, price history
  PriceChange.java           One price change: date, old and new cost
  BillingCycle.java          Weekly / monthly / quarterly / yearly, with cost conversions
  SubscriptionStorage.java   Saving to and loading from subscriptions.txt, safely
  CsvScreen.java             The Import or export CSV menu
  CsvExporter.java           CSV export
  CsvImporter.java           CSV import: column names, amounts, dates, quoted cells
test/subscriptiontracker/    JUnit tests
```
