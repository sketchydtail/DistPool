import DistPool.Worker;

/**
 * Start a slave node.
 * @author Julian Hunt aka. Sketchy D Tail
 * @version 1.0, 21/02/2016
 *
 * Run this on each remote machine to add them to the distribution network.
 */
class RunSlave {

    public static void main(String[] args){
        Worker worker = new Worker(8880);

    }
}
