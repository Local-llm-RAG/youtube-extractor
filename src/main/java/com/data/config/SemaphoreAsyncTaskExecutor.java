package com.data.config;

import org.springframework.core.task.AsyncTaskExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

public class SemaphoreAsyncTaskExecutor implements AsyncTaskExecutor {

    private final AsyncTaskExecutor delegate;
    private final Semaphore semaphore;

    public SemaphoreAsyncTaskExecutor(AsyncTaskExecutor delegate, int maxConcurrency) {
        this.delegate = delegate;
        this.semaphore = new Semaphore(maxConcurrency);
    }

    @Override
    public void execute(Runnable task) {
        delegate.execute(wrap(task));
    }

    @Override
    public void execute(Runnable task, long startTimeout) {
        delegate.execute(wrap(task), startTimeout);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(() -> {
            semaphore.acquire();
            try {
                return task.call();
            } finally {
                semaphore.release();
            }
        });
    }

    private Runnable wrap(Runnable task) {
        return () -> {
            try {
                semaphore.acquire();
                task.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                semaphore.release();
            }
        };
    }
}
