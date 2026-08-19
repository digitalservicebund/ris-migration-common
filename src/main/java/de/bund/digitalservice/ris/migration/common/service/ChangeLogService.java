package de.bund.digitalservice.ris.migration.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class ChangeLogService {

  private final List<String> changed = new CopyOnWriteArrayList<>();
  private final List<String> deleted = new CopyOnWriteArrayList<>();
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

    Set<String> deletedSet = new HashSet<>(deleted);

    ArrayNode changedArray = root.putArray("changed");
    changed.stream().filter(filename -> !deletedSet.contains(filename)).forEach(changedArray::add);
    changed.clear();

    ArrayNode deletedArray = root.putArray("deleted");
    deleted.forEach(deletedArray::add);
    deleted.clear();

    try {
      return jsonMapper.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public String buildChangeLogAll() {
    changed.clear();
    deleted.clear();
    return "{\"change_all\":true}";
  }
}
