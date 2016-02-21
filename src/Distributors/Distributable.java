package Distributors;

import java.io.Serializable;
import java.util.concurrent.Callable;

/**
 * @author Julian Hunt aka. Sketchy D Tail
 * @version 1.0, 21/02/2016
 * The distributable version of Callable
 */
@FunctionalInterface
public interface Distributable<V extends Callable> extends Serializable{
        /**
         The distribution equivalent of Runnable
         */
        V distribute() throws Exception;

}
