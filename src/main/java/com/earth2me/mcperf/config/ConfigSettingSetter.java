package com.earth2me.mcperf.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For the time being, this is purely to mark methods as used that would otherwise trigger a warning.
 * At some point in the future, this will likely serve a functional runtime purpose.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD })
public @interface ConfigSettingSetter {
}
