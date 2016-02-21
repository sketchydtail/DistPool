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

package Distributors;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Julian Hunt aka. Sketchy D Tail
 * @version 1.0, 21/02/2016
 */
public interface DistributorService<T extends DistributableTask>{
    void shutdown();
    List<Distributable> shutdownNow();
    boolean isShutdown();
    boolean isTerminated();
    boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException;

    DistFuture<T> submit(T task);

    List<DistFuture<T>> invokeAll(Collection<? extends T> tasks);

    List<DistFuture<T>> invokeAll(Collection<? extends T> tasks,
                                  long timeout, TimeUnit unit)
            throws InterruptedException;

}
