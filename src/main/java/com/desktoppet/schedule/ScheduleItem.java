package com.desktoppet.schedule;

import java.time.LocalDate;

public record ScheduleItem(long id, LocalDate date, String title, boolean done) {
}
