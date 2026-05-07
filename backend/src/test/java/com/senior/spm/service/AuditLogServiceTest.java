package com.senior.spm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.senior.spm.entity.AuditLog;
import com.senior.spm.entity.AuditLog.Outcome;
import com.senior.spm.entity.AuditLog.UserType;
import com.senior.spm.repository.AuditLogRepository;

class AuditLogServiceTest {

    // -------------------------------------------------------------------------
    // Unit tests — verify record() saves correct data
    // -------------------------------------------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    class UnitTests {

        @Mock
        AuditLogRepository auditLogRepository;

        @InjectMocks
        AuditLogService auditLogService;

        @Test
        void record_savesAuditLogWithCorrectFields() {
            UUID userId = UUID.randomUUID();
            ArgumentCaptor<AuditLog> captor = forClass(AuditLog.class);
            when(auditLogRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            auditLogService.record(userId, UserType.STAFF, "STAFF_LOGIN", Outcome.SUCCESS, "10.0.0.1");

            AuditLog saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getUserType()).isEqualTo(UserType.STAFF);
            assertThat(saved.getAction()).isEqualTo("STAFF_LOGIN");
            assertThat(saved.getOutcome()).isEqualTo(Outcome.SUCCESS);
            assertThat(saved.getIpAddress()).isEqualTo("10.0.0.1");
            assertThat(saved.getOccurredAt()).isNotNull();
        }

        @Test
        void record_allowsNullUserIdAndIpAddress() {
            ArgumentCaptor<AuditLog> captor = forClass(AuditLog.class);
            when(auditLogRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            auditLogService.record(null, UserType.STAFF, "STAFF_LOGIN", Outcome.FAILURE, null);

            AuditLog saved = captor.getValue();
            assertThat(saved.getUserId()).isNull();
            assertThat(saved.getIpAddress()).isNull();
            assertThat(saved.getOutcome()).isEqualTo(Outcome.FAILURE);
        }

        @Test
        void record_doesNotPropagateException_whenAuditWriteFails() {
            doThrow(new RuntimeException("DB unavailable"))
                .when(auditLogRepository).save(org.mockito.ArgumentMatchers.any());

            assertThatCode(() ->
                auditLogService.record(UUID.randomUUID(), UserType.STAFF, "STAFF_LOGIN", Outcome.SUCCESS, null)
            ).doesNotThrowAnyException();
        }
    }

    // -------------------------------------------------------------------------
    // Integration test — REQUIRES_NEW: audit row survives outer tx rollback
    // -------------------------------------------------------------------------

    @Nested
    @SpringBootTest
    @TestPropertySource(locations = "classpath:test.properties")
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
    class RequiresNewIntegrationTest {

        @Autowired
        AuditLogService auditLogService;

        @Autowired
        AuditLogRepository auditLogRepository;

        @Autowired
        PlatformTransactionManager transactionManager;

        @Test
        void record_commitsSeparately_whenCallerTransactionRollsBack() {
            UUID userId = UUID.randomUUID();
            TransactionTemplate outerTx = new TransactionTemplate(transactionManager);

            // Outer transaction calls record() then throws — should roll back the outer work
            // but the REQUIRES_NEW inner transaction must have already committed the audit row.
            assertThatThrownBy(() ->
                outerTx.execute(status -> {
                    auditLogService.record(userId, UserType.STAFF, "STAFF_LOGIN", Outcome.SUCCESS, null);
                    throw new RuntimeException("simulated business failure");
                })
            ).isInstanceOf(RuntimeException.class);

            assertThat(auditLogRepository.count()).isEqualTo(1);
            AuditLog persisted = auditLogRepository.findAll().get(0);
            assertThat(persisted.getUserId()).isEqualTo(userId);
            assertThat(persisted.getAction()).isEqualTo("STAFF_LOGIN");
            assertThat(persisted.getOutcome()).isEqualTo(Outcome.SUCCESS);
        }
    }
}
