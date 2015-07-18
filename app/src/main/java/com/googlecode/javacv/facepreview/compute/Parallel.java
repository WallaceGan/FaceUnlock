package com.googlecode.javacv.facepreview.compute;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

// from http://stackoverflow.com/questions/4010185/parallel-for-for-java
public class Parallel {
    private static final int NUM_CORES = Runtime.getRuntime().availableProcessors();

    private static final ExecutorService forPool = Executors.newFixedThreadPool(NUM_CORES * 2, new NamedThreadFactory("Parallel.For"));

    public static <T> void For(final Iterable<T> elements, final Operation<T> operation) {
        try {
            // invokeAll blocks for us until all submitted tasks in the call complete
            forPool.invokeAll(createCallables(elements, operation));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static <T> Collection<Callable<Void>> createCallables(final Iterable<T> elements, final Operation<T> operation) {
        List<Callable<Void>> callables = new LinkedList<Callable<Void>>();
        for (final T elem : elements) {
            callables.add(new Callable<Void>() {
                @Override
                public Void call() {
                    operation.perform(elem);
                    return null;
                }
            });
        }

        return callables;
    }

    public static interface Operation<T> {
        public void perform(T pParameter);
    }
}

// http://grepcode.com/file_/repo1.maven.org/maven2/org.apache.james/james-server-util/3.0-beta4/org/apache/james/util/concurrent/NamedThreadFactory.java/?v=source
class NamedThreadFactory implements ThreadFactory {

    public final String name;
    private final AtomicLong count = new AtomicLong();
    private int priority;

    public NamedThreadFactory(final String name, final int priority) {
        if (priority > Thread.MAX_PRIORITY || priority < Thread.MIN_PRIORITY) {
            throw new IllegalArgumentException("Priority must be <= " + Thread.MAX_PRIORITY + " and >=" + Thread.MIN_PRIORITY);
        }
        this.name = name;
        this.priority = priority;
    }

    public NamedThreadFactory(final String name) {
        this(name, Thread.NORM_PRIORITY);
    }

    /**
     * @see java.util.concurrent.ThreadFactory#newThread(java.lang.Runnable)
     */
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setName(name + "-" + count.incrementAndGet());
        t.setPriority(priority);
        return t;
    }

    /**
     * Return the name
     * 
     * @return name
     */
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "NamedTreadFactory: " + getName();
    }

}