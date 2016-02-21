package Distributors;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by king_ on 16/02/2016.
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
