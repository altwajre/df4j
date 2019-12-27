/*
 * Copyright 2011 by Alexei Kaigorodov
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.df4j.core.dataflow;

import org.df4j.protocol.Completable;

/**
 * {@link AsyncProc} is a {@link Dataflow} with single {@link BasicBlock} which is executed only once.
*/
public abstract class AsyncProc extends BasicBlock implements Activity {
    private volatile boolean stopped = false;

    public AsyncProc(Dataflow parent) {
        super(parent);
    }

    public AsyncProc() {
        super(new Dataflow());
    }

    public boolean isStopped() {
        return stopped;
    }

    @Override
    public void start() {
        super.awake();
    }

    @Override
    public void stop() {
        stopped = true;
        super.stop();
    }

    @Override
    public void stop(Throwable ex) {
        stopped = true;
        super.stop(ex);
    }

    @Override
    public boolean isAlive() {
        return super.dataflow.isAlive();
    }

    public boolean isCompleted() {
        return dataflow.isCompleted();
    }

    public void join() {
        dataflow.join();
    }

    public boolean blockingAwait(long timeout) {
        return dataflow.blockingAwait(timeout);
    }

    protected void run() {
        try {
            runAction();
            stop();
        } catch (Throwable e) {
            stop(e);
        }
    }

}