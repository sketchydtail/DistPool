package Distributors;

import java.io.Serializable;
import java.util.concurrent.Callable;

/**
 * Created by king_ on 16/02/2016.
 */
@FunctionalInterface
public interface Distributable<V extends Callable> extends Serializable{
        /**
         The distribution equivalent of Runnable
         */
        V distribute() throws Exception;

}
