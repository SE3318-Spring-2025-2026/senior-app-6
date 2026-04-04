INSERT
	IGNORE
INTO
	staff_user (id, is_first_login, mail, password_hash, role)
VALUES
	(
		UUID_TO_BIN ("00000000-0000-0000-0000-000000000001"),
		FALSE,
		"test@test.com",
		"$2a$10$tj811p0KDPOD6Dd58xb0.uBNIT8.CXeJPKoSUSwPuJ0BI.RuC5yGq",
		"ADMIN"
	);

INSERT
	IGNORE
INTO
	student (id, student_id)
VALUES
	(
		UUID_TO_BIN ("00000000-0000-0000-0000-000000000002"),
		"23070006018"
	);

INSERT IGNORE INTO staff_user (id, is_first_login, mail, password_hash, role)
VALUES (
    UUID_TO_BIN("00000000-0000-0000-0000-000000000001"),
    FALSE,
    "coordinator@test.com",
    "bcrypt_hash_buraya",
    "Coordinator"
);