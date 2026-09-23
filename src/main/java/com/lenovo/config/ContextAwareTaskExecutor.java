package com.lenovo.config;

import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/** Propagates request and security contexts to asynchronous tasks. */
public class ContextAwareTaskExecutor implements AsyncTaskExecutor {
    private final AsyncTaskExecutor delegate;

    public ContextAwareTaskExecutor(AsyncTaskExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable task) {
        delegate.execute(wrap(task));
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(wrap(task));
    }

    private Runnable wrap(Runnable task) {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        return () -> {
            try {
                SecurityContextHolder.setContext(securityContext);
                if (requestAttributes != null) {
                    RequestContextHolder.setRequestAttributes(requestAttributes);
                }
                task.run();
            } finally {
                SecurityContextHolder.clearContext();
                RequestContextHolder.resetRequestAttributes();
            }
        };
    }

    private <T> Callable<T> wrap(Callable<T> task) {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        return () -> {
            try {
                SecurityContextHolder.setContext(securityContext);
                if (requestAttributes != null) {
                    RequestContextHolder.setRequestAttributes(requestAttributes);
                }
                return task.call();
            } finally {
                SecurityContextHolder.clearContext();
                RequestContextHolder.resetRequestAttributes();
            }
        };
    }
}
