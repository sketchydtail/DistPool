package Distributors;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Julian Hunt aka. Sketchy D Tail
 * @version 1.0, 21/02/2016
 */
public interface DistributorService<T extends DistributableTask>{
    void shutdown();
    List<Distributable> shutdownNow();
    boolean isShutdown();
    boolean isTerminated();
    boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException;

    DistFuture<T> submit(T task);

    List<DistFuture<T>> invokeAll(Collection<? extends T> tasks);

    List<DistFuture<T>> invokeAll(Collection<? extends T> tasks,
                                  long timeout, TimeUnit unit)
            throws InterruptedException;

}
