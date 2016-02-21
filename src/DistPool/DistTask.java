package DistPool;

import Distributors.DistributableTask;

import java.util.concurrent.Callable;

/**
 * Created by king_ on 15/02/2016.
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
