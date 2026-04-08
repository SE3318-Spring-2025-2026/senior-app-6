package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.senior.spm.entity.SystemConfig;
import com.senior.spm.exception.TermConfigNotFoundException;
import com.senior.spm.repository.SystemConfigRepository;

@ExtendWith(MockitoExtension.class)
class TermConfigServiceTest {

    @Mock
    private SystemConfigRepository systemConfigRepository;

    @InjectMocks
    private TermConfigService termConfigService;

    // ── getActiveTermId ──────────────────────────────────────────────────────

    @Test
    void getActiveTermId_keyExists_returnsValue() {
        SystemConfig config = configEntry("active_term_id", "2026-SPRING");
        when(systemConfigRepository.findByConfigKey("active_term_id"))
                .thenReturn(Optional.of(config));

        assertThat(termConfigService.getActiveTermId()).isEqualTo("2026-SPRING");
    }

    @Test
    void getActiveTermId_keyMissing_throwsTermConfigNotFoundException() {
        // Requirement: both getActiveTermId() and getMaxTeamSize() throw
        // TermConfigNotFoundException (HTTP 500) if the key is missing — CLAUDE.md
        when(systemConfigRepository.findByConfigKey("active_term_id"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> termConfigService.getActiveTermId())
                .isInstanceOf(TermConfigNotFoundException.class)
                .hasMessageContaining("active_term_id");
    }

    // ── getMaxTeamSize ───────────────────────────────────────────────────────

    @Test
    void getMaxTeamSize_keyExists_returnsInteger() {
        SystemConfig config = configEntry("max_team_size", "5");
        when(systemConfigRepository.findByConfigKey("max_team_size"))
                .thenReturn(Optional.of(config));

        assertThat(termConfigService.getMaxTeamSize()).isEqualTo(5);
    }

    @Test
    void getMaxTeamSize_keyMissing_throwsTermConfigNotFoundException() {
        when(systemConfigRepository.findByConfigKey("max_team_size"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> termConfigService.getMaxTeamSize())
                .isInstanceOf(TermConfigNotFoundException.class)
                .hasMessageContaining("max_team_size");
    }

    @Test
    void getMaxTeamSize_valueBoundary_one_returnsOne() {
        // P2 rule: max_team_size enforced at 1 should allow no invitations
        SystemConfig config = configEntry("max_team_size", "1");
        when(systemConfigRepository.findByConfigKey("max_team_size"))
                .thenReturn(Optional.of(config));

        assertThat(termConfigService.getMaxTeamSize()).isEqualTo(1);
    }

    @Test
    void getMaxTeamSize_valueBoundary_large_returnsValue() {
        SystemConfig config = configEntry("max_team_size", "100");
        when(systemConfigRepository.findByConfigKey("max_team_size"))
                .thenReturn(Optional.of(config));

        assertThat(termConfigService.getMaxTeamSize()).isEqualTo(100);
    }

    /**
     * BUG: getMaxTeamSize() does not guard against non-numeric config_value.
     * Integer.parseInt("abc") throws NumberFormatException — not TermConfigNotFoundException.
     * The raw NumberFormatException bypasses @ResponseStatus and GlobalExceptionHandler,
     * producing an unstructured 500 with a cryptic message.
     *
     * Suggested Fix: wrap parseInt in try-catch and re-throw as TermConfigNotFoundException.
     *
     *   try {
     *       return Integer.parseInt(config.getConfigValue().trim());
     *   } catch (NumberFormatException e) {
     *       throw new TermConfigNotFoundException(
     *           "max_team_size config value is not a valid integer: " + config.getConfigValue());
     *   }
     */
    @Test
    void getMaxTeamSize_valueNotNumeric_shouldThrowTermConfigNotFoundException() {
        SystemConfig config = configEntry("max_team_size", "abc");
        when(systemConfigRepository.findByConfigKey("max_team_size"))
                .thenReturn(Optional.of(config));

        // FAILS until the fix above is applied — NumberFormatException leaks instead.
        assertThatThrownBy(() -> termConfigService.getMaxTeamSize())
                .isInstanceOf(TermConfigNotFoundException.class)
                .hasMessageContaining("not a valid integer");
    }

    @Test
    void getMaxTeamSize_valueWithWhitespace_shouldThrowTermConfigNotFoundException() {
        // " 4 " — Integer.parseInt does not trim, same root cause.
        SystemConfig config = configEntry("max_team_size", " 4 ");
        when(systemConfigRepository.findByConfigKey("max_team_size"))
                .thenReturn(Optional.of(config));

        // FAILS until the fix above is applied.
        assertThatThrownBy(() -> termConfigService.getMaxTeamSize())
                .isInstanceOf(TermConfigNotFoundException.class)
                .hasMessageContaining("not a valid integer");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static SystemConfig configEntry(String key, String value) {
        SystemConfig c = new SystemConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        return c;
    }
}
