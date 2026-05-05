package com.senior.spm.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.senior.spm.entity.RubricCriterion.GradingType;
import com.senior.spm.entity.ScrumGrade.ScrumGradeValue;

class GradeValueMapperTest {

    // ── ScrumGradeValue.toNumeric() ───────────────────────────────────────────

    @Test
    void scrumGradeValue_A_toNumeric_returns100() {
        assertThat(ScrumGradeValue.A.toNumeric()).isEqualTo(100);
    }

    @Test
    void scrumGradeValue_B_toNumeric_returns80() {
        assertThat(ScrumGradeValue.B.toNumeric()).isEqualTo(80);
    }

    @Test
    void scrumGradeValue_C_toNumeric_returns60() {
        assertThat(ScrumGradeValue.C.toNumeric()).isEqualTo(60);
    }

    @Test
    void scrumGradeValue_D_toNumeric_returns50() {
        assertThat(ScrumGradeValue.D.toNumeric()).isEqualTo(50);
    }

    @Test
    void scrumGradeValue_F_toNumeric_returns0() {
        assertThat(ScrumGradeValue.F.toNumeric()).isZero();
    }

    // ── GradeValueMapper.validateGrade — Binary ───────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"S", "F"})
    void validateGrade_binary_validGrades_returnsTrue(String grade) {
        assertThat(GradeValueMapper.validateGrade(GradingType.Binary, grade)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "B", "C", "D", "X", ""})
    void validateGrade_binary_invalidGrades_returnsFalse(String grade) {
        assertThat(GradeValueMapper.validateGrade(GradingType.Binary, grade)).isFalse();
    }

    @Test
    void validateGrade_binary_nullValue_returnsFalse() {
        assertThat(GradeValueMapper.validateGrade(GradingType.Binary, null)).isFalse();
    }

    // ── GradeValueMapper.validateGrade — Soft ────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"A", "B", "C", "D", "F"})
    void validateGrade_soft_validGrades_returnsTrue(String grade) {
        assertThat(GradeValueMapper.validateGrade(GradingType.Soft, grade)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"S", "X", "G", ""})
    void validateGrade_soft_invalidGrades_returnsFalse(String grade) {
        assertThat(GradeValueMapper.validateGrade(GradingType.Soft, grade)).isFalse();
    }

    @Test
    void validateGrade_soft_nullValue_returnsFalse() {
        assertThat(GradeValueMapper.validateGrade(GradingType.Soft, null)).isFalse();
    }

    // ── GradeValueMapper.toNumeric — Binary ──────────────────────────────────

    @Test
    void toNumeric_binary_S_returns100() {
        assertThat(GradeValueMapper.toNumeric(GradingType.Binary, "S")).isEqualTo(100);
    }

    @Test
    void toNumeric_binary_F_returns0() {
        assertThat(GradeValueMapper.toNumeric(GradingType.Binary, "F")).isZero();
    }

    @Test
    void toNumeric_binary_invalidGrade_throwsIllegalArgument() {
        assertThatThrownBy(() -> GradeValueMapper.toNumeric(GradingType.Binary, "A"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toNumeric_nullValue_throwsIllegalArgument() {
        assertThatThrownBy(() -> GradeValueMapper.toNumeric(GradingType.Binary, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── GradeValueMapper.toNumeric — Soft ────────────────────────────────────

    @ParameterizedTest
    @CsvSource({"A, 100", "B, 80", "C, 60", "D, 50", "F, 0"})
    void toNumeric_soft_validGrades_returnsCorrectValue(String grade, int expected) {
        assertThat(GradeValueMapper.toNumeric(GradingType.Soft, grade)).isEqualTo(expected);
    }

    @Test
    void toNumeric_soft_invalidGrade_throwsIllegalArgument() {
        assertThatThrownBy(() -> GradeValueMapper.toNumeric(GradingType.Soft, "S"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
