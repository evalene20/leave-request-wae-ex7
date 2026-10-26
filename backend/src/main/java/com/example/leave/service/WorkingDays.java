package com.example.leave.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Counts Monday-Friday days in an inclusive date range (no public-holiday calendar). */
public final class WorkingDays {

    private WorkingDays() {}

    public static int between(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        long total = ChronoUnit.DAYS.between(start, end) + 1;
        long fullWeeks = total / 7;
        long remainder = total % 7;
        long count = fullWeeks * 5;
        LocalDate day = start.plusDays(fullWeeks * 7);
        for (long i = 0; i < remainder; i++) {
            DayOfWeek dow = day.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
            day = day.plusDays(1);
        }
        return (int) count;
    }
}
