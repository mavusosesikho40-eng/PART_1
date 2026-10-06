package subscriptiontracker.mobile.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class StorageTest {

    private val fs = FakeFileSystem()
    private val file = "/data/subscriptions.txt".toPath()
    private val storage = Storage(fs, file)

    init {
        fs.createDirectories("/data".toPath())
    }

    /** Written by the desktop app (on Windows, so with CRLF line endings). */
    private val desktopFile = "BUDGET\t1200\r\n" +
        "CURRENCY\tR\r\n" +
        "7\tShowmax\t99.00\tMONTHLY\t2026-06-12\tEntertainment\tCANCELLED=2026-06-01\r\n" +
        "4\tDisney+\t0.00\tMONTHLY\t2026-09-30\tEntertainment\tTRIAL\r\n" +
        "1\tNetflix\t199.00\tMONTHLY\t2026-10-01\tEntertainment\tNOTE=Shared with family\tPRICE=2026-03-01:169.00:199.00\r\n" +
        "3\tOffice\t2000.00\tYEARLY\t2027-01-15\tSoftware\tPRICE=2026-01-15:199.00:2000.00:MONTHLY:YEARLY\r\n" +
        "9\tGym\t450.00\tMONTHLY\t2027-02-28\tHealth\tDAY=31\r\n"

    @Test
    fun readsEverythingTheDesktopAppSaves() {
        fs.write(file) { writeUtf8(desktopFile) }
        val m = SubscriptionManager()

        val result = storage.load(m)

        assertEquals(Storage.LoadResult(0, null), result)
        assertEquals(120000, m.monthlyBudget)
        assertEquals("R", m.currencySymbol)
        assertEquals(4, m.all.size)
        assertTrue(m.find(4)!!.freeTrial)
        val netflix = m.find(1)!!
        assertEquals("Shared with family", netflix.note)
        assertEquals(PriceChange(LocalDate(2026, 3, 1), 16900, 19900, BillingCycle.MONTHLY), netflix.priceChanges.single())
        assertEquals(31, m.find(9)!!.billingDay)
        assertEquals(LocalDate(2026, 6, 1), m.find(7)!!.cancelledOn)
        assertTrue(m.find(3)!!.priceChanges.single().cycleChanged)
        assertEquals(10, m.add("New", 100, BillingCycle.MONTHLY, LocalDate(2026, 10, 1), "X").id)
    }

    @Test
    fun savesTheSameTextTheDesktopAppDoes() {
        fs.write(file) { writeUtf8(desktopFile) }
        val m = SubscriptionManager()
        storage.load(m)

        storage.save(m)

        // Line endings become \n, and the budget is written with its cents.
        val expected = desktopFile.replace("\r\n", "\n").replace("BUDGET\t1200", "BUDGET\t1200.00")
        assertEquals(expected, fs.read(file) { readUtf8() })
    }

    @Test
    fun savingKeepsTheOldFileAsABackup() {
        val m = SubscriptionManager()
        m.add("A", 100, BillingCycle.MONTHLY, LocalDate(2026, 10, 1), "X")
        storage.save(m)
        m.add("B", 100, BillingCycle.MONTHLY, LocalDate(2026, 10, 2), "X")
        storage.save(m)

        assertTrue(fs.read(storage.backupFile) { readUtf8() }.contains("\tA\t"))
        assertTrue(!fs.read(storage.backupFile) { readUtf8() }.contains("\tB\t"))
        assertTrue(fs.read(file) { readUtf8() }.contains("\tB\t"))
        assertTrue(!fs.exists("/data/subscriptions.txt.tmp".toPath()))
    }

    @Test
    fun unreadableLinesAreSkippedAndTheFileIsCopiedFirst() {
        fs.write(file) { writeUtf8("this line is broken\n1\tNetflix\t199.00\tMONTHLY\t2026-10-01\tX\n2\tBad\tabc\tMONTHLY\t2026-10-01\tX\n") }
        val m = SubscriptionManager()

        val result = storage.load(m)

        assertEquals(2, result.skippedLines)
        assertNotNull(result.unreadableCopy)
        assertTrue(fs.read(result.unreadableCopy!!) { readUtf8() }.contains("this line is broken"))
        assertEquals(listOf("Netflix"), m.all.map { it.name })
    }

    @Test
    fun missingFileLoadsNothing() {
        val m = SubscriptionManager()
        assertEquals(Storage.LoadResult(0, null), storage.load(m))
        assertTrue(m.isEmpty)
    }
}

class CsvTest {

    private val today = LocalDate(2026, 9, 26)

    @Test
    fun amountsAreReadInEveryCommonStyle() {
        assertEquals(19900, CsvImporter.parseAmount("199"))
        assertEquals(129900, CsvImporter.parseAmount("R 1,299.00"))
        assertEquals(129950, CsvImporter.parseAmount("$1 299.50"))
        assertEquals(129950, CsvImporter.parseAmount("1299,50"))
        assertEquals(129950, CsvImporter.parseAmount("1.299,50"))
        assertEquals(129900000, CsvImporter.parseAmount("1,299,000"))
        assertFailsWith<IllegalArgumentException> { CsvImporter.parseAmount("free") }
        assertFailsWith<IllegalArgumentException> { CsvImporter.parseAmount("-5") }
    }

    @Test
    fun datesAreReadThreeWays() {
        assertEquals(LocalDate(2026, 10, 1), CsvImporter.parseDate("2026-10-01"))
        assertEquals(LocalDate(2026, 10, 1), CsvImporter.parseDate("2026/10/1"))
        assertEquals(LocalDate(2026, 10, 1), CsvImporter.parseDate("01/10/2026"))
        assertFailsWith<IllegalArgumentException> { CsvImporter.parseDate("31/02/2026") }
        assertFailsWith<IllegalArgumentException> { CsvImporter.parseDate("soon") }
    }

    @Test
    fun importReadsASpreadsheetAndExplainsSkippedRows() {
        val m = SubscriptionManager()
        m.add("Netflix", 19900, BillingCycle.MONTHLY, LocalDate(2026, 10, 1), "Streaming")
        val csv = "Service;Price;Frequency;Due Date;Category;Trial;Notes;Other\r\n" +
            "Spotify;79,99;monthly;28/09/2026;Music;no;\"family; plan\";x\r\n" +
            "netflix;199;monthly;;;;;\r\n" +
            ";5;;;;;;\r\n" +
            "Gym;lots;;;;;;\r\n" +
            "Disney+;0;Annual;2026-09-01;;yes;;\r\n" +
            ";;;;;;;\r\n"

        val result = CsvImporter.importText(csv, m, today)

        assertEquals(2, result.imported)
        assertEquals(listOf(
            "Row 3: \"netflix\" is already in your list.",
            "Row 4: it has no name.",
            "Row 5: the cost \"lots\" isn't an amount.",
        ), result.problems)
        val spotify = m.all.first { it.name == "Spotify" }
        assertEquals(7999, spotify.cost)
        assertEquals("family; plan", spotify.note)
        val disney = m.all.first { it.name == "Disney+" }
        assertEquals(BillingCycle.YEARLY, disney.cycle)
        assertEquals("Other", disney.category)
        // The trial ended on 1 Sep 2026, so it's now a paid subscription.
        assertEquals(LocalDate(2027, 9, 1), disney.nextPayment)
        assertTrue(!disney.freeTrial)
    }

    @Test
    fun importNeedsNameAndCostColumns() {
        val result = CsvImporter.importText("Title,Cost\nX,1\n", SubscriptionManager(), today)
        assertEquals(0, result.imported)
        assertEquals(listOf("The first row must name the columns, including Name and Cost."), result.problems)
        assertEquals(listOf("The file is empty."), CsvImporter.importText("", SubscriptionManager(), today).problems)
    }

    @Test
    fun exportThenImportGivesTheSameSubscriptions() {
        val m = SubscriptionManager()
        val a = m.add("Netflix, \"HD\"", 19900, BillingCycle.MONTHLY, LocalDate(2026, 10, 1), "Streaming")
        a.note = "line one\nline two"
        m.add("Office", 120000, BillingCycle.YEARLY, LocalDate(2027, 1, 15), "Software").freeTrial = true
        val csv = CsvExporter.toCsv(m.all)
        assertTrue(csv.startsWith("ID,Name,Category,Cost,Billing Cycle,Next Payment,Monthly Cost,Yearly Cost,Free Trial,Note,Currency\r\n"))
        assertTrue(csv.contains("1,\"Netflix, \"\"HD\"\"\",Streaming,199.00,Monthly,2026-10-01,199.00,2388.00,No,"))

        val copy = SubscriptionManager()
        val result = CsvImporter.importText(csv, copy, today)

        assertEquals(2, result.imported)
        assertEquals(m.all.map { listOf(it.name, it.cost, it.cycle, it.nextPayment, it.category, it.freeTrial, it.note) },
            copy.all.map { listOf(it.name, it.cost, it.cycle, it.nextPayment, it.category, it.freeTrial, it.note) })
    }

    @Test
    fun excelFilesInTheWindowsCharacterSetKeepTheirAccents() {
        // "Café,€5" in windows-1252.
        val bytes = byteArrayOf(0x43, 0x61, 0x66, 0xE9.toByte(), 0x2C, 0x80.toByte(), 0x35)
        assertEquals("Café,€5", TextDecoding.decode(bytes))
        assertEquals("Café", TextDecoding.decode("﻿Café".encodeToByteArray()))
    }
}

class FormatTest {

    private val today = LocalDate(2026, 9, 26)

    @Test
    fun moneyUsesTheCurrencySymbol() {
        assertEquals("1,234.50", Format("").money(123450))
        assertEquals("R 1,234.50", Format("R").money(123450))
        assertEquals("$1,234.50", Format("$").money(123450))
        assertEquals("-R 30.00", Format("R").money(-3000))
        assertEquals("+R 30.00", Format("R").signedMoney(3000))
    }

    @Test
    fun axisAmountsAreWhole() {
        assertEquals("R 1,500", Format("R").moneyWhole(150000))
        assertEquals("$1,500", Format("$").moneyWhole(149950))
        assertEquals("0", Format("").moneyWhole(49))
        assertEquals("Sep", Format.monthShort(LocalDate(2026, 9, 1)))
    }

    @Test
    fun budgetLineSaysHowMuchIsLeftOrOver() {
        assertEquals("No monthly budget set", Format("R").budgetLine(100000, null))
        assertEquals("88% of your R 1,200.00 budget · R 134.11 left", Format("R").budgetLine(106589, 120000))
        assertEquals("Over budget: 110% of your R 1,000.00 budget · R 100.00 over", Format("R").budgetLine(110000, 100000))
    }

    @Test
    fun describesPriceChanges() {
        assertEquals("169.00 → 199.00 (+30.00, +18%)",
            Format("").describe(PriceChange(today, 16900, 19900, BillingCycle.MONTHLY)))
        assertEquals("199.00 a month → 2,000.00 a year (-32.33 a month, -16%)",
            Format("").describe(PriceChange(today, 19900, 200000, BillingCycle.MONTHLY, BillingCycle.YEARLY)))
    }

    @Test
    fun datesAndDistances() {
        assertEquals("1 Oct 2026", Format.date(LocalDate(2026, 10, 1)))
        assertEquals("today", Format.dueIn(today, today))
        assertEquals("tomorrow", Format.dueIn(LocalDate(2026, 9, 27), today))
        assertEquals("in 5 days", Format.dueIn(LocalDate(2026, 10, 1), today))
        assertEquals("3 days ago", Format.dueIn(LocalDate(2026, 9, 23), today))
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "31st"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 31).map { Format.ordinal(it) })
    }

    @Test
    fun currencySymbolsThatWouldReadAsPartOfTheAmountAreRefused() {
        assertTrue(Format.isValidCurrencySymbol("R"))
        assertTrue(Format.isValidCurrencySymbol("US$"))
        listOf("", "R 1", "abcdef", "R.", "+", " ").forEach { assertTrue(!Format.isValidCurrencySymbol(it), it) }
    }
}

class SubscriptionFormTest {

    private val today = LocalDate(2026, 9, 26)

    @Test
    fun problemsAreExplained() {
        val ok = SubscriptionForm(name = "Netflix", cost = "199", date = LocalDate(2026, 10, 1))
        assertNull(ok.problem)
        assertEquals("Please enter a name.", ok.copy(name = " \n").problem)
        assertEquals("Please enter the cost as an amount, e.g. 99.99.", ok.copy(cost = "abc").problem)
        assertEquals("Please enter the cost as an amount, e.g. 99.99.", ok.copy(cost = "-5").problem)
        assertEquals("Please choose the date.", ok.copy(date = null).problem)
    }

    @Test
    fun addingKeepsFieldsOnOneLineAndRollsThePastDateForward() {
        val m = SubscriptionManager()
        val sub = SubscriptionForm(name = "Net\tflix", cost = "199", date = LocalDate(2026, 9, 1),
            note = "shared\nwith family", cycle = BillingCycle.MONTHLY).add(m, today)
        assertEquals("Net flix", sub.name)
        assertEquals("shared with family", sub.note)
        assertEquals("Other", sub.category)
        assertEquals(LocalDate(2026, 10, 1), sub.nextPayment)
    }

    @Test
    fun aNewPriceIsRecordedUnlessItsACorrection() {
        val m = SubscriptionManager()
        val sub = m.add("Netflix", 16900, BillingCycle.MONTHLY, LocalDate(2026, 10, 1), "Streaming")
        val form = SubscriptionForm.from(sub).copy(cost = "199.00")
        assertNotNull(form.pendingPriceChange(sub, today))
        val recorded = form.applyTo(sub, today)
        assertEquals(PriceChange(today, 16900, 19900, BillingCycle.MONTHLY), recorded)

        val fix = SubscriptionForm.from(sub).copy(cost = "189", recordPriceChange = false)
        assertNull(fix.applyTo(sub, today))
        assertEquals(18900, sub.cost)
        assertEquals(1, sub.priceChanges.size)
    }

    @Test
    fun anUnchangedFormChangesNothing() {
        val m = SubscriptionManager()
        val sub = m.add("Netflix", 19900, BillingCycle.MONTHLY, LocalDate(2027, 2, 28), "Streaming")
        sub.restoreBillingDay(31)
        sub.note = "family"
        val form = SubscriptionForm.from(sub)
        assertNull(form.pendingPriceChange(sub, today))
        assertNull(form.applyTo(sub, today))
        assertEquals(31, sub.billingDay)
        assertEquals("family", sub.note)
        assertTrue(sub.priceChanges.isEmpty())
    }

    @Test
    fun billingDescriptionSaysWhenPaymentsFall() {
        val form = SubscriptionForm(date = LocalDate(2026, 10, 1))
        assertEquals("Billed on the 1st of each month · in 5 days", form.billingDescription(today))
        assertEquals("Billed every year on 1 Oct · in 5 days", form.copy(cycle = BillingCycle.YEARLY).billingDescription(today))
        assertEquals("Billed every Thursday · in 5 days", form.copy(cycle = BillingCycle.WEEKLY).billingDescription(today))
        assertEquals("Billed on the 1st of each month · that date has passed, so the next one is 1 Oct 2026",
            form.copy(date = LocalDate(2026, 9, 1)).billingDescription(today))
        assertNull(SubscriptionForm().billingDescription(today))
    }
}
