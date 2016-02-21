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

/**
 * @author Julian Hunt aka. Sketchy D Tail
 * @version 1.0, 21/02/2016
 */
public class DistributionException extends Exception {

    /**
     * Constructs an {@code DistributionException} with no detail message.
     * The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    protected DistributionException() { }

    /**
     * Constructs an {@code DistributionException} with the specified detail
     * message. The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param message the detail message
     */
    protected DistributionException(String message) {
        super(message);
    }

    /**
     * Constructs an {@code DistributionException} with the specified detail
     * message and cause.
     *
     * @param  message the detail message
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public DistributionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs an {@code DistributionException} with the specified cause.
     * The detail message is set to {@code (cause == null ? null :
     * cause.toString())} (which typically contains the class and
     * detail message of {@code cause}).
     *
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public DistributionException(Throwable cause) {
        super(cause);
    }
}
