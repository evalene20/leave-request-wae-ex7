package com.example.leave.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class WorkingDaysTest {

    @Test
    void threeWeekdaysCountAsThree() {
        // Mon 2 Nov 2026 - Wed 4 Nov 2026
        assertEquals(3, WorkingDays.between(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4)));
    }

    @Test
    void weekendOnlyHasNoWorkingDays() {
        // Sat 7 Nov 2026 - Sun 8 Nov 2026
        assertEquals(0, WorkingDays.between(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8)));
    }

    @Test
    void rangeSpanningAWeekendSkipsIt() {
        // Fri 6 Nov - Tue 10 Nov 2026 -> Fri, Mon, Tue
        assertEquals(3, WorkingDays.between(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 10)));
    }

    @Test
    void fullWeeksCountFiveEach() {
        // Mon 2 Nov - Sun 15 Nov 2026 = two full weeks
        assertEquals(10, WorkingDays.between(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 15)));
    }

    @Test
    void endBeforeStartIsZero() {
        assertEquals(0, WorkingDays.between(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 2)));
    }

    @Test
    void singleWeekday() {
        assertEquals(1, WorkingDays.between(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2)));
    }
}
