package DistPool;

import Distributors.DistributableTask;

import java.util.concurrent.Callable;

/**
 * @author Julian Hunt aka. Sketchy D Tail
 * @version 1.0, 21/02/2016
 */
public abstract class DistTask implements DistributableTask, Callable {
    int id;
    public int getId(){
        return id;
    }
    public void setId(int _id) {
        id = _id;
    }

    @Override
    public Callable distribute() throws Exception {
        return this;
    }
}
