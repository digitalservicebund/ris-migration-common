package de.bund.digitalservice.ris.migration.common.writer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;

class FileItemWriterTest {

	record TestItem(String number, String content) implements MigrationOutputItem {
		@Override
		public String getDocumentNumber() {
			return number;
		}

		@Override
		public String getXmlContent() {
			return content;
		}
	}

	@Test
	void open_createsOutputDirectory(@TempDir Path base) throws Exception {
		Path outDir = base.resolve("output/nested");
		var writer = new FileItemWriter<TestItem>(outDir.toString(), ".xml");

		writer.open(new ExecutionContext());

		assertThat(Files.exists(outDir)).isTrue();
	}

	@Test
	void write_createsFileWithContent(@TempDir Path outDir) throws Exception {
		var writer = new FileItemWriter<TestItem>(outDir.toString(), ".akn.xml");
		writer.open(new ExecutionContext());

		writer.write(new Chunk<>(List.of(new TestItem("DOC001", "<akn>content</akn>"))));

		Path file = outDir.resolve("DOC001.akn.xml");
		assertThat(Files.exists(file)).isTrue();
		assertThat(Files.readString(file)).isEqualTo("<akn>content</akn>");
	}

	@Test
	void write_withFilter_skipsRejectedItems(@TempDir Path outDir) throws Exception {
		var writer = new FileItemWriter<TestItem>(outDir.toString(), ".xml", item -> !item.number().startsWith("SKIP"));
		writer.open(new ExecutionContext());

		writer.write(new Chunk<>(List.of(new TestItem("OK001", "content"), new TestItem("SKIP002", "skip"))));

		assertThat(Files.exists(outDir.resolve("OK001.xml"))).isTrue();
		assertThat(Files.exists(outDir.resolve("SKIP002.xml"))).isFalse();
	}

	@Test
	void writeToOutput_staticMethod_writesFile(@TempDir Path outDir) throws Exception {
		FileItemWriter.writeToOutput(new TestItem("STATIC", "<data/>"), outDir.toString(), ".xml");

		Path file = outDir.resolve("STATIC.xml");
		assertThat(Files.exists(file)).isTrue();
		assertThat(Files.readString(file)).isEqualTo("<data/>");
	}

	@Test
	void write_emptyChunk_writesNothing(@TempDir Path outDir) throws Exception {
		var writer = new FileItemWriter<TestItem>(outDir.toString(), ".xml");
		writer.open(new ExecutionContext());

		writer.write(new Chunk<>(List.of()));

		assertThat(Files.list(outDir).count()).isZero();
	}
}
