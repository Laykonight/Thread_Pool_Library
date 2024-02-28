package il.co.ILRD;

import org.jetbrains.annotations.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPool implements Executor {
    private final AtomicInteger realNumOfThreads = new AtomicInteger();
    private final AtomicInteger futureNumOfThreads = new AtomicInteger();
    private final SemWaitableQueue<Task<?>> tasks;
    private final Semaphore pausing = new Semaphore(0);
    private volatile boolean isRunning;
    private AtomicBoolean isPause;
    private static final int MAX_PRIORITY = 11;
    private static final int MIN_PRIORITY = 0;
    /*----------------------------------------------------------------*/

    public ThreadPool(int numOfThreads) {
        tasks = new SemWaitableQueue<>(numOfThreads);
        this.realNumOfThreads.set(numOfThreads);
        this.futureNumOfThreads.set(numOfThreads);
        this.isPause = new AtomicBoolean(false);
        this.isRunning = true;
        this.threadCreator(numOfThreads);
    }

    public ThreadPool() {
        this(Runtime.getRuntime().availableProcessors());
    }

    public <V> Future<V> submit(Callable<V> command, @NotNull Priority priority) {
        return this.submit(command, priority.getValue());
    }

    public <V> Future<V> submit(Callable<V> command) {
        return this.submit(command, Priority.DEFAULT.getValue());
    }

    public <Void> Future<Void> submit(Runnable command, @NotNull Priority priority) {

        return this.submit(this.convert(command, null), priority.getValue());
    }

    public <V> Future<V> submit(Runnable command, @NotNull Priority priority, V returnValue) {
        return this.submit(this.convert(command, returnValue), priority.getValue());
    }

    @Override
    public void execute(@NotNull Runnable command) {
        this.submit(command, Priority.DEFAULT);
    }

    public void setRealNumOfThreads(int newNumOfThreads) {
        if (isRunning) {
            int numOfThreadsDiff;

            if (this.realNumOfThreads.get() > newNumOfThreads) {
                numOfThreadsDiff = this.realNumOfThreads.get() - newNumOfThreads;
                insertPoisonApple(numOfThreadsDiff, MAX_PRIORITY);
                this.futureNumOfThreads.set(newNumOfThreads);
                return;
            }

            if (this.realNumOfThreads.get() < newNumOfThreads) {
                numOfThreadsDiff = newNumOfThreads - this.realNumOfThreads.get();

                // check if pause
                if (this.isPause.get()) {
                    this.insertSleepingPill(numOfThreadsDiff);
                }

                this.threadCreator(numOfThreadsDiff);

                this.futureNumOfThreads.set(newNumOfThreads);
            }
        }
    }

    public void pause() {
        this.isPause.set(true);
        this.insertSleepingPill(this.futureNumOfThreads.get());
    }

    public void resume() {
        if (this.isPause.get()) {
            this.isPause.set(false);
            for (int i = 0; i < this.realNumOfThreads.get(); ++i) {
                this.pausing.release();
            }
        }
    }

    public void shutDown() {
        this.resume();
        insertPoisonApple(this.futureNumOfThreads.get(), MIN_PRIORITY);
        this.isRunning = false;
    }

    public void awaitTermination() throws InterruptedException {
        while (0 != this.realNumOfThreads.get()) {
            TimeUnit.SECONDS.sleep(1);
        }
    }

    //TODO adding timeOUT whit No busy Waiting = semaphore with bad apple
    public void awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException, TimeoutException {
        long waitingTime = System.currentTimeMillis() + unit.toMillis(timeout);

        while (0 != this.realNumOfThreads.get() && waitingTime >= System.currentTimeMillis()) {
            TimeUnit.SECONDS.sleep(1);
        }
        if (0 != this.realNumOfThreads.get()) {
            throw new TimeoutException();
        }
    }

    /*----------------------------------------------------------------*/

    private void insertSleepingPill(int numOfPills) {
        for (int i = 0; i < numOfPills; ++i) {
            this.submit(() -> {
                this.pausing.acquire();
                return null;
            }, MAX_PRIORITY);
        }
    }

    private void insertPoisonApple(int numOfApples, int priority) {
        for (int i = 0; i < numOfApples; ++i) {
            this.submit(() -> {
                        ((WorkingThread) (Thread.currentThread())).isStopped = true;
                        return null;
                    },
                    priority);
        }
    }

    private <V> @Nullable Future<V> submit(Callable<V> command, int priority) {
        if (this.isRunning) {
            Task<V> newTask = new Task<>(command, priority);
            this.tasks.enqueue(newTask);
            return newTask.getFuture();
        }
        return null;
    }

    @Contract(pure = true)
    private <T> @NotNull Callable<T> convert(Runnable command, T result) {
        return () -> {
            command.run();
            return result;
        };
    }

    private void threadCreator(int nuOfThreadsToCreate) {
        for (int i = 0; i < nuOfThreadsToCreate; ++i) {
            (new WorkingThread()).start();
        }
    }

    /*----------------------------------------------------------------*/
    public enum Priority {
        LOW(1),
        DEFAULT(5),
        HIGH(10);

        private final int value;


        Priority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /*----------------------------------------------------------------*/
    private class WorkingThread extends Thread {
        private volatile boolean isStopped = false;

        @Override
        public void run() {
            while (!isStopped) {
                try {
                    ThreadPool.this.tasks.dequeue().execute();
                } catch (InterruptedException ie) {
                    Thread.interrupted(); // clear isInterrupted flag
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            ThreadPool.this.realNumOfThreads.decrementAndGet();
        }
    }

    /*----------------------------------------------------------------*/
    private class Task<V> implements Comparable<Task<V>> {

        private final int priority;

        private final TaskFuture<V> future;

        public Task(Callable<V> callable, int priority) {
            this.priority = priority;
            this.future = new TaskFuture<>(callable, this);
        }

        public void execute() throws Exception {
            this.future.run();
        }

        public TaskFuture<V> getFuture() {
            return this.future;
        }

        @Override
        public int compareTo(@NotNull Task<V> task) {
            return Integer.compare(task.priority, this.priority);
        }

    }

    /*----------------------------------------------------------------*/
    private class TaskFuture<V> implements Future<V> {

        private V value;
        private final Callable<V> callable;
        private boolean isCanceled;
        private boolean done;
        private final Task<V> task;
        private Thread thread;
        private ExecutionException execution;

        public TaskFuture(Callable<V> callable, Task<V> task) {
            this.callable = callable;
            this.isCanceled = false;
            this.task = task;
            this.thread = null;

        }

        public void run() {
            try {
                value = callable.call();
            } catch (Exception e) {
                this.execution = new ExecutionException(e);
            }
            this.done = true;
            this.thread = Thread.currentThread();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (this.isDone() || this.isCanceled) {
                return false;
            }

            this.isCanceled = ThreadPool.this.tasks.remove(this.task);
            if (this.isCanceled) {
                return true;
            }

            if (mayInterruptIfRunning) {
                this.thread.interrupt();
                this.isCanceled = true;
                return true;
            }

            return false;
        }

        @Override
        public boolean isCancelled() {
            return this.isCanceled;
        }

        @Override
        public boolean isDone() {
            return this.done;
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            // check if already cancelled
            if (this.isCancelled()) {
                throw new CancellationException();
            }

            while (!this.isDone()) {
                try {
                    TimeUnit.MILLISECONDS.sleep(1);
                } catch (InterruptedException e) {
                    throw new InterruptedException();
                }

                if (null != this.execution) {
                    throw this.execution;
                }
            }
            return this.value;
        }

        @Override
        public V get(long timeout, @NotNull TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            // check if already cancelled
            if (this.isCancelled()) {
                throw new CancellationException();
            }

            long waitingTime = System.currentTimeMillis() + unit.toMillis(timeout);

            while (!this.isDone() && waitingTime >= System.currentTimeMillis()) {
                try {
                    TimeUnit.MILLISECONDS.sleep(1);
                } catch (InterruptedException e) {
                    throw new InterruptedException();
                } catch (Exception ex) {
                    throw new ExecutionException(ex);
                }
            }

            if (null != this.execution){
                throw this.execution;
            }

            if (!this.isDone()) {
                throw new TimeoutException();
            }

            return value;
        }
    }
}
