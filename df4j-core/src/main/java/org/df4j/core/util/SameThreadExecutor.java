package org.df4j.core.util;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

public class SameThreadExecutor implements Executor {
    static private ThreadLocal<Queue<Runnable>> myThreadLocal = new ThreadLocal<>();

    @Override
    public void execute(Runnable command) {
        Queue<Runnable> queue = myThreadLocal.get();
        if (queue == null) {
            queue = new ArrayDeque<>();
            myThreadLocal.set(queue);
            queue.add(command);
            while ((command = queue.poll()) != null) {
                try {
                    command.run();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            myThreadLocal.remove();
        } else {
            queue.add(command);
        }
    }
}
