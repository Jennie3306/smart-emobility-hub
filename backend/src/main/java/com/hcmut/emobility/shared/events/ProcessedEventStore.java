package com.hcmut.emobility.shared.events;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chong xu ly trung: moi handler kiem tra eventId truoc khi xu ly.
 * Ban demo giu trong bo nho, gioi han 10.000 ban ghi gan nhat.
 */
@Component
public class ProcessedEventStore {

    private static final int MAX = 10_000;

    private final Set<UUID> processed = Collections.newSetFromMap(
        Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                return size() > MAX;
            }
        }));

    /** true neu su kien nay chua tung duoc xu ly (va danh dau la da xu ly). */
    public boolean markIfNew(UUID eventId) {
        return processed.add(eventId);
    }
}
