package ru.itmo.courses.common.config;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import java.util.concurrent.Callable;

@Component
public class BlockingExecutor {
    private final Scheduler scheduler;

    public BlockingExecutor(@Value("${app.blocking.threads:16}") int threads,
            @Value("${app.blocking.queue-capacity:100}") int queueCapacity) {
        scheduler = Schedulers.newBoundedElastic(threads, queueCapacity, "course-blocking");
    }

    public <T> Mono<T> call(Callable<T> action) {
        // Spring-прокси сервиса и вся JPA-транзакция выполняются в одном рабочем потоке.
        return Mono.fromCallable(action).subscribeOn(scheduler);
    }

    @PreDestroy
    public void close() {
        scheduler.dispose();
    }
}
