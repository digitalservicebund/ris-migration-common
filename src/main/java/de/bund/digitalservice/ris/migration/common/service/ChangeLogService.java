package de.bund.digitalservice.ris.migration.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
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

	public synchronized String buildChangeLog() {
		ObjectNode root = jsonMapper.createObjectNode();
		ArrayNode changedArray = root.putArray("changed");
		changed.forEach(changedArray::add);
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
