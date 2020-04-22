package com.r3.conclave.jvmtester.djvm.testsauxiliary.tests;

import java.time.ZoneId;
import java.time.zone.ZoneRulesProvider;
import java.util.Date;
import java.util.TimeZone;
import java.util.function.Function;

public class JavaTimeTest {
    public static final long OFFSET_SECONDS = 5000L;

    public static class TemporalToString implements Function<Object, String> {
        @Override
        public String apply(Object temporal) {
            return temporal.toString();
        }
    }

    public static class IdentityTransformation implements Function<Object, Object> {
        @Override
        public Object apply(Object temporal) {
            return temporal;
        }
    }

    public static class AllZoneIDs implements Function<Object, String[]> {
        @Override
        public String[] apply(Object o) {
            return ZoneRulesProvider.getAvailableZoneIds().toArray(new String[0]);
        }
    }

    public static class DefaultZoneId implements Function<Object, String> {
        @Override
        public String apply(Object o) {
            return ZoneId.systemDefault().getId();
        }
    }

    public static class DefaultTimeZone implements Function<Object, String> {
        @Override
        public String apply(Object o) {
            return TimeZone.getDefault().getDisplayName();
        }
    }

    public static class ShowDate implements Function<Date, String> {
        @Override
        public String apply(Date date) {
            return date.toString();
        }
    }

    public static class AddToDate implements Function<Date, Date> {
        @Override
        public Date apply(Date date) {
            return new Date(date.getTime() + OFFSET_SECONDS);
        }
    }
}
