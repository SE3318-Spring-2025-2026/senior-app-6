package com.senior.spm.util;

import com.senior.spm.entity.RubricCriterion.GradingType;

public final class GradeValueMapper {

    private GradeValueMapper() {}

    public static boolean validateGrade(GradingType type, String value) {
        if (value == null) return false;
        return switch (type) {
            case Binary -> value.equals("S") || value.equals("F");
            case Soft   -> value.equals("A") || value.equals("B") || value.equals("C")
                        || value.equals("D") || value.equals("F");
        };
    }

    public static int toNumeric(GradingType type, String value) {
        if (value == null) throw new IllegalArgumentException("Grade value must not be null");
        return switch (type) {
            case Binary -> switch (value) {
                case "S" -> 100;
                case "F" -> 0;
                default  -> throw new IllegalArgumentException("Invalid Binary grade: " + value);
            };
            case Soft -> switch (value) {
                case "A" -> 100;
                case "B" -> 80;
                case "C" -> 60;
                case "D" -> 50;
                case "F" -> 0;
                default  -> throw new IllegalArgumentException("Invalid Soft grade: " + value);
            };
        };
    }
}
