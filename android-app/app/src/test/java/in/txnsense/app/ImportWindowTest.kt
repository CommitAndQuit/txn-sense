package `in`.txnsense.app

import `in`.txnsense.app.data.ImportWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ImportWindowTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()

    @Test fun three_calendar_months_are_not_ninety_days_and_end_at_scan_start() {
        val now = Instant.parse("2026-05-31T12:00:00Z").toEpochMilli()
        val range = ImportWindow.lastThreeMonths(now, ZoneId.of("UTC"))
        assertEquals(Instant.parse("2026-02-28T00:00:00Z").toEpochMilli(), range.start)
        assertEquals(now, range.end)
        assertTrue(range.contains(range.start))
        assertTrue(range.contains(now))
        assertFalse(range.contains(range.start - 1))
        assertFalse(range.contains(now + 1))
    }

    @Test fun first_refresh_covers_the_last_two_calendar_months() {
        val expectedStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusMonths(2)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val window = ImportWindow.lastTwoMonths(now, zone)
        assertEquals(expectedStart, window.start)
        assertEquals(now, window.end)
        assertTrue(window.contains(expectedStart))
        assertTrue(window.contains(now))
    }

    @Test fun catch_up_starts_five_minutes_before_the_previous_scan() {
        val lastScan = now - 3_600_000
        val window = ImportWindow.catchUp(lastScan, now, zone)
        assertEquals(lastScan - 300_000, window.start)
        assertEquals(now, window.end)
    }

    @Test fun catch_up_falls_back_to_two_months_without_a_usable_scan() {
        assertEquals(ImportWindow.lastTwoMonths(now, zone), ImportWindow.catchUp(null, now, zone))
        assertEquals(ImportWindow.lastTwoMonths(now, zone), ImportWindow.catchUp(now + 1, now, zone))
    }
}
