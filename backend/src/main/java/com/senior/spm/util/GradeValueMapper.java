package com.senior.spm.util;

import com.senior.spm.entity.RubricCriterion.GradingType;
import com.senior.spm.entity.ScrumGrade.ScrumGradeValue;

public final class GradeValueMapper {

    private GradeValueMapper() {}

    public static boolean validateGrade(GradingType type, String value) {
        if (value == null) return false;
        return switch (type) {
            case Binary -> value.equals("S") || value.equals("F");
            case Soft   -> {
                try { ScrumGradeValue.valueOf(value); yield true; }
                catch (IllegalArgumentException e) { yield false; }
            }
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
            case Soft -> {
                try { yield ScrumGradeValue.valueOf(value).toNumeric(); }
                catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid Soft grade: " + value);
                }
            }
        };
    }
}
