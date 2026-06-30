package de.bund.digitalservice.ris.migration.common.config;

import de.bund.digitalservice.ris.migration.common.service.BucketPrefixBuilder;
import de.bund.digitalservice.ris.migration.common.service.ChangeLogService;
import de.bund.digitalservice.ris.migration.common.service.S3MigrationService;
import java.time.LocalDate;
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
 * Auto-configures {@link S3MigrationService} for cloud runs using
 * project-provided S3 client beans and optional customisation beans. Projects
 * declare {@code sourceS3Client}, {@code
 * destinationS3Client}, and {@code destinationTransferManager} beans in their
 * own {@code S3Config}. Optional: {@code BucketPrefixBuilder} (default: empty
 * sub-path) and a {@code Predicate<String>} named {@code s3KeyFilter} (default:
 * accept all keys).
 */
@AutoConfiguration(after = MigrationAutoConfiguration.class)
@Profile("cloud")
public class S3AutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(name = "s3KeyFilter")
	public Predicate<String> s3KeyFilter() {
		return key -> true;
	}

	@Bean
	@ConditionalOnMissingBean
	public BucketPrefixBuilder bucketPrefixBuilder() {
		return new BucketPrefixBuilder("");
	}

	@Bean
	@ConditionalOnMissingBean
	public S3MigrationService s3MigrationService(@Qualifier("sourceS3Client") S3Client sourceS3Client,
			@Qualifier("destinationS3Client") S3Client destinationS3Client,
			S3TransferManager destinationTransferManager, ChangeLogService changeLogService,
			BucketPrefixBuilder bucketPrefixBuilder, @Qualifier("s3KeyFilter") Predicate<String> s3KeyFilter,
			@Value("${aws.bucket}") String sourceBucket, @Value("${aws.destination.bucket}") String destBucket,
			@Value("${app.monthly-start}") LocalDate monthlyStart) {
		return new S3MigrationService(sourceS3Client, destinationS3Client, destinationTransferManager, sourceBucket,
				destBucket, changeLogService, bucketPrefixBuilder, s3KeyFilter, monthlyStart);
	}
}
