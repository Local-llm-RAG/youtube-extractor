package com.data.startup;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostgresAdvisoryLock {

    private final JdbcTemplate jdbcTemplate;

    public boolean tryLock(long lockKey) {
        Boolean locked = jdbcTemplate.queryForObject(
                "select pg_try_advisory_lock(?)",
                Boolean.class,
                lockKey
        );
        return Boolean.TRUE.equals(locked);
    }

    public void unlock(long lockKey) {
        jdbcTemplate.queryForObject(
                "select pg_advisory_unlock(?)",
                Boolean.class,
                lockKey
        );
    }
}
