package com.r3.conclave.jvmtester.djvm.tests;

import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase;
import com.r3.conclave.jvmtester.djvm.tests.util.Log;
import com.r3.conclave.jvmtester.djvm.tests.util.SerializationUtils;
import com.r3.conclave.jvmtester.api.EnclaveJvmTest;
import org.jetbrains.annotations.NotNull;

import java.time.*;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaTimeTest extends DJVMBase {

    private static final long OFFSET_SECONDS = 5000L;

    public static class InstantTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            Instant instant = Instant.ofEpochSecond(1L, 1L);
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Class<Function<Object, String>> temporalToStringClass =
                            (Class<Function<Object, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$TemporalToString")
                                    .getType();
                    Function<? super Instant, String> stringTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, temporalToStringClass);
                    String toStringResult = stringTask.apply(instant);
                    assertThat(toStringResult).isEqualTo(instant.toString());

                    Class<Function<Object, Object>> identityTransformationClass =
                            (Class<Function<Object, Object>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$IdentityTransformation")
                                    .getType();
                    Function<? super Instant, ?> identityTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, identityTransformationClass);
                    Object identityResult = identityTask.apply(instant);
                    assertThat(identityResult).isEqualTo(instant);
                    assertThat(identityResult).isNotSameAs(instant);
                    output.set(toStringResult);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }
        
        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.InstantTest().assertResult(testResult);
        }
    }

    public static class DurationTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            Duration duration = Duration.ofHours(2);
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Class<Function<Object, String>> temporalToStringClass =
                            (Class<Function<Object, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$TemporalToString")
                                    .getType();
                    Function<? super Duration, String> stringTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, temporalToStringClass);

                    String toStringResult = stringTask.apply(duration);
                    assertThat(toStringResult).isEqualTo(duration.toString());

                    Class<Function<Object, Object>> identityTransformationClass =
                            (Class<Function<Object, Object>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$IdentityTransformation")
                                    .getType();
                    Function<? super Duration, ?> identityTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, identityTransformationClass);
                    Object identityResult = identityTask.apply(duration);
                    assertThat(identityResult).isEqualTo(duration);
                    assertThat(identityResult).isNotSameAs(duration);
                    output.set(toStringResult);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }
        
        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.DurationTest().assertResult(testResult);
        }
    }

    public static class LocalDateTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            LocalDate localDate = LocalDate.of(1971, 2, 3);
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Class<Function<Object, String>> temporalToStringClass =
                            (Class<Function<Object, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$TemporalToString")
                                    .getType();
                    Function<? super LocalDate, String> stringTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, temporalToStringClass);
                    String toStringResult = stringTask.apply(localDate);
                    assertThat(toStringResult).isEqualTo(localDate.toString());

                    Class<Function<Object, Object>> identityTransformationClass =
                            (Class<Function<Object, Object>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$IdentityTransformation")
                                    .getType();
                    Function<? super LocalDate, ?> identityTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, identityTransformationClass);
                    Object identityResult = identityTask.apply(localDate);
                    assertThat(identityResult).isEqualTo(localDate);
                    assertThat(identityResult).isNotSameAs(localDate);
                    output.set(toStringResult);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }
        
        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.LocalDateTest().assertResult(testResult);
        }
    }

    public static class LocalTimeTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            LocalTime localTime = LocalTime.of(1, 2, 3);
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Class<Function<Object, String>> temporalToStringClass =
                            (Class<Function<Object, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$TemporalToString")
                            .getType();
                    Function<? super LocalTime, String> stringTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, temporalToStringClass);
                    String toStringResult = stringTask.apply(localTime);
                    assertThat(toStringResult).isEqualTo(localTime.toString());

                    Class<Function<Object, Object>> identityTransformationClass =
                            (Class<Function<Object, Object>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$IdentityTransformation")
                                    .getType();
                    Function<? super LocalTime, ?> identityTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, identityTransformationClass);
                    Object identityResult = identityTask.apply(localTime);
                    assertThat(identityResult).isEqualTo(localTime);
                    assertThat(identityResult).isNotSameAs(localTime);
                    output.set(toStringResult);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.LocalTimeTest().assertResult(testResult);
        }
    }

    public static class LocalDateTimeTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            LocalDateTime localDateTime = LocalDateTime.of(1971, 2, 3, 4, 5, 6, 7);
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Class<Function<Object, String>> temporalToStringClass =
                            (Class<Function<Object, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$TemporalToString")
                                    .getType();
                    Function<? super LocalDateTime, String> stringTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, temporalToStringClass);
                    String toStringResult = stringTask.apply(localDateTime);
                    assertThat(toStringResult).isEqualTo(localDateTime.toString());

                    Class<Function<Object, Object>> identityTransformationClass =
                            (Class<Function<Object, Object>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$IdentityTransformation")
                                    .getType();
                    Function<? super LocalDateTime, ?> identityTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, identityTransformationClass);
                    Object identityResult = identityTask.apply(localDateTime);
                    assertThat(identityResult).isEqualTo(localDateTime);
                    assertThat(identityResult).isNotSameAs(localDateTime);
                    output.set(toStringResult);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.LocalDateTimeTest().assertResult(testResult);
        }
    }

    public static class MonthDayTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            MonthDay monthDay = MonthDay.of(1, 2);
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Class<Function<Object, String>> temporalToStringClass =
                            (Class<Function<Object, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$TemporalToString")
                                    .getType();
                    Function<? super MonthDay, String> stringTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, temporalToStringClass);
                    String toStringResult = stringTask.apply(monthDay);
                    assertThat(toStringResult).isEqualTo(monthDay.toString());

                    Class<Function<Object, Object>> identityTransformationClass =
                            (Class<Function<Object, Object>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$IdentityTransformation")
                                    .getType();
                    Function<? super MonthDay, ?> identityTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, identityTransformationClass);
                    Object identityResult = identityTask.apply(monthDay);
                    assertThat(identityResult).isEqualTo(monthDay);
                    assertThat(identityResult).isNotSameAs(monthDay);
                    output.set(toStringResult);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.MonthDayTest().assertResult(testResult);
        }
    }

    public static class OffsetDateTimeTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            OffsetDateTime offsetDateTime = OffsetDateTime.of(LocalDateTime.of(1971, 2, 3, 4, 5, 6, 7),
                    ZoneOffset.ofHours(7));
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Class<Function<Object, String>> temporalToStringClass =
                            (Class<Function<Object, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$TemporalToString")
                                    .getType();
                    Function<? super OffsetDateTime, String> stringTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, temporalToStringClass);
                    String toStringResult = stringTask.apply(offsetDateTime);
                    assertThat(toStringResult).isEqualTo(offsetDateTime.toString());

                    Class<Function<Object, Object>> identityTransformationClass =
                            (Class<Function<Object, Object>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$IdentityTransformation")
                                    .getType();
                    Function<? super OffsetDateTime, ?> identityTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, identityTransformationClass);
                    Object identityResult = identityTask.apply(offsetDateTime);
                    assertThat(identityResult).isEqualTo(offsetDateTime);
                    assertThat(identityResult).isNotSameAs(offsetDateTime);
                    output.set(toStringResult);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.OffsetDateTimeTest().assertResult(testResult);
        }
    }

    public static class OffsetTimeTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            OffsetTime offsetTime = OffsetTime.of(LocalTime.of(1, 2, 3), ZoneOffset.ofHours(7));
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Class<Function<Object, String>> temporalToStringClass =
                            (Class<Function<Object, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$TemporalToString")
                                    .getType();
                    Function<? super OffsetTime, String> stringTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, temporalToStringClass);
                    String toStringResult = stringTask.apply(offsetTime);
                    assertThat(toStringResult).isEqualTo(offsetTime.toString());

                    Class<Function<Object, Object>> identityTransformationClass =
                            (Class<Function<Object, Object>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$IdentityTransformation")
                                    .getType();
                    Function<? super OffsetTime, ?> identityTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, identityTransformationClass);
                    Object identityResult = identityTask.apply(offsetTime);
                    assertThat(identityResult).isEqualTo(offsetTime);
                    assertThat(identityResult).isNotSameAs(offsetTime);
                    output.set(toStringResult);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.OffsetTimeTest().assertResult(testResult);
        }
    }

    public static class PeriodTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            Period period = Period.of(1, 2, 3);
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Class<Function<Object, String>> temporalToStringClass =
                            (Class<Function<Object, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$TemporalToString")
                                    .getType();
                    Function<? super Period, String> stringTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, temporalToStringClass);
                    String toStringResult = stringTask.apply(period);
                    assertThat(toStringResult).isEqualTo(period.toString());

                    Class<Function<Object, Object>> identityTransformationClass =
                            (Class<Function<Object, Object>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$IdentityTransformation")
                                    .getType();
                    Function<? super Period, ?> identityTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, identityTransformationClass);
                    Object identityResult = identityTask.apply(period);
                    assertThat(identityResult).isEqualTo(period);
                    assertThat(identityResult).isNotSameAs(period);
                    output.set(toStringResult);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.PeriodTest().assertResult(testResult);
        }
    }

    public static class YearTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            Year year = Year.of(1971);
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Class<Function<Object, String>> temporalToStringClass =
                            (Class<Function<Object, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$TemporalToString")
                                    .getType();
                    Function<? super Year, String> stringTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, temporalToStringClass);
                    String toStringResult = stringTask.apply(year);
                    assertThat(toStringResult).isEqualTo(year.toString());

                    Class<Function<Object, Object>> identityTransformationClass =
                            (Class<Function<Object, Object>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$IdentityTransformation")
                                    .getType();
                    Function<? super Year, ?> identityTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, identityTransformationClass);
                    Object identityResult = identityTask.apply(year);
                    assertThat(identityResult).isEqualTo(year);
                    assertThat(identityResult).isNotSameAs(year);
                    output.set(toStringResult);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.YearTest().assertResult(testResult);
        }
    }

    public static class YearMonthTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            YearMonth yearMonth = YearMonth.of(1971, 2);
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Class<Function<Object, String>> temporalToStringClass =
                            (Class<Function<Object, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$TemporalToString")
                                    .getType();
                    Function<? super YearMonth, String> stringTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, temporalToStringClass);
                    String toStringResult = stringTask.apply(yearMonth);
                    assertThat(toStringResult).isEqualTo(yearMonth.toString());

                    Class<Function<Object, Object>> identityTransformationClass =
                            (Class<Function<Object, Object>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$IdentityTransformation")
                                    .getType();
                    Function<? super YearMonth, ?> identityTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, identityTransformationClass);
                    Object identityResult = identityTask.apply(yearMonth);
                    assertThat(identityResult).isEqualTo(yearMonth);
                    assertThat(identityResult).isNotSameAs(yearMonth);
                    output.set(toStringResult);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.YearMonthTest().assertResult(testResult);
        }
    }

    public static class ZonedDateTimeTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            ZonedDateTime zonedDateTime = ZonedDateTime.of(LocalDateTime.of(1971, 2, 3, 4, 5, 6, 7),
                    ZoneId.of("UTC"));
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Class<Function<Object, String>> temporalToStringClass =
                            (Class<Function<Object, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$TemporalToString")
                                    .getType();
                    Function<? super ZonedDateTime, String> stringTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, temporalToStringClass);
                    String toStringResult = stringTask.apply(zonedDateTime);
                    assertThat(toStringResult).isEqualTo(zonedDateTime.toString());

                    Class<Function<Object, Object>> identityTransformationClass =
                            (Class<Function<Object, Object>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$IdentityTransformation")
                                    .getType();
                    Function<? super ZonedDateTime, ?> identityTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, identityTransformationClass);
                    Object identityResult = identityTask.apply(zonedDateTime);
                    assertThat(identityResult).isEqualTo(zonedDateTime);
                    assertThat(identityResult).isNotSameAs(zonedDateTime);
                    output.set(toStringResult);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.ZonedDateTimeTest().assertResult(testResult);
        }
    }

    public static class ZoneOffsetTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            ZoneOffset zoneOffset = ZoneOffset.ofHours(7);
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Class<Function<Object, String>> temporalToStringClass =
                            (Class<Function<Object, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$TemporalToString")
                                    .getType();
                    Function<? super ZoneOffset, String> stringTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, temporalToStringClass);
                    String toStringResult = stringTask.apply(zoneOffset);
                    assertThat(toStringResult).isEqualTo(zoneOffset.toString());

                    Class<Function<Object, Object>> identityTransformationClass =
                            (Class<Function<Object, Object>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$IdentityTransformation")
                                    .getType();
                    Function<? super ZoneOffset, ?> identityTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, identityTransformationClass);
                    Object identityResult = identityTask.apply(zoneOffset);
                    assertThat(identityResult).isEqualTo(zoneOffset);
                    assertThat(identityResult).isSameAs(zoneOffset);
                    output.set(toStringResult);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.ZoneOffsetTest().assertResult(testResult);
        }
    }

    public static class AllZoneIDsTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();


                    Class<Function<Object, String[]>> allZoneIDsClass
                            = (Class<Function<Object, String[]>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$AllZoneIDs")
                            .getType();
                    Function<?, String[]> allZoneIDs = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, allZoneIDsClass);
                    String[] zoneIDs = allZoneIDs.apply(null);
                    assertThat(zoneIDs).hasSize(600);
                    output.set(zoneIDs.length);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeInt(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.AllZoneIDsTest().assertResult(testResult);
        }
    }

    public static class DefaultZoneIDTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();
                    Class<? extends Function<Object, String>> defaultZoneIDClass =
                            (Class<? extends Function<Object, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$DefaultZoneId")
                                    .getType();
                    Function<?, String> defaultZoneIdTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, defaultZoneIDClass);
                    String defaultZoneID = defaultZoneIdTask.apply(null);
                    assertThat(defaultZoneID).isEqualTo("UTC");
                    output.set(defaultZoneID);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.DefaultZoneIDTest().assertResult(testResult);
        }
    }

    public static class DefaultTimeZoneTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();
                    Class<Function<Date, String>> defaultTimeZoneClass =
                            (Class<Function<Date, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$DefaultTimeZone")
                                    .getType();
                    Function<?, String> defaultTimeZoneTask = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, defaultTimeZoneClass);
                    String defaultTimeZone = defaultTimeZoneTask.apply(null);
                    assertThat(defaultTimeZone).isEqualTo("Coordinated Universal Time");
                    output.set(defaultTimeZone);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.DefaultTimeZoneTest().assertResult(testResult);
        }
    }

    public static class DateTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Date date = new Date(1);
            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Class<Function<Date, String>> showDateClass =
                            (Class<Function<Date, String>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$ShowDate")
                                    .getType();
                    Function<Date, String> showDate = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, showDateClass);
                    String result = showDate.apply(date);
                    assertThat(result).isEqualTo(date.toString());
                    output.set(result);
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeString(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.DateTest().assertResult(testResult);
        }
    }

    public static class ReturningDateTestEnclaveTest extends DJVMBase implements EnclaveJvmTest {

        @Override
        public Object apply(Object input) {
            AtomicReference<Object> output = new AtomicReference<>();
            Date date = new Date(1);
            Date later = new Date(date.getTime() + OFFSET_SECONDS);

            sandbox(ctx -> {
                try {
                    Function<? super Object, ? extends Function<? super Object, ?>> taskFactory = ctx.getClassLoader().createTaskFactory();

                    Class<Function<Date, Date>> addToDateClass =
                            (Class<Function<Date, Date>>) loadClass(ctx, "com.r3.conclave.jvmtester.djvm.testsauxiliary.tests.JavaTimeTest$AddToDate")
                                    .getType();
                    Function<Date, Date> addToDate = DJVMBase.typedTaskFor(ctx.getClassLoader(), taskFactory, addToDateClass);
                    Date result = addToDate.apply(date);
                    assertThat(later).isNotSameAs(result);
                    assertThat(later).isEqualTo(result);
                    output.set(result.getTime());
                } catch (Throwable throwable) {
                    output.set(Log.recursiveStackTrace(throwable, this.getClass().getCanonicalName()));
                }
                return null;
            });
            return output.get();
        }

        @Override
        public byte[] serializeTestOutput(Object output) {
            return SerializationUtils.serializeLong(output).toByteArray();
        }

        @Override
        public void assertResult(@NotNull byte[] testResult) {
            new com.r3.conclave.jvmtester.djvm.asserters.JavaTimeTest.ReturningDateTest().assertResult(testResult);
        }
    }
}
