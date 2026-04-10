CREATE TABLE export_watermark (
    data_source  VARCHAR(128) PRIMARY KEY,
    exported_at  TIMESTAMPTZ NOT NULL
);
