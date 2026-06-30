package de.bund.digitalservice.ris.migration.common.writer;

import de.bund.digitalservice.ris.migration.common.model.DocumentNumberReference;
import de.bund.digitalservice.ris.migration.common.service.S3MigrationService;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/**
 * Deletes documents from S3 and removes corresponding records from the
 * database. Project wires via constructor lambdas so the library stays
 * decoupled from project-specific entity types.
 */
@RequiredArgsConstructor
@Slf4j
public class S3DeletionWriter<T> implements ItemWriter<DocumentNumberReference> {

	private final S3MigrationService s3MigrationService;
	private final Function<String, Optional<T>> recordFinder;
	private final Consumer<T> recordDeleter;
	private final String fileExtension;

	@Override
	public void write(Chunk<? extends DocumentNumberReference> chunk) {
		for (DocumentNumberReference ref : chunk) {
			String documentNumber = ref.documentNumber();
			String filename = documentNumber + fileExtension;
			s3MigrationService.delete(filename);
			log.info("Removed documentation unit {} from bucket.", filename);
			recordFinder.apply(documentNumber).ifPresentOrElse(record -> {
				recordDeleter.accept(record);
				log.info("Removed documentation unit {} from database.", documentNumber);
			}, () -> log.warn("Documentation unit {} not found in database for removal.", documentNumber));
		}
	}
}
