-- 创建用户表（user）
CREATE TABLE IF NOT EXISTS user (
    user_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    name VARCHAR(100) DEFAULT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20) DEFAULT NULL,
    dept VARCHAR(100) DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status BOOLEAN DEFAULT FALSE,
    is_member BOOLEAN  DEFAULT FALSE,
    avatar VARCHAR(255)  DEFAULT NULL
);

-- 创建简历表（resume）
CREATE TABLE IF NOT EXISTS resume (
    resume_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    cycle_id INT NOT NULL,
    status TINYINT DEFAULT 1,
    submitted_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(user_id)
);

-- 创建简历字段定义表（resume_field_definition）
CREATE TABLE IF NOT EXISTS resume_field_definition (
    field_id INT PRIMARY KEY AUTO_INCREMENT,
    cycle_id INT NOT NULL,
    field_key VARCHAR(100) NOT NULL,
    field_label VARCHAR(200) NOT NULL,
    is_required BOOLEAN DEFAULT FALSE,
    sort_order INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 创建简历字段值表（resume_field_value）
CREATE TABLE IF NOT EXISTS resume_field_value (
    value_id INT PRIMARY KEY AUTO_INCREMENT,
    resume_id INT NOT NULL,
    field_id INT NOT NULL,
    field_value TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (resume_id) REFERENCES resume(resume_id) ON DELETE CASCADE,
    FOREIGN KEY (field_id) REFERENCES resume_field_definition(field_id)
);

-- 创建获奖经历表（award_experience）
CREATE TABLE IF NOT EXISTS award_experience (
    award_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    user_id INTEGER NOT NULL,
    award_name VARCHAR(255) NOT NULL,
    award_time TIMESTAMP NOT NULL,
    description TEXT,
    FOREIGN KEY (user_id) REFERENCES user(user_id) ON DELETE CASCADE
);