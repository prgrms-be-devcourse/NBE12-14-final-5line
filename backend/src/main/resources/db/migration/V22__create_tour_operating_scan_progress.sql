CREATE TABLE tour_operating_scan_progress (
    scan_id TINYINT NOT NULL PRIMARY KEY,
    after_place_id BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_tour_operating_scan_singleton CHECK (scan_id = 1),
    CONSTRAINT ck_tour_operating_scan_cursor CHECK (after_place_id >= 0)
) ENGINE=InnoDB;

INSERT INTO tour_operating_scan_progress (scan_id) VALUES (1);
