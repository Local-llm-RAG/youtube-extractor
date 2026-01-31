create table if not exists youtube_regions (
                                                id bigserial primary key,
                                                region_id varchar(64) not null unique,
                                                country_name varchar(64) not null);

create table if not exists youtube_region_languages (
                                                id bigserial primary key,
                                                youtube_region_id bigint not null references youtube_regions(id) on delete cascade,
                                                language_code varchar(64) not null,
                                                priority int not null default 0,
                                                unique (youtube_region_id, language_code));

create index if not exists ix_youtube_region_languages_region
    on youtube_region_languages (youtube_region_id);

create index if not exists ix_youtube_region_languages_code
    on youtube_region_languages (language_code);
