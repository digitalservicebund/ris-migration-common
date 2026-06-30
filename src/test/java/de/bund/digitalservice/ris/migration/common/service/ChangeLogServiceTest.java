package de.bund.digitalservice.ris.migration.common.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChangeLogServiceTest {

	private ChangeLogService service;

	@BeforeEach
	void setUp() {
		service = new ChangeLogService();
	}

	@Test
	void buildChangeLog_includesChangedAndDeleted() {
		service.addChanged("doc1.xml");
		service.addDeleted("doc2.xml");

		String result = service.buildChangeLog();

		assertThat(result).contains("\"changed\"").contains("doc1.xml");
		assertThat(result).contains("\"deleted\"").contains("doc2.xml");
	}

	@Test
	void buildChangeLog_clearsListsAfterBuild() {
		service.addChanged("doc1.xml");
		service.buildChangeLog();

		String second = service.buildChangeLog();

		assertThat(second).contains("\"changed\":[]").contains("\"deleted\":[]");
	}

	@Test
	void buildChangeLogAll_returnsChangeAllFlag() {
		service.addChanged("doc1.xml");
		service.addDeleted("doc2.xml");

		String result = service.buildChangeLogAll();

		assertThat(result).isEqualTo("{\"change_all\":true}");
	}

	@Test
	void buildChangeLogAll_clearsLists() {
		service.addChanged("doc1.xml");
		service.buildChangeLogAll();

		String result = service.buildChangeLog();

		assertThat(result).contains("\"changed\":[]");
	}
}
