package com.r3.conclave.integrationtests.shading;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class ShadingTest {
    @Test
    public void makeSureKotlinIsNotATransitiveDependency() throws ClassNotFoundException {
        assertThatExceptionOfType(ClassNotFoundException.class)
                .isThrownBy(() -> Class.forName("kotlin.Pair"));
        Class.forName("com.r3.conclave.shaded.kotlin.Pair"); // Make sure the shaded one exists.
    }

    @Test
    public void makeSureJacksonIsNotATransitiveDependency() throws ClassNotFoundException {
        assertThatExceptionOfType(ClassNotFoundException.class)
                .isThrownBy(() -> Class.forName("com.fasterxml.jackson.databind.ObjectMapper"));
        Class.forName("com.r3.conclave.shaded.jackson.databind.ObjectMapper"); // Make sure the shaded one exists.
    }
}
