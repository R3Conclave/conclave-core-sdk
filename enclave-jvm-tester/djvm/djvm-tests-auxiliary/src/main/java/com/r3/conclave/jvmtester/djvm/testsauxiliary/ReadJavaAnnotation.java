package com.r3.conclave.jvmtester.djvm.testsauxiliary;

import java.util.function.Function;

public class ReadJavaAnnotation implements Function<String, String> {
    @Override
    public String apply(String input) {
        JavaAnnotation value = UserJavaData.class.getAnnotation(JavaAnnotation.class);
        return value == null ? null : value.toString();
    }
}
