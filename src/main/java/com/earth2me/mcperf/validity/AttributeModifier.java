package com.earth2me.mcperf.validity;

import lombok.Data;
import lombok.Getter;

import java.util.UUID;

@Data
public final class AttributeModifier {
    private final UUID uuid;
    private final String identifier;
    private final String name;
    private final double value;
    private final Operation operation;

    public String getAmountText() {
        if (operation == null) {
            return String.format("?? %.3f", value);
        } else {
            return operation.format(value);
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s", identifier, getAmountText());
    }

    public enum Operation {
        SUM(0, "+%.3f", false),
        MULTIPLICATIVE(1, "%.0f%%", true),
        ADDITIVE(2, "+%.0f%%", true),
        ;

        @Getter
        private final int id;
        @Getter
        private final String format;
        @Getter
        private boolean percentage;

        Operation(int id, String format, boolean percentage) {
            this.id = id;
            this.format = format;
            this.percentage = percentage;
        }

        public String format(double value) {
            if (percentage) {
                value *= 100;
            }
            return String.format(format, value);
        }

        public static Operation fromId(int id) {
            switch (id) {
                case 0:  return SUM;
                case 1:  return MULTIPLICATIVE;
                case 2:  return ADDITIVE;
                default: return null;
            }
        }
    }
}
