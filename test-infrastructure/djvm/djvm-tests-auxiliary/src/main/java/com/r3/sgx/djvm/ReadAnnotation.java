package com.r3.sgx.djvm;

import java.util.function.Function;

public class ReadAnnotation implements Function<String, String> {
    @Override
    public String apply(String input) {
        JavaAnnotation value = UserJavaData.class.getAnnotation(JavaAnnotation.class);
        return value == null ? null : value.toString();
    }
}
