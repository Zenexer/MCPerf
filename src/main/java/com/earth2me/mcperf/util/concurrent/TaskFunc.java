package com.earth2me.mcperf.util.concurrent;

import java.util.concurrent.Callable;
import java.util.logging.Level;

@FunctionalInterface
public interface TaskFunc extends Callable<Object>, Runnable {
    void attempt() throws Exception;

    default void interrupted() {
    }

    default void exception(Exception exception) {
        Tasks.async(() -> Tasks.getLogger().log(Level.SEVERE, "Uncaught exception while executing task", exception));
    }

    default void error(Error error) {
        Tasks.async(() -> Tasks.getLogger().log(Level.SEVERE, "Uncaught error while executing task", error));
    }

    default Object call() throws Exception {
        attempt();
        return null;
    }

    default void run() {
        try {
            attempt();
        } catch (InterruptedException ignored) {
            interrupted();
        } catch (Exception e) {
            exception(e);
        } catch (Error e) {
            error(e);
        }
    }
}
