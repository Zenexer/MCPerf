package com.earth2me.mcperf.util.concurrent;

public interface Callback<T> {
    void call(T result) throws Exception;
}
