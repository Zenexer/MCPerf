package com.earth2me.mcperf.util;

import lombok.Data;

@Data
public final class Tuple<A, B> {
    private final A a;
    private final B b;
}
