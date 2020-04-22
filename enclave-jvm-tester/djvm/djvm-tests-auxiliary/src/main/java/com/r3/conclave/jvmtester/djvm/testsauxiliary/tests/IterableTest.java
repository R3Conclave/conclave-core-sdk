package com.r3.conclave.jvmtester.djvm.testsauxiliary.tests;

import java.util.ArrayList;
import java.util.function.Function;

public class IterableTest {
    public static class Create implements Function<Object, Iterable<String>> {

        @Override
        public Iterable<String> apply(Object o) {
            return new ArrayList<>();
        }
    }
}
