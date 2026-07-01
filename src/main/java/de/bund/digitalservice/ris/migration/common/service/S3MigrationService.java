package de.bund.digitalservice.ris.migration.common.service;

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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
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

  public void uploadFolder(String localPath, String migrationType) throws IOException {
    log.info("Publishing from {} to s3://{}", localPath, destBucket);
    Path rootPath = Paths.get(localPath);

    if (!Files.exists(rootPath)) {
      log.warn("Source directory {} does not exist. Skipping upload.", localPath);
      return;
    }

    try {
      UploadDirectoryRequest uploadDirectoryRequest =
          UploadDirectoryRequest.builder().source(rootPath).bucket(destBucket).build();
      DirectoryUpload directoryUpload =
          destinationTransferManager.uploadDirectory(uploadDirectoryRequest);
      long numberOfUploadedFiles;
      try (Stream<Path> stream = Files.walk(rootPath)) {
        if (migrationType.equals("monthly")) {
          numberOfUploadedFiles = stream.parallel().filter(Files::isRegularFile).count();
          log.info(
              "Computed number of {} files. No changelog is computed for monthly migration.",
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
    } catch (Exception e) {
      throw new IOException("Failed to upload local directory", e);
    }
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

  public void writeChangeLog(String migrationType) {
    String changeLog =
        migrationType.equals("monthly")
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
    return "changelogs/" + timestamp + ".json";
  }
}
