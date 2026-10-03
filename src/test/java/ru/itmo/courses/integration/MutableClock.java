package ru.itmo.courses.integration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class MutableClock extends Clock {
    private volatile Instant instant = Instant.parse("2026-01-10T12:00:00Z");

    void set(String instant) {
        this.instant = Instant.parse(instant);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
