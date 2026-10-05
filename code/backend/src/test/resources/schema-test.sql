CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(64),
    avatar VARCHAR(255),
    email VARCHAR(128),
    phone VARCHAR(20),
    role VARCHAR(20) DEFAULT 'user',
    status INT DEFAULT 1,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted INT DEFAULT 0
);

CREATE TABLE emotion_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    emotion VARCHAR(32) NOT NULL,
    emotion_icon VARCHAR(8),
    emotion_score INT DEFAULT 3,
    note VARCHAR(255),
    sleep_score INT DEFAULT 3,
    stress_score INT DEFAULT 3,
    rating_source VARCHAR(24) DEFAULT NULL,
    `trigger` VARCHAR(64),
    record_date DATE NOT NULL,
    created_at TIMESTAMP
);
