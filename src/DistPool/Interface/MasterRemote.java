package DistPool.Interface;

import Distributors.DistributableTask;

import java.net.InetAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI interface to the master
 */
public interface MasterRemote extends Remote{

    //keep alive signal

    /**
     *
     * @return
     * @throws RemoteException
     */
    boolean RMIheartbeat() throws RemoteException;

    //register self as slave

    /**
     *
     * @param address
     * @param port
     * @param cores
     * @return
     * @throws RemoteException
     */
    boolean RMIregisterWorker(InetAddress address, int port, int cores) throws RemoteException;

    //unregister self

    /**
     *
     * @param address
     * @return
     * @throws RemoteException
     */
    boolean RMIunregisterWorker(InetAddress address) throws RemoteException;

    //submit result

    /**
     *
     * @param r
     * @param address
     * @return
     * @throws RemoteException
     */
    boolean RMIsubmitResult(DistributableTask r, InetAddress address) throws RemoteException;

}
