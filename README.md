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
    implementation("de.bund.digitalservice.ris:ris-migration-common:[VERSION]")
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
  monthly-offset: 3         # how many months back to allow recursive monthly search
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

### Base entities — `AbstractMigrationRecord` / `AbstractMigrationError`

`MigrationRecord` and `MigrationError` can't be fully owned by the library the way
`IncrementalMigrationStatus` is — each project's `MigrationRecord` needs its own `@OneToOne`/
`@OneToMany` relations to project-specific entities, and JPA can't target a `@ManyToOne` at an
abstract mapped-superclass polymorphically. Instead, extend the two `@MappedSuperclass` base
classes, which carry the common scalar fields:

```java
@Entity
public class MigrationRecord extends AbstractMigrationRecord {
    @OneToOne(mappedBy = "migrationRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private MyDocument document;

    @OneToMany(mappedBy = "migrationRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MigrationError> migrationErrors = new ArrayList<>();
}

@Entity
public class MigrationError extends AbstractMigrationError {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "migration_record_id")
    private MigrationRecord migrationRecord;
}
```

`AbstractMigrationRecord` provides `id` + `migrationStatus`. `AbstractMigrationError` provides
`id` + `type` (`MigrationErrorType.ERROR`/`WARNING`) + `description`. No Flyway changes needed
beyond your existing `migration_record`/`migration_error` tables — the mapped-superclass fields map
to the same column names your hand-written entities already used.

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
| `NoDataFoundStepListener` | a source-reading step; sets exit status `NO_DATA_FOUND` when it (and any named companion steps) read zero items |
| `MigrationStatusService` (call manually) | status-update tasklet after successful run |

Example — a project with two source-reading steps (e.g. XML and JSON) wants `NO_DATA_FOUND` only
when *neither* step read anything:

```java
@Bean
public Step processJsonFilesStep(/* ... */) {
    return new StepBuilder("processJsonFilesStep", jobRepository)
            .<Source, Output>chunk(500)
            // ...
            .listener(new NoDataFoundStepListener("processXmlFilesStep"))
            .build();
}
```

### Deletion steps — `DeletionCsvItemReaderFactory` + `S3DeletionWriter`

Reads a CSV of document numbers to delete (any column layout — only the document-number column is
used) and pairs directly with `S3DeletionWriter`:

```java
@Bean
public FlatFileItemReader<DocumentNumberReference> deletionReader() {
    return DeletionCsvItemReaderFactory.create(
            "deletionsReader",
            new FileSystemResource(inputDirectory + "/deletions.csv"),
            ",",
            List.of("documentUuid", "documentNumber", "deletionDate"),
            "documentNumber");
}

@Bean
public Step processDeletionsStep(
        FlatFileItemReader<DocumentNumberReference> deletionReader,
        S3DeletionWriter<MyRecord> s3DeletionWriter) {
    return new StepBuilder("processDeletionsStep", jobRepository)
            .<DocumentNumberReference, DocumentNumberReference>chunk(500)
            .reader(deletionReader)
            .writer(s3DeletionWriter)
            .build();
}
```

The reader tolerates a missing file (non-strict) since a deletion CSV may not exist for every run.

If your deletion CSV carries extra columns beyond the document number (e.g. a document type or
deletion date) that downstream code needs, use the `FieldSetMapper` overload instead and implement
`DocumentNumberReference` on your own row record — `S3DeletionWriter` only ever calls
`documentNumber()`, so it works unchanged with any implementation:

```java
public record MyDeleteEntry(String documentNumber, String documentType, String deletedAt)
        implements DocumentNumberReference {}

@Bean
public FlatFileItemReader<MyDeleteEntry> deletionReader() {
    return DeletionCsvItemReaderFactory.create(
            "deletionsReader",
            new FileSystemResource(inputDirectory + "/deletions.csv"),
            ",",
            List.of("documentNumber", "documentType", "deletedAt"),
            fieldSet -> new MyDeleteEntry(
                    fieldSet.readString("documentNumber"),
                    fieldSet.readString("documentType"),
                    fieldSet.readString("deletedAt")));
}
```

### Publish step — `PublishTasklet`

Uploads the output directory to the destination bucket and sets the step's exit status to
`MONTHLY_MIGRATION_COMPLETED` or `DAILY_MIGRATION_COMPLETED` so the job flow can branch on it (e.g.
skip deletion handling for monthly runs). Pass `null` for `S3MigrationService` in local mode — the
tasklet no-ops the upload but still sets the exit status.

```java
@Bean
public Step publishStep(
        ObjectProvider<S3MigrationService> s3ServiceProvider, MigrationJobProperties props) {
    return new StepBuilder("publishStep", jobRepository)
            .tasklet(
                    new PublishTasklet(
                            s3ServiceProvider.getIfAvailable(),
                            props.outputDirectory(),
                            props.migrationType()),
                    transactionManager)
            .build();
}
```

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
