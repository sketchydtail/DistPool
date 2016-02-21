/**
 * Testing & example program for the Distributed Thread Pool.
 * @author Julian Hunt aka. Sketchy D Tail
 * @version 1.0, 21/02/2016
 */

import DistPool.DistPoolExecutor;
import Distributors.DistributableTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

class Main {

    public static void main(String[] args){
        final int PORT = 8880;
        DistPoolExecutor pool = new DistPoolExecutor(PORT);
        List<DistributableTask> tasks = new ArrayList<>();

        //create 1000 test tasks, add them to a list
        for (int i = 0; i < 1000; i++) {
            tasks.add(new TestTask(50000));
        }

        //add the test tasks to the pool and store their futures.
        List futures = pool.invokeAll(tasks);

        try {
            while (futures.size() > 0) {    //loop until all futures have been removed from the list
                while (futures.iterator().hasNext()) {  //keep looping through to find any that have finished
                    Future f = (Future) futures.iterator().next();
                    if (f != null && f.isDone()) {
                        System.out.println("Task: " + ((TestTask) f.get()).getId() + " result: " + ((TestTask) f.get()).getResult());
                        futures.remove(f);
                    }
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }

    }
}
