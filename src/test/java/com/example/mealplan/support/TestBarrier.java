package com.example.mealplan.support;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The rendezvous point the concurrency test uses to hold two transactions open at the same instant.
 *
 * <p>The decorator that calls it only knows {@link #arriveAndWait()}, with no arguments, so the mode
 * is armed by each test before it launches its threads and disarmed when it finishes. There is no
 * {@code reset()}: disarming and the two arming methods each leave a clean state, which is one way
 * fewer to leave the next test hanging.
 *
 * <p>Every wait times out after ten seconds and throws. A coordination mistake has to show up as a
 * red test, never as a suite that hangs.
 */
public class TestBarrier {

    private static final long TIMEOUT_SECONDS = 10L;

    /** Symmetric mode: both threads wait for each other. Null unless armed. */
    private volatile CyclicBarrier symmetric;

    /** Handoff mode: opened by the thread that arrives, waited on by the other. */
    private volatile CountDownLatch arrived;

    /** Handoff mode: opened by the other thread once it has committed. */
    private volatile CountDownLatch released;

    /** Initial state: {@link #arriveAndWait()} returns immediately. */
    public void disarm() {
        symmetric = null;
        arrived = null;
        released = null;
    }

    /** Symmetric mode, for the two scenarios where both threads do the same thing. */
    public void armForParties(int parties) {
        symmetric = new CyclicBarrier(parties);
        arrived = null;
        released = null;
    }

    /**
     * Asymmetric mode, for cooking against cancelling: only one of the two threads goes through the
     * pantry, so there is nothing for a cyclic barrier to pair it with.
     */
    public void armForHandoff() {
        symmetric = null;
        arrived = new CountDownLatch(1);
        released = new CountDownLatch(1);
    }

    /** Called by the thread that is not cooking: waits until the other has read and mutated. */
    public void awaitArrival() {
        awaitLatch(arrived, "the other thread never reached the barrier");
    }

    /** Called by the thread that is not cooking, once it has committed. */
    public void release() {
        released.countDown();
    }

    /**
     * Blocks according to the armed mode, and returns at once when there is none.
     *
     * <p>This is called from inside the transaction of cooking, after the pantry rows have been read
     * and changed in memory and before anything is committed. That instant is the only one where two
     * transactions have read the same version and neither has written.
     */
    public void arriveAndWait() {
        CyclicBarrier barrier = symmetric;
        CountDownLatch arrivedLatch = arrived;

        if (barrier != null) {
            awaitBarrier(barrier);
        } else if (arrivedLatch != null) {
            arrivedLatch.countDown();
            awaitLatch(released, "the other thread never released the barrier");
        }
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting at the barrier", interrupted);
        } catch (BrokenBarrierException | TimeoutException failure) {
            throw new IllegalStateException("The barrier never paired the two threads", failure);
        }
    }

    private static void awaitLatch(CountDownLatch latch, String message) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting: " + message, interrupted);
        }
    }
}
