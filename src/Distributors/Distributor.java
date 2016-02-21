package Distributors;

/**
 * @author Julian Hunt aka. Sketchy D Tail
 * @version 1.0, 21/02/2016
 * Based on Executor by Doug Lea
 */
public interface Distributor {

    /**
     * Distributes the given command at some time in the future.
     *
     * @param command the distributable task
     * @throws RejectedDistributionException if this task cannot be
     *                                       accepted for distribution
     * @throws NullPointerException          if command is null
     */
    void distribute(Distributable command);
}
