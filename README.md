# ris-migration-common — Usage Manual

## 1. Add the dependency

```kotlin
// build.gradle.kts
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/digitalservicebund/ris-migration-common")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("de.bund.digitalservice.ris:ris-migration-common:1.0.0")
}
```

## 2. Required application properties

```yaml
app:
  input:
    directory: /data/input
  output:
    directory: /data/output
  migration-type: daily     # "daily" or "monthly"
  monthly-start: 2022-01-01 # earliest monthly import; prevents infinite recursion
```

## 3. What is auto-configured

The library registers two auto-configurations via `AutoConfiguration.imports`:

| Class | Active when | Beans registered |
|---|---|---|
| `MigrationAutoConfiguration` | always | `ChangeLogService`, `ImportService` |
| `S3AutoConfiguration` | `@Profile("cloud")` | `S3MigrationService`, `BucketPrefixBuilder` (default), `s3KeyFilter` (default) |

`MigrationAutoConfiguration` also calls `@AutoConfigurationPackage(basePackages = "de.bund.digitalservice.ris.migration.common")` so JPA picks up the `IncrementalMigrationStatus` entity automatically — no `@EntityScan` needed in the consuming app.

## 4. Project-specific wiring (cloud profile)

`S3AutoConfiguration` expects three beans by qualifier — declare them in your project's `S3Config`:

```java
@Configuration
@Profile("cloud")
public class S3Config {

    @Bean("sourceS3Client")
    public S3Client sourceS3Client(@Value("${aws.bucket.access-key}") String key, ...) { ... }

    @Bean("destinationS3Client")
    public S3Client destinationS3Client(...) { ... }

    @Bean
    public S3TransferManager destinationTransferManager(
            @Qualifier("destinationS3Client") S3Client client) { ... }
}
```

Required properties for `S3AutoConfiguration`:

```yaml
aws:
  bucket: source-bucket-name
  destination:
    bucket: destination-bucket-name
```

### Optional overrides

| Bean | Purpose | Default |
|---|---|---|
| `BucketPrefixBuilder` | Adds a sub-path inside S3 prefixes | `""` (no sub-path) |
| `Predicate<String> s3KeyFilter` (name `s3KeyFilter`) | Filters which S3 keys are downloaded | accept all |

Example — project needs a `"BZSt/"` sub-folder and only downloads `.xml` keys:

```java
@Bean
public BucketPrefixBuilder bucketPrefixBuilder() {
    return new BucketPrefixBuilder("BZSt/");
}

@Bean("s3KeyFilter")
public Predicate<String> s3KeyFilter() {
    return key -> key.endsWith(".xml");
}
```

## 5. Implement the orchestrator

Extend `DailyMigrationOrchestrator` and annotate with `@Component`. Inject the concrete `Job` bean your project defines.

```java
@Component
public class MigrationOrchestrator extends DailyMigrationOrchestrator {

    public MigrationOrchestrator(
            Job migrationJob,
            JobOperator jobOperator,
            ObjectProvider<IncrementalMigrationStatusRepository> statusRepoProvider,
            MigrationJobProperties properties) {
        super(migrationJob, jobOperator, statusRepoProvider, properties);
    }

    @Override
    protected void preRunSetup(LocalDate date) {
        // optional: clear caches, set thread-locals, etc.
    }
}
```

In **daily** mode the orchestrator iterates every date from `lastSuccessfulRun + 1` to today, clearing input/output directories between runs. In **monthly** mode it runs the job once.

If `IncrementalMigrationStatusRepository` is not yet in the project context (no bean), the orchestrator defaults `lastRun = today − 1` and processes only today.

## 6. Implement repositories

Declare JPA repositories that extend both the library interface and `JpaRepository`:

```java
// Required when MigrationStatusService is in the job
@Repository
public interface IncrementalMigrationStatusJpaRepository
        extends IncrementalMigrationStatusRepository,
                JpaRepository<IncrementalMigrationStatus, UUID> {}

// Required when PrintMigrationErrorsListener is in the job
@Repository
public interface MigrationErrorJpaRepository
        extends MigrationErrorRepository,
                JpaRepository<MigrationError, UUID> {

    @Query("SELECT e FROM MigrationError e GROUP BY e.description")
    List<CountedError> countAllGroupByDescription();
}

@Repository
public interface MigrationRecordJpaRepository
        extends MigrationRecordRepository,
                JpaRepository<MigrationRecord, UUID> {

    long countAllByMigrationStatusNot(MigrationStatus status);
}
```

The `IncrementalMigrationStatus` entity is owned by the library — no `@Entity` declaration needed in the project.

## 7. Use library components in a job

```java
@Configuration
@EnableBatchProcessing
public class MigrationJobConfig {

    @Bean
    public Job migrationJob(
            Step importStep,
            Step transformStep,
            Step exportStep,
            Step deleteStep) {
        return new JobBuilder("migrationJob", jobRepository)
                .start(importStep)
                .next(transformStep)
                .next(exportStep)
                .next(deleteStep)
                .build();
    }

    @Bean
    public Step importStep(ImportService importService, MigrationJobProperties props) {
        return new StepBuilder("importStep", jobRepository)
                .tasklet((contribution, ctx) -> {
                    importService.importData(
                            ctx.getStepContext().getStepExecution().getJobExecution().getExecutionContext(),
                            props.migrationType(),
                            props.inputDirectory(),
                            LocalDate.parse(ctx.getStepContext().getJobParameters().get("processingDate").toString()));
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .listener(new PrintTimeUsedStepListener())
                .build();
    }

    // FileItemReader — XML files
    @Bean
    @StepScope
    public FileItemReader fileItemReader(MigrationJobProperties props) {
        return new FileItemReader(props.inputDirectory(), new XmlDocumentItemReader(), ".xml");
    }

    // FileItemWriter — write transformed output
    @Bean
    public FileItemWriter<MyOutputItem> fileItemWriter(MigrationJobProperties props) {
        return new FileItemWriter<>(props.outputDirectory(), ".akn.xml");
    }

    // S3DeletionWriter — delete by document number
    @Bean
    public S3DeletionWriter<MyRecord> s3DeletionWriter(
            S3MigrationService s3MigrationService,
            MyRecordRepository repo) {
        return new S3DeletionWriter<>(
                s3MigrationService,
                repo::findByDocumentNumber,
                repo::delete,
                ".akn.xml");
    }
}
```

### Listeners available

| Class | Attach to |
|---|---|
| `PrintTimeUsedStepListener` | any step |
| `PrintProcessedItemsChunkListener` | any chunk-oriented step |
| `PrintMigrationErrorsListener` | the final step; needs `MigrationErrorRepository` + `MigrationRecordRepository` |
| `MigrationStatusService` (call manually) | status-update tasklet after successful run |

## 8. Output items

Implement `MigrationOutputItem` on your processor's output type so `FileItemWriter` can write it:

```java
public record TransformedDocument(String documentNumber, String xmlContent)
        implements MigrationOutputItem {

    @Override public String getDocumentNumber() { return documentNumber; }
    @Override public String getXmlContent()      { return xmlContent; }
}
```

## 9. Flyway migration for IncrementalMigrationStatus

Add a migration file:

```sql
-- V<N>__create_incremental_migration_status.sql
CREATE TABLE incremental_migration_status (
    id                           UUID      NOT NULL PRIMARY KEY,
    created_at                   TIMESTAMP NOT NULL,
    last_daily_import_version    DATE,
    last_historic_import_version DATE
);
```
