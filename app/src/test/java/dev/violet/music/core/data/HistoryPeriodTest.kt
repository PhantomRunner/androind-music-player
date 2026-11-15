package dev.violet.music.core.data

import dev.violet.music.core.model.HistoryPeriod
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class HistoryPeriodTest {
    @Test
    fun calendarDaysUseZoneBoundariesAcrossDst() {
        val now = Instant.parse("2026-03-30T12:00:00Z")
        val zone = ZoneId.of("Europe/Dublin")
        assertEquals(
            Instant.parse("2026-03-24T00:00:00Z").toEpochMilli(),
            HistoryPeriod.WEEK.start(now, zone),
        )
        assertEquals(
            Instant.parse("2026-03-29T23:00:00Z").toEpochMilli(),
            HistoryPeriod.WEEK.start(now, zone) + 6 * 86_400_000L - 3_600_000,
        )
    }

    @Test
    fun sameInstantHasDifferentLocalCalendarBoundaries() {
        val now = Instant.parse("2026-11-15T01:00:00Z")
        assertNotEquals(
            HistoryPeriod.WEEK.start(now, ZoneId.of("Asia/Tokyo")),
            HistoryPeriod.WEEK.start(now, ZoneId.of("America/Los_Angeles")),
        )
        assertEquals(0, HistoryPeriod.ALL.start(now, ZoneId.of("UTC")))
    }
}
