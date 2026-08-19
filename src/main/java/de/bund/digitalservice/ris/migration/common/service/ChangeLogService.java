package de.bund.digitalservice.ris.migration.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Service;

@Service
public class ChangeLogService {

  /**
   * Concurrent queues rather than {@code CopyOnWriteArrayList}: entries are appended once per
   * migrated or deleted file — hundreds of thousands of them during a monthly run — and read
   * exactly once at the end, so copy-on-write would make accumulation quadratic.
   */
  private final Queue<String> changed = new ConcurrentLinkedQueue<>();

  private final Queue<String> deleted = new ConcurrentLinkedQueue<>();
  private final JsonMapper jsonMapper = new JsonMapper();

  public void addChanged(String filename) {
    changed.add(filename);
  }

  public void addDeleted(String filename) {
    deleted.add(filename);
  }

  /**
   * Builds the incremental changelog from files changed or deleted during the run.
   *
   * <p>If a filename appears in both {@code changed} and {@code deleted}, it will only be included
   * in the {@code deleted} section.
   *
   * <p>The collected entries are cleared after they are copied into the JSON payload.
   *
   * @return changelog JSON containing {@code changed} and {@code deleted} arrays
   */
  public synchronized String buildChangeLog() {
    ObjectNode root = jsonMapper.createObjectNode();

    List<String> deletedEntries = drain(deleted);
    Set<String> deletedSet = new HashSet<>(deletedEntries);

    ArrayNode changedArray = root.putArray("changed");
    drain(changed).stream()
        .filter(filename -> !deletedSet.contains(filename))
        .forEach(changedArray::add);

    ArrayNode deletedArray = root.putArray("deleted");
    deletedEntries.forEach(deletedArray::add);

    try {
      return jsonMapper.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public synchronized String buildChangeLogAll() {
    changed.clear();
    deleted.clear();
    return "{\"change_all\":true}";
  }

  /**
   * Removes and returns everything currently queued, so no entry is dropped between read and reset.
   */
  private static List<String> drain(Queue<String> queue) {
    List<String> entries = new ArrayList<>();
    for (String entry = queue.poll(); entry != null; entry = queue.poll()) {
      entries.add(entry);
    }
    return entries;
  }
}
