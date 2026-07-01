package de.bund.digitalservice.ris.migration.common.writer;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamWriter;

/**
 * Writes migration output items as files to the configured output directory.
 */
public class FileItemWriter<T extends MigrationOutputItem> implements ItemStreamWriter<T> {

	private final String outputDirectory;
	private final String fileExtension;
	private final Predicate<T> writeFilter;

	public FileItemWriter(String outputDirectory, String fileExtension) {
		this(outputDirectory, fileExtension, _ -> true);
	}

	public FileItemWriter(String outputDirectory, String fileExtension, Predicate<T> writeFilter) {
		this.outputDirectory = outputDirectory;
		this.fileExtension = fileExtension;
		this.writeFilter = writeFilter;
	}

	@Override
	public void open(@Nonnull ExecutionContext executionContext) throws ItemStreamException {
		try {
			Files.createDirectories(Path.of(outputDirectory));
		} catch (IOException e) {
			throw new ItemStreamException(e);
		}
	}

	@Override
	public void write(Chunk<? extends T> chunk) {
		chunk.getItems().stream().filter(writeFilter)
				.forEach(item -> writeToOutput(item, outputDirectory, fileExtension));
	}

	public static <T extends MigrationOutputItem> void writeToOutput(T item, String outputDirectory,
			String fileExtension) {
		try {
			Path outputDir = Path.of(outputDirectory).toAbsolutePath().normalize();
			Path targetPath = outputDir.resolve(item.getDocumentNumber() + fileExtension).normalize();
			if (!targetPath.startsWith(outputDir)) {
				throw new IllegalArgumentException("Invalid document number: " + item.getDocumentNumber());
			}
			Files.writeString(targetPath, item.getXmlContent(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
