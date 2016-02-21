package Distributors;

/**
 * Created by king_ on 16/02/2016.
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
