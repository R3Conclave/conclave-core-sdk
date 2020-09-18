package com.r3.conclave.integrationtests.djvm.sandboxtests.util;

import java.util.function.Function;

public class ReadJavaAnnotation implements Function<String, String> {
    @Override
    public String apply(String input) {
        JavaAnnotation value = UserJavaData.class.getAnnotation(JavaAnnotation.class);
        return value == null ? null : value.toString();
    }
}
