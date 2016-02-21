package DistPool;

import DistPool.Interface.*;
import Distributors.DistFuture;
import Distributors.DistributableTask;
import Distributors.DistributorService;

import java.net.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.stream.Collectors;

enum RunState {STARTING, RUNNING, SHUTDOWN, STOP, TIDYING, TERMINATED}

public class DistPoolExecutor<T extends DistributableTask> implements MasterRemote, DistributorService {
    private static final Logger logger = Logger.getLogger("DistPool");
    private static final Level logLevel = Level.FINE;
    private static final Handler logHandler = new ConsoleHandler();
    private final int TIMEOUT = 5000;
    private final int HEARTFREQ = 1000;
    private final int BROADCASTFREQ = 10000;
    private final int port;
    private final Timer heartbeatTimer = new Timer("Heartbeat");
    private final Timer broadcastTimer = new Timer("Broadcast");
    private final Set<DistributableTask> completeTasks = new HashSet<>();
    private final HashMap<Integer, DistFuture> futures = new HashMap<>();
    private final HashMap<InetAddress, RemoteWorker> workerList = new HashMap<>();
    private final BlockingQueue<DistributableTask> taskQueue = new LinkedBlockingQueue<>();
    int totalTasks = 0;
    private RunState state = RunState.STARTING;
    private Thread workManagerThread;

    public DistPoolExecutor(int _port) {
        logger.setUseParentHandlers(false);
        logHandler.setLevel(logLevel);
        logger.setLevel(logLevel);
        logger.addHandler(logHandler);
        logger.log(Level.INFO, "Starting DistPool");
        port = _port;
        startLocalResource();
        if (state == RunState.STARTING) {
            startTimers();
            workManagerThread = new Thread(new WorkerManager());
            workManagerThread.start();
        }
    }

    private void startTimers() {
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkPulse();
            }
        }, 100, HEARTFREQ);

        broadcastTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                broadcast();
            }
        }, 100, BROADCASTFREQ);
    }


    private void startLocalResource() {
        try {
            String name = "Master";
            MasterRemote stub = (MasterRemote) UnicastRemoteObject.exportObject(this, port);
            Registry registry = LocateRegistry.createRegistry(port);
            registry.rebind(name, stub);
            logger.log(Level.FINE, "Exported Master resource");
            logger.log(Level.FINER, "Master started");
            state = RunState.STARTING;
        } catch (RemoteException e) {
            if (e.getCause() instanceof BindException) {
                logger.log(Level.SEVERE, "Failed to bind socket, port probably in use");
            } else {
                logger.log(Level.SEVERE, "Failed to export Master resource " + e.getMessage());
            }
            shutdown();
        }

    }

    public void shutdown() {
        logger.log(Level.INFO, "Shutting down DistPool");
        state = RunState.SHUTDOWN;
        //final ReentrantLock mainLock = this.mainLock;
        // mainLock.lock();
        try {
            terminateWorkers();
            //interruptIdleWorkers(false);
            workManagerThread.interrupt();
        } catch (NullPointerException ignore) {

        } finally {
            //  mainLock.unlock();
        }
        //tryTerminate();
        taskQueue.clear();
    }

    private void addComplete(DistributableTask t) {
        completeTasks.add(t);
    }

    @Override
    public List<DistributableTask> shutdownNow() {
        List<DistributableTask> incompleteTasks = new ArrayList<>();
        for (RemoteWorker worker : workerList.values()) {
            incompleteTasks.addAll(worker.runningTasks);
        }

        try {
            state = RunState.SHUTDOWN;
            terminateWorkers();
            //interruptIdleWorkers(false);
        } finally {

        }
        //tryTerminate();
        taskQueue.clear();
        return incompleteTasks;
    }

    @Override
    public boolean isShutdown() {
        return state == RunState.SHUTDOWN;
    }

    @Override
    public boolean isTerminated() {
        return state == RunState.TERMINATED;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        //long nanos = unit.toNanos(timeout);
        return false;
    }

    @Override
    public DistFuture<DistributableTask> submit(DistributableTask task) {
        if (task == null) throw new NullPointerException();
        task.setId(totalTasks++);
        DistFuture<DistributableTask> ftask = newFuture(task);
        futures.put(task.getId(), ftask);
        distribute(task);
        return ftask;
    }

    /**
     *
     * @param task
     * @return
     */

    private DistFuture<DistributableTask> newFuture(DistributableTask task) {
        return new DistFuture<DistributableTask>(task);
    }

    /**
     * TODO - Inherited from Distributable. Possibly useless. Investigate.
     * Triggers distribution of task if it's not NULL
     * @param task Task to distribute
     * @return NULL? Doesn't do anything in this context.
     */
    public DistributableTask distribute(DistributableTask task) {
        if (task == null)
            throw new NullPointerException();
        giveTaskToWorkers(task);
        return null;
    }

    /**
     * Send task to idle worker.
     * If no idle worker is available, task is (re)submitted to the task queue.
     * @param t Task to send.
     * @return Task was sent.
     */
    private boolean giveTaskToWorkers(DistributableTask t) {
        if (t != null) {
            if (workerList.size() > 0) {
                RemoteWorker worker = getIdleWorker();
                if (worker != null) {
                    try {
                        worker.submitTask(t);
                        logger.log(Level.FINER, "Submitted task to worker " + worker.address.getHostAddress());
                        return true;
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Worker Rejected task");
                    }
                }
            }
            taskQueue.add(t);
        }
        return false;
    }

    /**
     * Finds a RemoteWorker with spare thread pool capacity.
     * @return RemoteWorker with extra capacity or NULL if none exist.
     */
    private RemoteWorker getIdleWorker() {
        for (RemoteWorker w : workerList.values()) {
            if (w.getState() == WorkerState.IDLE) {
                return w;
            }
        }
        return null;
    }

    /**
     * Checks if Remote Workers respond to keep alive heartbeats.
     * Workers that fail to respond after TIMEOUT milliseconds get disconnected.
     * Called automatically by the heartbeat timer.
     */
    private void checkPulse() {
        for (Map.Entry<InetAddress, RemoteWorker> entry : workerList.entrySet()) {
            RemoteWorker worker = entry.getValue();
            if (worker.aliveTimer + HEARTFREQ < System.currentTimeMillis()) {
                if (worker.aliveTimer + TIMEOUT < System.currentTimeMillis()) {
                    disconnectWorker(entry.getKey());
                }
                worker.heartbeat();
            }
        }
    }

    /**
     * Connect to RMI resource exported by remote worker.
     * Adds resource to worker object.
     * @param address Address of remote worker.
     * @param port Port of exported resource on remote worker.
     * @param cores Number of cores (Thread Pool size) worker has.
     * @return connection success.
     */
    private boolean connectToWorkerResource(InetAddress address, int port, int cores) {
        RemoteWorker worker = new RemoteWorker(address, port, cores);
        if (worker.connectToResource()) {
            workerList.put(address, worker);
            return true;
        }
        return false;
    }

    /**
     * Disconnects and removes worker from DistPool.
     * Triggers task cleanup.
     * @param address Address of worker to disconnect
     */
    private void disconnectWorker(InetAddress address) {
        cleanupWorkerTasks(address);
        workerList.get(address).disconnect();
        workerList.remove(address);
    }

    /**
     * Adds workers tasks back to task queue, removes tasks from worker.
     * Called when worker disconnects, ensures no tasks get dropped by worker disconnects.
     * @param address workers ip address
     */
    private void cleanupWorkerTasks(InetAddress address) {
        RemoteWorker worker = workerList.get(address);
        taskQueue.addAll(worker.runningTasks.stream().collect(Collectors.toList()));
        worker.runningTasks.clear();
    }

    /**
     * Adds task returned from worker as the result value of its future.
     * @param task result of task execution
     */
    private void bindResultToFuture(DistributableTask task) {
        try {
            DistFuture future = futures.get(task.getId());
            future.setResult(task);
            logger.log(Level.FINER, "Bound result to Task Future");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to bind result to Task Future");
        }
    }

    /**
     * Remote Task executed by Worker
     * Inform Master that worker still alive.
     * @return true that heartbeat has been received.
     */
    @Override
    public boolean RMIheartbeat() {
        return true;
    }

    /**
     * Remote task executed by Worker
     * Informs DistPool about worker, so it can connect to it.
     * Triggered on Worker in response to a Broadcast packet.
     * @param _address Address of worker attempting registration
     * @param port Port of exported worker resources.
     * @param cores Number of cores (Thread Pool size) present on Worker.
     * @return Success.
     */
    @Override
    public boolean RMIregisterWorker(InetAddress _address, int port, int cores) {
        logger.log(Level.FINER, "Connecting to worker: " + _address + ":" + port);
        if (!workerList.containsKey(_address)) {
            connectToWorkerResource(_address, port, cores);
            logger.log(Level.FINER, "Success: Register worker " + _address);
        } else {
            logger.log(Level.FINE, "Fail: Register worker" + _address);
        }
        return true;
    }

    /**
     * Remote task executed by Worker
     * Worker is attempting to unregister itself, in order to shut down cleanly.
     * Disconnects and removes worker from DistPool.
     * @param address Address of RemoteWorker
     * @return Success.
     */

    @Override
    public boolean RMIunregisterWorker(InetAddress address) {
        if (workerList.containsKey(address)) {
            disconnectWorker(address);
            logger.log(Level.INFO, "Success: Unregister worker " + address);
        } else {
            logger.log(Level.FINE, "Fail: Unregister worker " + address);
        }
        return true;
    }

    /**
     * Remote Task executed by Worker
     * Submit task that has been completed by a remote worker.
     * @param result Completed task.
     * @param address Address of worker that completed task.
     * @return Success.
     */

    @Override
    public boolean RMIsubmitResult(DistributableTask result, InetAddress address) {
        addComplete(result);
        workerList.get(address).runningTasks.remove(result);
        logger.log(Level.FINER, "Success: Received result from worker: " + address.getHostAddress() + " Tasks remaining in queue: " + taskQueue.size());
        workerList.get(address).getState();
        bindResultToFuture(result);
        return true;
    }

    /**
     * Submit a collection of Tasks to the Dist Pool.
     * @param tasks A Collection of Tasks to be executed by the DistPool.
     * @return A list of futures for submitted tasks.
     */

    @Override
    public List<DistFuture> invokeAll(Collection tasks) {
        List<DistFuture> futureList = new ArrayList<>(tasks.size());
        try {
            for (DistributableTask t : (Collection<DistributableTask>) tasks) {
                futureList.add(submit(t));
            }
            return futureList;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    /**
     * TODO - Not Yet Implemented
     * Submit a collection of Tasks to the DistPool with a deadline to completion.
     * If the deadline is not met, the tasks are dropped.
     * @param tasks Collection of Tasks to be executed.
     * @param timeout Execution deadline.
     * @param unit TimeUnit for timeout value.
     * @return List of futures for submitted tasks.
     * @throws InterruptedException
     */

    @Override
    public List<DistFuture> invokeAll(Collection tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return null;
    }

    /**
     * Disconnect all workers from DistPool.
     * Triggered by Shutdown State.
     */

    final void terminateWorkers() {
        for (RemoteWorker worker : workerList.values()) {
            worker.disconnect();
        }
        workerList.clear();
    }

    /**
     * Broadcast an announcement of running DistPool service.
     * Workers respond to this announcement by connecting to the DistPool.
     * Called automatically by broadcastTimer.
     */

    private void broadcast() {
        if (state == RunState.RUNNING) {
            logger.log(Level.FINER, "Broadcasting");
            try {
                DatagramSocket socket = new DatagramSocket(port + 2);
                socket.setBroadcast(true);
                byte[] sendData = "ANN".getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), port + 1);
                socket.send(sendPacket);
                // Broadcast the message over all the network interfaces
                Enumeration interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = (NetworkInterface) interfaces.nextElement();
                    if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                        continue; // Don't want to broadcast to the loopback interface
                    }
                    for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                        InetAddress broadcast = interfaceAddress.getBroadcast();
                        if (broadcast == null) {
                            continue;
                        }
                        try {
                            sendPacket = new DatagramPacket(sendData, sendData.length, broadcast, port + 1);
                            socket.send(sendPacket);
                        } catch (Exception e) {

                        }
                    }
                }
                socket.close();
            } catch (BindException b) {
                logger.log(Level.SEVERE, "Socket in use, terminating");
                shutdown();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, ex.toString());
            }
        }
    }

    /**
     * Task distribution thread.
     * Sends tasks to workers.
     */
    private class WorkerManager implements Runnable {
        /**
         * DistPools main loop.
         * Distributes tasks to workers while RunState is RUNNING.
         */
        @Override
        public void run() {
            logger.log(Level.FINEST, "Started work manager thread");
            state = RunState.RUNNING;
            try {
                while (state == RunState.RUNNING) {
                    if (taskQueue.size() > 0 && workerList.size() > 0) {
                        for (DistributableTask t : taskQueue) {
                            if (!giveTaskToWorkers(taskQueue.poll())) {
                                break;
                            }
                        }
                    }
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Interrupted main loop " + e.getMessage());
            }
        }
    }

    /**
     * RemoteWorker is the local holder of Worker resources.
     * Contains information about the worker and tasks currently running.
     * Provides methods to communicate with Workers via RMI.
     */

    class RemoteWorker {
        final InetAddress address;
        final int port;       //remote resource port
        final int cores;      //number of processor cores
        final Set<DistributableTask> runningTasks = new HashSet<>();  //tasks remote worker is running
        long aliveTimer = 0;
        WorkerRemote remote;
        WorkerState state = WorkerState.IDLE;

        public RemoteWorker(InetAddress _address, int _port, int _cores) {
            port = _port;
            address = _address;
            cores = _cores;
        }

        boolean connectToResource() {
            try {
                Registry registry = LocateRegistry.getRegistry(address.getHostAddress(), port);
                remote = ((WorkerRemote) registry.lookup("Worker"));
                if (heartbeat()) {
                    logger.log(Level.INFO, "Success: Connected to worker resource " + address);
                    return true;
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed: Not Connected to worker resource " + address);
                e.printStackTrace();
                return false;
            }
            return false;
        }

        boolean heartbeat() {
            try {
                remote.RMIheartbeat();
                aliveTimer = System.currentTimeMillis();
                return true;
            } catch (RemoteException e) {
                logger.log(Level.FINER, "Worker failed to respond " + address.getHostAddress());
                return false;
            }
        }

        boolean disconnect() {
            try {
                if (remote.RMIDisconnect()) {
                    logger.log(Level.FINE, "Terminated connection to worker " + address);
                    return true;
                }
            } catch (RemoteException ignored) {
            }
            logger.log(Level.FINE, "Failed to cleanly terminate connection to worker " + address);
            return false;
        }

        boolean submitTask(DistributableTask t) {
            try {
                state = remote.RMISubmitTask(t);
                runningTasks.add(t);

                return true;
            } catch (RemoteException e) {
                e.printStackTrace();
                logger.log(Level.FINE, "Failed to submit task to " + address.getHostAddress());
            }
            return false;
        }

        WorkerState getState() {
            try {
                return remote.RMIGetState();
            } catch (RemoteException e) {
                logger.log(Level.FINER, "Failed to get Worker state " + address.getHostAddress());
            }
            return state;
        }

        void interruptIfStarted() {
            if (getState() == WorkerState.RUNNING)
                interrupt();
        }

        void interrupt() {
            try {
                remote.RMIInterrupt();
            } catch (RemoteException e) {
                logger.log(Level.FINER, "Failed to interrupt Worker " + address.getHostAddress());
            }
        }
    }
}
