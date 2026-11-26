CREATE TABLE place_change_event (
    place_change_event_id BIGINT NOT NULL AUTO_INCREMENT,
    place_notice_id BIGINT NOT NULL,
    task_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed_at DATETIME NULL,
    PRIMARY KEY (place_change_event_id),
    CONSTRAINT uk_place_change_event_notice UNIQUE (place_notice_id),
    CONSTRAINT fk_place_change_event_notice FOREIGN KEY (place_notice_id)
        REFERENCES place_notice (place_notice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
