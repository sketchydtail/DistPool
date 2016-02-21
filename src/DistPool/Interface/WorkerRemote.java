package DistPool.Interface;

import Distributors.DistributableTask;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by king_ on 16/02/2016.
 */
public interface WorkerRemote extends Remote {
    //keep alive signal
    boolean RMIheartbeat () throws RemoteException;

    //unregister master
    boolean RMIDisconnect () throws RemoteException;

    //set thread priority
    void RMIsetThreadPriority(int priority) throws RemoteException;

    WorkerState RMISubmitTask(DistributableTask task) throws RemoteException;

    WorkerState RMIGetState() throws RemoteException;

    boolean RMIInterrupt() throws RemoteException;

}
