package de.bund.digitalservice.ris.migration.common.writer;

import de.bund.digitalservice.ris.migration.common.model.DocumentNumberReference;
import de.bund.digitalservice.ris.migration.common.service.S3MigrationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/**
 * Deletes documents from S3 and removes corresponding records from the database. Project wires via
 * constructor lambdas so the library stays decoupled from project-specific entity types.
 *
 * <p>Deletes one published file per configured extension for each document, at the per-document
 * paths {@link FileItemWriter} wrote them to, in one batched request per chunk. No-op for the S3
 * delete when {@code s3MigrationService} is {@code null} (local mode, no cloud profile active) —
 * the database deletion still runs.
 *
 * @param <T> the project's own record type for a migrated document, looked up and deleted through
 *     the supplied callbacks
 */
@RequiredArgsConstructor
@Slf4j
public class S3DeletionWriter<T> implements ItemWriter<DocumentNumberReference> {

  private final S3MigrationService s3MigrationService;
  private final Function<String, Optional<T>> recordFinder;
  private final Consumer<T> recordDeleter;
  private final List<String> fileExtensions;

  /**
   * Deletes each document's files from the bucket and its database record. Skips the S3 delete in
   * local mode, and logs a warning for document numbers without a database record.
   *
   * @param chunk document numbers identifying the documents to delete
   */
  @Override
  public void write(Chunk<? extends DocumentNumberReference> chunk) {
    if (chunk.isEmpty()) {
      return;
    }

    List<String> filesToDelete = new ArrayList<>(chunk.size() * fileExtensions.size());
    for (DocumentNumberReference ref : chunk) {
      for (String fileExtension : fileExtensions) {
        filesToDelete.add(FileItemWriter.relativePath(ref.documentNumber(), fileExtension));
      }
    }
    deleteFromBucket(filesToDelete);

    for (DocumentNumberReference ref : chunk) {
      String documentNumber = ref.documentNumber();
      recordFinder
          .apply(documentNumber)
          .ifPresentOrElse(
              entity -> {
                recordDeleter.accept(entity);
                log.info("Removed documentation unit {} from database.", documentNumber);
              },
              () ->
                  log.warn(
                      "Documentation unit {} not found in database for removal.", documentNumber));
    }
  }

  /**
   * Removes the chunk's files in one batched request. The keys are relative to the destination
   * prefix, which {@link S3MigrationService#deleteDocuments} prepends.
   */
  private void deleteFromBucket(List<String> relativeKeys) {
    if (s3MigrationService == null) {
      log.info("Local mode: skipping S3 delete for {} file(s)", relativeKeys.size());
      return;
    }
    s3MigrationService.deleteDocuments(relativeKeys);
    relativeKeys.forEach(key -> log.info("Removed {} from bucket.", key));
  }
}
