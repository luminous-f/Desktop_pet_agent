package com.desktoppet.memory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class InMemoryConversationMemory {
    private final int maxMessages;
    private final Deque<String> messages = new ArrayDeque<>();

    public InMemoryConversationMemory(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public void add(String role, String content) {
        messages.addLast(role + ": " + content);
        while (messages.size() > maxMessages) {
            messages.removeFirst();
        }
    }

    public List<String> snapshot() {
        return new ArrayList<>(messages);
    }

    public int size() {
        return messages.size();
    }

    public List<String> drainOldest(int keepMessages) {
        List<String> drained = new ArrayList<>();
        while (messages.size() > keepMessages) {
            drained.add(messages.removeFirst());
        }
        return drained;
    }

    public void clear() {
        messages.clear();
    }
}
