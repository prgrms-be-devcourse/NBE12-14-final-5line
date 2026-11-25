ALTER TABLE place_change_event
    ADD COLUMN eligible_at DATETIME(6) NULL,
    ADD INDEX ix_place_change_event_status_eligible (task_status, eligible_at, place_change_event_id);

CREATE TABLE notification (
    notification_id BIGINT NOT NULL AUTO_INCREMENT,
    place_change_event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    PRIMARY KEY (notification_id),
    CONSTRAINT uk_notification_event_user UNIQUE (place_change_event_id, user_id),
    CONSTRAINT fk_notification_event FOREIGN KEY (place_change_event_id)
        REFERENCES place_change_event (place_change_event_id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES `user` (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
