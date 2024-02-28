# Thread Pool Library

The Thread Pool library is a Java implementation providing a robust and efficient thread pool mechanism for concurrent task execution. It offers various features to manage and control the execution of tasks, including dynamic thread management, task prioritization, pausing and resuming operations, and graceful termination.

This library utilizes a waitable queue implementation for managing tasks within the thread pool.

## Features

- **Task Submission**: Allows users to submit tasks to the thread pool for execution. Tasks can be either `Runnable` or `Callable`, with optional priority settings.
- **Dynamic Thread Management**: Users can specify the number of threads to be created in the pool or use the default setting, which automatically creates threads based on the available CPU cores.
- **Future Object**: Provides a `Future` object upon task submission, enabling users to track task status and obtain results asynchronously.
- **Priority Queue**: Implements a waitable priority queue to manage task execution order based on priority levels.
- **Thread Pool Adjustment**: Supports dynamic adjustment of the thread pool size while it's running, allowing for efficient resource management.
- **Pausing and Resuming**: Offers methods for pausing and resuming thread pool operations, providing flexibility in controlling task execution.
- **Non-blocking Shutdown**: Supports non-blocking termination of the thread pool, ensuring graceful shutdown of all threads.
- **Blocking Termination**: Provides methods for blocking termination of the thread pool, allowing the calling thread to wait until all threads are closed, with optional timeout settings.

## Waitable Queue

The Thread Pool library utilizes my waitable queue implementation for managing tasks within the thread pool.

## Methods

- `submit(task: Runnable/Callable, priority: enum)`: Add a new task to be executed by the thread pool, with an optional priority level.
- `setNumOfThreads(numThreads: int)`: Change the number of threads in the pool dynamically.
- `pause()`: Pause all threads until `resume()` is called (does not pause tasks in execution).
- `resume()`: Resume the operation of the thread pool after pausing.
- `shutDown()`: Close the thread pool gracefully (non-blocking).
- `awaitTermination(timeout: long, unit: TimeUnit)`: Block the calling thread until all threads in the pool are closed, with an optional timeout.

## Future (Returned Object) Methods

- `cancel(boolean mayInterruptIfRunning)`: Remove a task from the queue or interrupt the running thread if applicable.
- `isCanceled()`: Check if a task was canceled.
- `isDone()`: Check if a task has completed.
- `get()`: Get the result value of a `Callable`. This method is blocking and throws an exception if the task was canceled or encountered an exception.
- `get(timeout: long, unit: TimeUnit)`: Get the result value of a `Callable` with a specified timeout duration.

## Usage

```java
// Example usage of the Thread Pool library
import il.co.ILRD.thread_pool.ThreadPool;
import il.co.ILRD.thread_pool.ThreadPool.Priority;

public class Main {
    public static void main(String[] args) {
        // Create a thread pool with default settings
        ThreadPool threadPool = new ThreadPool();

        // Submit a task with default priority
        threadPool.submit(() -> {
            // Task logic
            System.out.println("Task executed");
        });

        // Submit a task with high priority
        threadPool.submit(() -> {
            // Task logic
            System.out.println("High priority task executed");
        }, Priority.HIGH);

        // Pause the thread pool
        threadPool.pause();

        // Resume the thread pool
        threadPool.resume();

        // Shut down the thread pool
        threadPool.shutDown();
    }
}
