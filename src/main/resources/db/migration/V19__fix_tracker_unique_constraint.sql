ALTER TABLE public.tracker
    DROP CONSTRAINT IF EXISTS uq_tracker_period;

ALTER TABLE public.tracker
    ADD CONSTRAINT uq_tracker_period
        UNIQUE (date_start, date_end, data_source);

ALTER TABLE public.record_category
    ALTER COLUMN category TYPE text;

ALTER TABLE public.document_section
    ALTER COLUMN title TYPE text;

ALTER TABLE public.document_section
    ALTER COLUMN text TYPE text;