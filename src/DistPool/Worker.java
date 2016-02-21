package DistPool;

import DistPool.Interface.MasterRemote;
import DistPool.Interface.WorkerRemote;
import DistPool.Interface.WorkerState;
import Distributors.DistributableTask;

import java.io.IOException;
import java.net.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Julian Hunt aka. Sketchy D Tail
 * @version 1.0, 21/02/2016
 */
public class Worker implements WorkerRemote {
    private static final Logger logger = Logger.getLogger("DistPool.Slave");
    private static final Level logLevel = Level.FINE;
    private static final Handler logHandler = new ConsoleHandler();
    private final Timer aliveTimer = new Timer(false);
    private final int MASTERPORT;
    private final int SLAVEPORT;
    private final int TIMEOUT = 5000;
    private final int cores = Runtime.getRuntime().availableProcessors();
    private final ArrayBlockingQueue<Future<DistributableTask>> runningTasks = new ArrayBlockingQueue<>(cores);
    private final BlockingQueue<Runnable> workQueue = new SynchronousQueue<>();
    private ExecutorService executor;
    private InetAddress ADDRESS;
    private MasterRemote master;
    private boolean connected = false;
    private long aliveTime = 0;
    private WorkerState state = WorkerState.IDLE;

    public Worker(int masterPort) {
        MASTERPORT = masterPort;
        SLAVEPORT = MASTERPORT - 10;
        initLogger();
        getAddress();
        bindLocalResource();
        initHeartbeat();
        run();
    }

    private void run() {
        boolean terminated = false;
        while (!terminated) {
            if (!connected) {
                listen();
            }
            while (connected) {
                checkTasksComplete();
                checkIdleCores();
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
    }

    private void getAddress() {
        try {
            ADDRESS = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            logger.log(Level.SEVERE, "No network available");
            System.exit(-1);
        }
    }

    private void initLogger() {
        logHandler.setLevel(logLevel);
        logger.addHandler(logHandler);
        logger.setLevel(logLevel);
    }

    private void initHeartbeat() {
        int HEARTFREQ = 1000;
        aliveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkPulse();
            }
        }, 100, HEARTFREQ);
    }

    private boolean checkIdleCores() {

        if (runningTasks.size() < cores) {
            state = WorkerState.IDLE;
            return true;
        } else {
            state = WorkerState.RUNNING;
            return false;
        }
    }

    private boolean bindLocalResource() {
        try {
            String name = "Worker";
            WorkerRemote stub = (WorkerRemote) UnicastRemoteObject.exportObject(this, SLAVEPORT);
            Registry registry = LocateRegistry.createRegistry(SLAVEPORT);
            registry.rebind(name, stub);
            logger.log(Level.FINE, "Exported Worker resource");
            return true;
        } catch (RemoteException e) {
            logger.log(Level.SEVERE, "Failed to export Worker resource");
            return false;
        }
    }

    private boolean bindMaster(InetAddress address) {
        logger.log(Level.INFO, "Connecting to master " + address.getHostAddress());
        try {
            Registry registry = LocateRegistry.getRegistry(address.getHostAddress(), MASTERPORT);
            master = ((MasterRemote) registry.lookup("Master"));
            logger.log(Level.INFO, "Success: Connected to master resource " + address.getHostAddress());
            return registerWithMaster();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed: Connection to master resource " + address.getHostAddress());
            e.printStackTrace();
            return false;
        }
    }

    private boolean registerWithMaster() {
        try {
            master.RMIregisterWorker(ADDRESS, SLAVEPORT, cores);
            connected = true;
            aliveTime = System.currentTimeMillis();
            createThreadPool();
            return true;
        } catch (RemoteException e) {
            logger.log(Level.SEVERE, "Failed to register with master");
        }
        return false;
    }

    private void createThreadPool() {
        executor = new ThreadPoolExecutor(cores, cores, 100, TimeUnit.MILLISECONDS, workQueue);
    }

    private void checkTasksComplete() {
        for (Future<DistributableTask> r : runningTasks) {
            if (r.isDone()) {
                submitResult(r);
            }
        }
        try {
            Thread.sleep(100);
        } catch (Exception ignored) {
        }
    }

    private boolean submitResult(Future<DistributableTask> result) {
        if (connected) {
            try {
                master.RMIsubmitResult(result.get(), ADDRESS);
                runningTasks.remove(result);
                checkIdleCores();
                logger.log(Level.FINER, "Submitted result for task ");
                return true;
            } catch (Exception e) {
                if (e.getCause() instanceof ConnectException) {
                    logger.log(Level.WARNING, "Error submitting result: Connection refused");
                    disconnect("disconnect");
                } else {
                    logger.log(Level.SEVERE, "Error submitting result: " + e.getMessage());
                }
                return false;
            }
        }
        return false;
    }

    private void checkPulse() {
        if (connected) {
            try {
                if (aliveTime + TIMEOUT < System.currentTimeMillis()) {
                    disconnect("timeout");
                } else if (master.RMIheartbeat()) {
                    aliveTime = System.currentTimeMillis();
                }
            } catch (RemoteException e) {
                logger.log(Level.FINEST, "Failed to send heartbeat");
            }
        }
    }

    private void disconnect(String reason) {
        clearData();
        switch (reason) {
            case "timeout":
                logger.log(Level.WARNING, "Disconnected: Master timed out after " + (TIMEOUT / 1000) + " seconds");
                break;
            case "disconnect":
                logger.log(Level.WARNING, "Disconnected: Connection Lost");
                break;
            case "ordered":
                logger.log(Level.WARNING, "Disconnected: Master ordered disconnect");
                break;
        }
    }

    private void clearData() {
        connected = false;
        master = null;
        executor.shutdown();
        runningTasks.clear();
    }

    @Override
    public boolean RMIheartbeat() throws RemoteException {
        return true;
    }

    @Override
    public boolean RMIDisconnect() throws RemoteException {
        disconnect("ordered");
        return true;
    }

    @Override
    public void RMIsetThreadPriority(int priority) throws RemoteException {

    }

    @Override
    public WorkerState RMISubmitTask(DistributableTask task) throws RemoteException {
        try {
            runningTasks.add(executor.submit((Callable) task));
            logger.log(Level.FINER, "Added task ");
        } catch (IllegalStateException e) {
            logger.log(Level.FINER, "Rejected task, Queue is full");
        }
        checkIdleCores();
        return state;
    }

    @Override
    public WorkerState RMIGetState() throws RemoteException {
        return state;
    }

    @Override
    public boolean RMIInterrupt() throws RemoteException {
        return true;
    }

    private void listen() {
        try {
            DatagramSocket socket = new DatagramSocket(MASTERPORT + 1);
            socket.setBroadcast(true);
            logger.log(Level.INFO, "Waiting for broadcast from master");
            byte[] recvBuf = new byte[15000];
            DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
            socket.receive(packet);
            String message = new String(packet.getData()).trim();
            if (message.equals("ANN")) {
                logger.log(Level.FINER, "Received broadcast from " + packet.getAddress());
                bindMaster(packet.getAddress());
            }
            socket.close();
        } catch (BindException b) {
            logger.log(Level.SEVERE, "Port in use, terminating");
            System.exit(-1);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.toString());

        }
    }
}
