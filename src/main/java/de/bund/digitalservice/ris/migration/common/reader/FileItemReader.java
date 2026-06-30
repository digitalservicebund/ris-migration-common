package de.bund.digitalservice.ris.migration.common.reader;

import de.bund.digitalservice.ris.migration.common.model.JurisXml;
import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/** File item reader that reads matching files from a directory path. */
@Slf4j
public class FileItemReader implements ItemStreamReader<JurisXml> {

	private static final String CURRENT_FILE_INDEX = "current.file.index";

	private final String directoryPath;
	private final XmlDocumentItemReader delegate;
	private final Predicate<Path> validInputPath;

	private Stream<Path> pathStream;
	private Iterator<Path> pathIterator;
	private long currentFileIndex = 0;

	public FileItemReader(String directoryPath, XmlDocumentItemReader delegate, Predicate<Path> validInputPath) {
		this.directoryPath = directoryPath;
		this.delegate = delegate;
		this.validInputPath = validInputPath;
	}

	public FileItemReader(String directoryPath, XmlDocumentItemReader delegate, String fileExtension) {
		this(directoryPath, delegate, path -> Files.isRegularFile(path) && path.toString().endsWith(fileExtension));
	}

	@Override
	public void open(@Nonnull ExecutionContext executionContext) throws ItemStreamException {
		delegate.open(executionContext);
		if (executionContext.containsKey(CURRENT_FILE_INDEX)) {
			currentFileIndex = executionContext.getLong(CURRENT_FILE_INDEX);
			log.info("Restarting job, skipping the first {} files.", currentFileIndex);
		}
		Path startPath = Paths.get(directoryPath);

		if (!Files.exists(startPath)) {
			log.warn("Input directory {} does not exist, no files imported. Skipping file processing.", directoryPath);
			pathIterator = Collections.emptyIterator();
			executionContext.put("NO_INPUT_DATA", true);
			return;
		}

		try {
			log.info("Starting to search for files in path {}", startPath);
			logNumberOfFiles(startPath);
			pathStream = Files.walk(startPath).filter(validInputPath).skip(currentFileIndex);
			pathIterator = pathStream.iterator();
		} catch (IOException e) {
			throw new ItemStreamException("Error opening directory stream: " + directoryPath, e);
		}
	}

	private void logNumberOfFiles(Path startPath) throws IOException {
		try (Stream<Path> countStream = Files.walk(startPath)) {
			long totalFiles = countStream.parallel().filter(validInputPath).count();
			long filesToProcess = Math.max(0, totalFiles - currentFileIndex);
			log.info("Found {} total matching files. After skipping {}, {} files will be processed in this run.",
					totalFiles, currentFileIndex, filesToProcess);
		}
	}

	@Override
	public JurisXml read() {
		if (pathIterator != null && pathIterator.hasNext()) {
			Resource resource = new FileSystemResource(pathIterator.next());
			delegate.setResource(resource);
			JurisXml item = delegate.read();
			if (item != null) {
				currentFileIndex++;
			}
			return item;
		}
		return null;
	}

	@Override
	public void update(@Nonnull ExecutionContext executionContext) throws ItemStreamException {
		executionContext.put(CURRENT_FILE_INDEX, currentFileIndex);
		delegate.update(executionContext);
	}

	@Override
	public void close() throws ItemStreamException {
		if (pathStream != null) {
			pathStream.close();
		}
		delegate.close();
	}
}
