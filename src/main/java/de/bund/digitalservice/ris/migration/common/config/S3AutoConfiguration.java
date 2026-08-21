package de.bund.digitalservice.ris.migration.common.config;

import de.bund.digitalservice.ris.migration.common.service.BucketPrefixBuilder;
import de.bund.digitalservice.ris.migration.common.service.ChangeLogService;
import de.bund.digitalservice.ris.migration.common.service.S3MigrationService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

/**
 * Auto-configures {@link S3MigrationService} for cloud runs using project-provided S3 client beans
 * and optional customisation beans. Projects declare {@code sourceS3Client}, {@code
 * destinationS3Client}, and {@code destinationTransferManager} beans in their own {@code S3Config}.
 * Optional: {@code BucketPrefixBuilder} (default: empty sub-path) and a {@code Predicate<String>}
 * named {@code s3KeyFilter} (default: accept all keys).
 */
@AutoConfiguration(after = MigrationAutoConfiguration.class)
@Profile("cloud")
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class S3AutoConfiguration {

  /**
   * Selects which source objects a project downloads.
   *
   * @return default filter accepting every object under the source prefix; override with a bean
   *     named {@code s3KeyFilter} to narrow it
   */
  @Bean
  @ConditionalOnMissingBean(name = "s3KeyFilter")
  public Predicate<String> s3KeyFilter() {
    return _ -> true;
  }

  /**
   * Locates a project's dumps inside the shared source bucket.
   *
   * @return builder targeting the bucket root; override to place a project in its own sub-folder
   */
  @Bean
  @ConditionalOnMissingBean
  public BucketPrefixBuilder bucketPrefixBuilder() {
    return new BucketPrefixBuilder("");
  }

  /**
   * Assembles the S3 access used by the import and publish steps from the project's own client
   * beans.
   *
   * @param sourceS3Client client for the juris source bucket
   * @param destinationS3Client client for the publication bucket
   * @param destinationTransferManager bulk uploader for the publication bucket
   * @param changeLogService collector notified of published and removed documents
   * @param bucketPrefixBuilder locates a project's dumps within the source bucket
   * @param s3KeyFilter restricts which source objects are downloaded
   * @param sourceBucket bucket the juris exports are read from
   * @param destBucket bucket the migrated documents are published to
   * @param monthlyOffset how many months back a monthly run may search for a usable dump
   * @return the configured S3 access for this project
   */
  @Bean
  @ConditionalOnMissingBean
  public S3MigrationService s3MigrationService(
      @Qualifier("sourceS3Client") S3Client sourceS3Client,
      @Qualifier("destinationS3Client") S3Client destinationS3Client,
      S3TransferManager destinationTransferManager,
      ChangeLogService changeLogService,
      BucketPrefixBuilder bucketPrefixBuilder,
      @Qualifier("s3KeyFilter") Predicate<String> s3KeyFilter,
      @Value("${aws.bucket}") String sourceBucket,
      @Value("${aws.destination.bucket}") String destBucket,
      @Value("${app.monthly-offset}") int monthlyOffset,
      @Value("${app.monthly-cleanup.enabled:false}") boolean monthlyCleanupEnabled,
      @Value("${app.monthly-cleanup.dry-run:false}") boolean monthlyCleanupDryRun) {
    LocalDate monthlyStart =
        LocalDate.now(ZoneOffset.UTC).minusMonths(monthlyOffset).withDayOfMonth(1);
    return new S3MigrationService(
        sourceS3Client,
        destinationS3Client,
        destinationTransferManager,
        sourceBucket,
        destBucket,
        changeLogService,
        bucketPrefixBuilder,
        s3KeyFilter,
        monthlyStart,
        monthlyCleanupEnabled,
        monthlyCleanupDryRun);
  }
}
