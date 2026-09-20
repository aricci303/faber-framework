package faber.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The mailbox that artifacts, the user-facing channel, and (eventually)
 * other agents write into continuously and asynchronously. Unchanged
 * from the first version of this harness — the design held up.
 */
public final class EventQueue {

    private final BlockingQueue<Percept> queue = new LinkedBlockingQueue<>();

    public void publish(Percept percept) {
        queue.offer(percept);
        // System.out.println("NEW PERCEPT IN QUEUE: " + percept + " - " + queue.size());
    }

    public List<Percept> drainAll() {
        List<Percept> drained = new ArrayList<>();
        // System.out.println("DRAINING QUEUE: " + queue.size());
        queue.drainTo(drained);
        // System.out.println("PERCEPTS DRAINED: " + drained.size());
        return drained;
    }

    public List<Percept> awaitAtLeastOne(long timeoutMillis) throws InterruptedException {
        // System.out.println("AWAIT-POLLING QUEUE: " + queue.size());
    	Percept first = queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        List<Percept> drained = new ArrayList<>();
        if (first != null) drained.add(first);
        queue.drainTo(drained);
        // System.out.println("AWAIT-PERCEPTS DRAINED: " + drained.size());
        return drained;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
