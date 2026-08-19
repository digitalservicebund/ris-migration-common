package de.bund.digitalservice.ris.migration.common.service;

import de.bund.digitalservice.ris.migration.common.config.MigrationType;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.DeletedObject;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.DirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest;

@Slf4j
@RequiredArgsConstructor
public class S3MigrationService {

  public record MonthlyImportSource(String prefix, LocalDate baseline) {}

  private final S3Client sourceClient;
  private final S3Client destClient;
  private final S3TransferManager destinationTransferManager;
  private final String sourceBucket;
  private final String destBucket;
  private final ChangeLogService changeLogService;
  private final BucketPrefixBuilder bucketPrefixBuilder;
  private final Predicate<String> keyFilter;
  private final LocalDate monthlyStart;
  private final boolean monthlyCleanupEnabled;
  private final boolean monthlyCleanupDryRun;

  private static final String CHANGELOG_PREFIX = "changelogs/";
  private static final int KEY_LOG_BATCH_SIZE = 100;

  public String resolveDailySourcePath(LocalDate date) {
    return bucketPrefixBuilder.buildDailyPrefix(date.minusDays(1));
  }

  public MonthlyImportSource resolveMonthlyPrefix(LocalDate date) {
    return resolveMonthlyPrefixForDate(date.minusDays(1));
  }

  private MonthlyImportSource resolveMonthlyPrefixForDate(LocalDate date) {
    YearMonth yearMonth = YearMonth.from(date);
    String monthlyPrefix = bucketPrefixBuilder.buildMonthlyPrefix(yearMonth);
    log.info("Resolve source path from s3://{}/{}", sourceBucket, monthlyPrefix);
    if (!sourceClient
        .listObjectsV2(
            ListObjectsV2Request.builder()
                .bucket(sourceBucket)
                .prefix(monthlyPrefix)
                .delimiter("/")
                .build())
        .commonPrefixes()
        .isEmpty()) {
      LocalDate baseline = yearMonth.minusMonths(1).atEndOfMonth().minusDays(5);
      return new MonthlyImportSource(monthlyPrefix, baseline);
    } else {
      LocalDate oneMonthBefore = date.minusMonths(1);
      if (oneMonthBefore.isBefore(monthlyStart)) {
        throw new IllegalStateException("No monthly data found!");
      }
      log.info("No data found, try month before: {}.", oneMonthBefore);
      return resolveMonthlyPrefixForDate(oneMonthBefore);
    }
  }

  public void assertFolderExists(String prefix) throws FileNotFoundException {
    ListObjectsV2Request request =
        ListObjectsV2Request.builder().bucket(sourceBucket).prefix(prefix).maxKeys(1).build();
    if (sourceClient.listObjectsV2(request).contents().isEmpty()) {
      throw new FileNotFoundException("Daily S3 folder not found: " + prefix);
    }
  }

  public void downloadFolder(String sourcePrefix, String localDest) {
    log.info("Downloading from s3://{}/{} to {}", sourceBucket, sourcePrefix, localDest);
    AtomicInteger successCount = new AtomicInteger();
    try (ExecutorService executor = Executors.newFixedThreadPool(50)) {
      ListObjectsV2Iterable responses =
          sourceClient.listObjectsV2Paginator(
              ListObjectsV2Request.builder().bucket(sourceBucket).prefix(sourcePrefix).build());
      for (ListObjectsV2Response page : responses) {
        downloadPage(localDest, sourcePrefix, page, executor, successCount);
      }
    }
    log.info(
        "Downloaded {} file(s) from s3://{}/{}", successCount.get(), sourceBucket, sourcePrefix);
  }

  private void downloadPage(
      String localDest,
      String sourcePrefix,
      ListObjectsV2Response page,
      ExecutorService executor,
      AtomicInteger successCount) {
    for (S3Object s3Object : page.contents()) {
      String key = s3Object.key();
      if (keyFilter.test(key)) {
        executor.submit(
            () -> {
              try {
                downloadFile(localDest, sourcePrefix, key);
                successCount.incrementAndGet();
              } catch (S3Exception | IOException e) {
                log.error("Failed to download file: {}", key, e);
              }
            });
      }
    }
  }

  private void downloadFile(String localDest, String sourcePrefix, String s3Key)
      throws IOException {
    Path localDestPath = Path.of(localDest).toAbsolutePath().normalize();
    Path destinationPath =
        localDestPath.resolve(s3Key.substring(sourcePrefix.length())).normalize();
    if (!destinationPath.startsWith(localDestPath)) {
      throw new IOException("S3 key escapes destination directory: " + s3Key);
    }
    Path parent = destinationPath.getParent();
    if (parent != null && Files.notExists(parent)) {
      Files.createDirectories(parent);
    }
    sourceClient.getObject(
        GetObjectRequest.builder().bucket(sourceBucket).key(s3Key).build(), destinationPath);
  }

  /**
   * Uploads the local folder to the destination bucket.
   *
   * @return for {@link MigrationType#MONTHLY}, the set of S3 keys that were uploaded (relative to
   *     {@code destBucket} root), used for post-upload reconciliation of stale objects. Empty for
   *     other migration types, since those are tracked via {@link ChangeLogService} instead.
   */
  public Set<String> uploadFolder(String localPath, MigrationType migrationType)
      throws IOException {
    log.info("Publishing from {} to s3://{}", localPath, destBucket);
    Path rootPath = Paths.get(localPath);

    if (!Files.exists(rootPath)) {
      log.warn("Source directory {} does not exist. Skipping upload.", localPath);
      return Set.of();
    }

    try {
      UploadDirectoryRequest uploadDirectoryRequest =
          UploadDirectoryRequest.builder().source(rootPath).bucket(destBucket).build();
      DirectoryUpload directoryUpload =
          destinationTransferManager.uploadDirectory(uploadDirectoryRequest);
      Set<String> uploadedKeys = Set.of();
      long numberOfUploadedFiles;
      try (Stream<Path> stream = Files.walk(rootPath)) {
        if (migrationType == MigrationType.MONTHLY) {
          uploadedKeys =
              stream
                  .parallel()
                  .filter(Files::isRegularFile)
                  .map(file -> toS3Key(rootPath, file))
                  .collect(Collectors.toUnmodifiableSet());
          numberOfUploadedFiles = uploadedKeys.size();
          log.info(
              "Computed {} file(s) for monthly reconciliation. No changelog is computed for"
                  + " monthly migration.",
              numberOfUploadedFiles);
        } else {
          List<String> uploadedFiles =
              stream.parallel().filter(Files::isRegularFile).map(Path::toString).toList();
          numberOfUploadedFiles = uploadedFiles.size();
          uploadedFiles.forEach(changeLogService::addChanged);
          log.info("Computed list of {} files for changelog.", numberOfUploadedFiles);
        }
      }
      CompletedDirectoryUpload completedUpload = directoryUpload.completionFuture().join();
      if (!completedUpload.failedTransfers().isEmpty()) {
        completedUpload
            .failedTransfers()
            .forEach(fail -> log.warn("Failed uploading: {}", fail.exception().getMessage()));
        throw new IOException(
            completedUpload.failedTransfers().size() + " file(s) failed to upload to S3");
      }
      log.info("Completed upload of {} file(s).", numberOfUploadedFiles);
      return uploadedKeys;
    } catch (Exception e) {
      throw new IOException("Failed to upload local directory", e);
    }
  }

  private static String toS3Key(Path root, Path file) {
    return root.relativize(file).toString().replace(File.separatorChar, '/');
  }

  public void delete(String filename) {
    try {
      DeleteObjectRequest request =
          DeleteObjectRequest.builder().bucket(destBucket).key(filename).build();
      destClient.deleteObject(request);
      changeLogService.addDeleted(filename);
    } catch (S3Exception e) {
      throw new UncheckedIOException(new IOException(e));
    }
  }

  /**
   * Batch-deletes the given keys from the destination bucket in chunks of up to 1000 (the S3
   * DeleteObjects limit), recording each successfully deleted key via {@link ChangeLogService}.
   */
  public void deleteObjects(Collection<String> keys) {
    if (keys.isEmpty()) {
      return;
    }
    List<String> keyList = new ArrayList<>(keys);
    for (int i = 0; i < keyList.size(); i += 1000) {
      List<String> chunk = keyList.subList(i, Math.min(i + 1000, keyList.size()));
      List<ObjectIdentifier> identifiers =
          chunk.stream().map(key -> ObjectIdentifier.builder().key(key).build()).toList();
      DeleteObjectsRequest request =
          DeleteObjectsRequest.builder()
              .bucket(destBucket)
              .delete(d -> d.objects(identifiers))
              .build();
      try {
        DeleteObjectsResponse response = destClient.deleteObjects(request);
        List<String> deletedKeys = response.deleted().stream().map(DeletedObject::key).toList();
        deletedKeys.forEach(changeLogService::addDeleted);
        logKeys("Deleted from destination bucket", deletedKeys);
        if (response.hasErrors()) {
          response
              .errors()
              .forEach(
                  error -> log.error("Failed to delete key {}: {}", error.key(), error.message()));
        }
      } catch (S3Exception e) {
        throw new UncheckedIOException(new IOException(e));
      }
    }
  }

  public void writeChangeLog(MigrationType migrationType) {
    String changeLog =
        migrationType == MigrationType.MONTHLY
            ? changeLogService.buildChangeLogAll()
            : changeLogService.buildChangeLog();
    saveChangelog(createChangeLogKey(), changeLog);
  }

  public void saveChangelog(String filename, String content) {
    PutObjectRequest putObjectRequest =
        PutObjectRequest.builder()
            .bucket(destBucket)
            .key(filename)
            .contentType("application/json")
            .build();
    try {
      destClient.putObject(putObjectRequest, RequestBody.fromString(content));
      log.info("Successfully published changelog to {}", destBucket);
    } catch (S3Exception e) {
      log.error("Failed to save changelog", e);
      throw new UncheckedIOException(new IOException(e));
    }
  }

  private static String createChangeLogKey() {
    String timestamp =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now());
    return CHANGELOG_PREFIX + timestamp + ".json";
  }

  /**
   * Reconciles the destination bucket after a monthly upload: any object present in the bucket that
   * is not part of {@code expectedKeys} (the set just uploaded by {@link #uploadFolder(String,
   * MigrationType)}) is considered stale and removed. No-op unless {@code
   * app.monthly-cleanup.enabled} is set; logs what would be deleted instead of deleting when {@code
   * app.monthly-cleanup.dry-run} is set. Either way the affected keys are logged. Must only be
   * called after a successful upload, so that stale detection never runs against a
   * partially-published bucket.
   */
  public void reconcileDestination(Set<String> expectedKeys) {
    if (!monthlyCleanupEnabled) {
      log.info("Monthly cleanup disabled. Skipping destination reconciliation.");
      return;
    }
    Set<String> staleKeys = listDestinationKeys();
    staleKeys.removeAll(expectedKeys);
    if (staleKeys.isEmpty()) {
      log.info("No stale objects found in destination bucket during monthly reconciliation.");
      return;
    }
    if (monthlyCleanupDryRun) {
      log.info(
          "Dry-run: would delete {} stale object(s) from destination bucket.", staleKeys.size());
      logKeys("Dry-run: would delete", staleKeys);
      return;
    }
    log.info("Deleting {} stale object(s) from destination bucket.", staleKeys.size());
    deleteObjects(staleKeys);
  }

  /**
   * Logs {@code keys} across several records of at most {@link #KEY_LOG_BATCH_SIZE} keys each. A
   * monthly reconciliation can touch hundreds of thousands of keys, which as a single record would
   * be too large for most log pipelines to accept.
   */
  private static void logKeys(String action, Collection<String> keys) {
    List<String> keyList = keys instanceof List<String> list ? list : new ArrayList<>(keys);
    for (int i = 0; i < keyList.size(); i += KEY_LOG_BATCH_SIZE) {
      List<String> batch = keyList.subList(i, Math.min(i + KEY_LOG_BATCH_SIZE, keyList.size()));
      log.info("{} [{}-{}/{}]: {}", action, i + 1, i + batch.size(), keyList.size(), batch);
    }
  }

  private Set<String> listDestinationKeys() {
    Set<String> keys = new HashSet<>();
    ListObjectsV2Iterable pages =
        destClient.listObjectsV2Paginator(
            ListObjectsV2Request.builder().bucket(destBucket).build());
    for (ListObjectsV2Response page : pages) {
      for (S3Object object : page.contents()) {
        String key = object.key();
        if (!key.startsWith(CHANGELOG_PREFIX)) {
          keys.add(key);
        }
      }
    }
    return keys;
  }
}
