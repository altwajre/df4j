/*
 * Copyright 2012 by Alexei Kaigorodov
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.df4j.core.actor;

import org.junit.Test;

import java.util.concurrent.ExecutionException;

public class UnicastUnBufferedSourceTest extends StreamOutputTestBase {

    @Override
    protected Source<Long> createPublisher(long elements) {
        Logger parent = new Logger(true);
        Source flowPublisher = new UnicastUnBufferedSource(parent, elements);
        flowPublisher.start();
        return flowPublisher;
    }

    @Test
    public void unBufferedSourceRegressionTest() throws InterruptedException, ExecutionException {
        testSource(1,2,2);
        testSource(9,10,4);
    }

    @Test
    public void testUnBufferedSource() throws InterruptedException, ExecutionException {
        for (int[] row: data()) {
            testSource(row[0], row[1], row[2]);
        }
    }
}

