-- V23: Add composite indexes for common query patterns

-- Composite indexes on source_record for common lookups
CREATE INDEX idx_source_record_datestamp_source
    ON source_record(data_source, datestamp, source_identifier);

CREATE INDEX idx_source_record_source_id_datasource
    ON source_record(data_source, source_identifier);

-- Descending index on tracker.date_end for recent-first queries
CREATE INDEX idx_tracker_date_end
    ON tracker(date_end DESC);
