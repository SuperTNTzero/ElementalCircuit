package dev.elementalcircuits.domain.logic;

import java.util.*;

/** Immutable two-stack FIFO. Runtime steps share history; serialization observes a flat list. */
final class SignalQueue extends AbstractList<RuneLogicState.Transition> {
    private static final SignalQueue EMPTY = new SignalQueue(null, null, 0);

    private record Node(RuneLogicState.Transition value, Node next) {}

    private final Node front, back;
    private final int size;

    private SignalQueue(Node front, Node back, int size) {
        this.front = front;
        this.back = back;
        this.size = size;
    }

    static SignalQueue from(List<RuneLogicState.Transition> values) {
        if (values instanceof SignalQueue queue) return queue;
        if (values.isEmpty()) return EMPTY;
        Node front = null;
        for (int i = values.size() - 1; i >= 0; i--)
            front = new Node(Objects.requireNonNull(values.get(i)), front);
        return new SignalQueue(front, null, values.size());
    }

    SignalQueue append(RuneLogicState.Transition value) {
        return new SignalQueue(front, new Node(value, back), size + 1);
    }

    SignalQueue ready() {
        if (front != null || back == null) return this;
        Node head = null;
        for (Node n = back; n != null; n = n.next) head = new Node(n.value, head);
        return new SignalQueue(head, null, size);
    }

    SignalQueue drop() {
        SignalQueue queue = ready();
        if (queue.front == null) throw new NoSuchElementException();
        return new SignalQueue(queue.front.next, queue.back, size - 1);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public RuneLogicState.Transition getFirst() {
        var queue = ready();
        if (queue.front == null) throw new NoSuchElementException();
        return queue.front.value;
    }

    @Override
    public RuneLogicState.Transition get(int index) {
        Objects.checkIndex(index, size);
        var iterator = iterator();
        while (index-- > 0) iterator.next();
        return iterator.next();
    }

    @Override
    public Iterator<RuneLogicState.Transition> iterator() {
        var tail = new ArrayDeque<RuneLogicState.Transition>();
        for (Node n = back; n != null; n = n.next) tail.push(n.value);
        return new Iterator<>() {
            private Node next = front;

            public boolean hasNext() {
                return next != null || !tail.isEmpty();
            }

            public RuneLogicState.Transition next() {
                if (next == null) return tail.pop();
                var value = next.value;
                next = next.next;
                return value;
            }
        };
    }
}
