package com.youtube.startup;

import com.youtube.locks.PostgresAdvisoryLock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OnInitialStartup implements CommandLineRunner {

    private static final long LOCK_KEY = 942_001L;

    private final PostgresAdvisoryLock lock;
    private final RegionBootstrapService bootstrapService;

    @Override
    public void run(String... args) throws Exception {
        if (!lock.tryLock(LOCK_KEY)) return;
        try {
            bootstrapService.importRegionsOnce();
        } finally {
            lock.unlock(LOCK_KEY);
        }
    }

}
