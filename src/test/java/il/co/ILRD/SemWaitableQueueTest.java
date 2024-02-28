package il.co.ILRD;

import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.testng.AssertJUnit.*;

public class SemWaitableQueueTest {

    @Test
    public void testThreadSafety() throws InterruptedException {
        final int numThreads = 100;
        final int numOperations = 1000;
        final AtomicInteger successfulEnqueueCount = new AtomicInteger(0);
        final AtomicInteger successfulDequeueCount = new AtomicInteger(0);
        SemWaitableQueue<Integer> queue = new SemWaitableQueue<>(numThreads * numOperations);

        Runnable producer = () -> {
            for (int i = 0; i < numOperations; i++) {
                if (queue.enqueue(i)) {
                    successfulEnqueueCount.incrementAndGet();
                }
            }
        };

        Runnable consumer = () -> {
            for (int i = 0; i < numOperations; i++) {
                if (queue.dequeue() != null) {
                    successfulDequeueCount.incrementAndGet();
                }
            }
        };

        Thread[] producers = new Thread[numThreads];
        Thread[] consumers = new Thread[numThreads];

        for (int i = 0; i < numThreads; i++) {
            producers[i] = new Thread(producer);
            consumers[i] = new Thread(consumer);
            producers[i].start();
            if (i != numThreads - 1) {
                consumers[i].start();
            }
        }

        for (int i = 0; i < numThreads; i++) {
            producers[i].join();
            if (i != numThreads - 1) {
                consumers[i].join();
            }
        }

        assertEquals(successfulEnqueueCount.get() - successfulDequeueCount.get(), queue.size());
        assertFalse(queue.isEmpty());
    }

    @Test
    public void testPriorityQueueWithComparator() {
        SemWaitableQueue<Integer> queue = new SemWaitableQueue<>(Comparator.comparingInt(Integer::intValue), 10);
        queue.enqueue(5);
        queue.enqueue(10);
        queue.enqueue(1);

        assertEquals(Integer.valueOf(1), queue.peek());
    }

    @Test
    public void testRemoveMethod() {
        SemWaitableQueue<Integer> queue = new SemWaitableQueue<>(10);
        queue.enqueue(5);
        queue.enqueue(10);
        queue.enqueue(1);

        assertTrue(queue.remove(10));
        assertEquals(2, queue.size());
    }

    @Test
    public void testPeekOnEmptyQueue() {
        SemWaitableQueue<Integer> queue = new SemWaitableQueue<>(10);
        assertNull(queue.peek());
    }
    @Test
    public void testCapacity() throws InterruptedException {
        final int DEF_CAPACITY = 20;
        final int NUM_OF_THREADS = 2;
        final int NUM_OF_ELEMENT = DEF_CAPACITY * 3;
        final SemWaitableQueue<String> writableQueue = new SemWaitableQueue<>(DEF_CAPACITY);
        final List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < NUM_OF_THREADS; i++) {
            threads.add(new Thread(() -> {
                for (int j = 0; j < NUM_OF_ELEMENT; j++) {
                    writableQueue.enqueue(Thread.currentThread().getName() + j);
                }
            }));

            threads.add(new Thread(() -> {
                for (int j = 0; j < NUM_OF_ELEMENT; j++) {
                    writableQueue.dequeue();
                }
            }));
        }
        threads.forEach(Thread::start);
        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue(writableQueue.isEmpty());

    }

}
