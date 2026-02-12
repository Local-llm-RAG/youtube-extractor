create table if not exists arxiv_tracker (
    id bigserial primary key,
    date_start date not null,
    date_end date not null,
    all_papers_for_period integer not null,
    processed_papers_for_period integer not null,
    constraint uq_tracker_period unique (date_start, date_end)
    );
