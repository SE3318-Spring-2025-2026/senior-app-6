SHOW TABLES;

DESCRIBE staff_user;

INSERT
	IGNORE
INTO
	staff_user (id, first_login, mail, password_hash, role)
VALUES
	(
		UUID_TO_BIN ('00000000-0000-0000-0000-000000000001'),
		FALSE,
		'test@test.com',
		'$2a$10$tj811p0KDPOD6Dd58xb0.uBNIT8.CXeJPKoSUSwPuJ0BI.RuC5yGq',
		'ADMIN'
	);

INSERT
	IGNORE
INTO
	student (id, student_id)
VALUES
	(
		UUID_TO_BIN ('00000000-0000-0000-0000-000000000002'),
		'23070006018'
	);

INSERT
	IGNORE
INTO
	staff_user (id, first_login, mail, password_hash, role)
VALUES
	(
		UUID_TO_BIN ('00000000-0000-0000-0000-000000000003'),
		FALSE,
		'coordinator@test.com',
		'$2a$10$tj811p0KDPOD6Dd58xb0.uBNIT8.CXeJPKoSUSwPuJ0BI.RuC5yGq',
		'COORDINATOR'
	);

INSERT
	IGNORE
INTO
	staff_user (id, first_login, mail, password_hash, role)
VALUES
	(
		UUID_TO_BIN ('00000000-0000-0000-0000-000000000004'),
		FALSE,
		'professor@test.com',
		'$2a$10$tj811p0KDPOD6Dd58xb0.uBNIT8.CXeJPKoSUSwPuJ0BI.RuC5yGq',
		'PROFESSOR'
	);

INSERT IGNORE INTO password_reset_token (
    id,
    staff_id,
    token,
    created_at,
    expires_at
)
VALUES (
    UUID_TO_BIN('11111111-1111-1111-1111-111111111111'),
    UUID_TO_BIN('00000000-0000-0000-0000-000000000004'),
    'TEST_RESET_TOKEN_123',
    NOW(),
    DATE_ADD(NOW(), INTERVAL 1 DAY)
);

INSERT IGNORE INTO password_reset_token (
    id,
    staff_id,
    token,
    created_at,
    expires_at
)
VALUES (
    UUID_TO_BIN('22222222-2222-2222-2222-222222222222'),
    UUID_TO_BIN('00000000-0000-0000-0000-000000000004'),
    'gercek_expired_token',
    DATE_SUB(NOW(), INTERVAL 2 DAY),
    DATE_SUB(NOW(), INTERVAL 1 DAY)
);

INSERT IGNORE INTO password_reset_token (
    id,
    staff_id,
    token,
    created_at,
    expires_at
)
VALUES (
    UUID_TO_BIN('33333333-3333-3333-3333-333333333333'),
    UUID_TO_BIN('00000000-0000-0000-0000-000000000004'),
    'USED_TOKEN_123',
    NOW(),
    DATE_ADD(NOW(), INTERVAL 1 DAY)
);

-- Issue 55: Red Team system_config seed data
INSERT IGNORE INTO system_config (config_key, config_value) VALUES ('active_term_id', '2026-SPRING');
INSERT IGNORE INTO system_config (config_key, config_value) VALUES ('max_team_size', '4');

-- P2: schedule window for group creation (2026-SPRING, open all year)
INSERT IGNORE INTO schedule_window (id, term_id, type, opens_at, closes_at)
VALUES (
    UUID_TO_BIN('cccccccc-cccc-cccc-cccc-000000000001'),
    '2026-SPRING',
    'GROUP_CREATION',
    '2026-01-01 00:00:00',
    '2026-12-31 23:59:59'
);

-- P3: schedule window for advisor association (2026-SPRING, open all year)
INSERT IGNORE INTO schedule_window (id, term_id, type, opens_at, closes_at)
VALUES (
    UUID_TO_BIN('cccccccc-cccc-cccc-cccc-000000000002'),
    '2026-SPRING',
    'ADVISOR_ASSOCIATION',
    '2026-01-01 00:00:00',
    '2026-12-31 23:59:59'
);

-- P2: additional students for invitation flow testing
INSERT IGNORE INTO student (id, student_id)
VALUES
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000010'), '23070006019'),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000011'), '23070006020'),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000012'), '23070006021');
