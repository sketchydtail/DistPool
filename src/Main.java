/*
 * Copyright (c) 2016. Julian Hunt.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

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
