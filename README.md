# ris-migration-common

## A. What it is

`ris-migration-common` is a Spring Boot library that provides the shared building blocks for
**juris data migration batch jobs**: Spring Batch readers, writers, and listeners for reading XML/JSON
source files and CSV deletion feeds, uploading/downloading data to and from S3, tracking a daily/monthly
import checkpoint, and orchestrating repeated daily runs.

It is used by every project that migrates juris source data into a target database (e.g. `ris-adm-bzst`,
`ris-literature`). Each project defines its own domain model, its own database schema, and its own batch
job wiring; the library supplies the repeated infrastructure pieces so they aren't reimplemented per
project.

### Ownership boundary

The library owns **no JPA entities and no repository interfaces**. Every piece of persistence — the
migration-status checkpoint, the migration record, migration errors, and how they're queried — is owned
entirely by the consuming project. The library's services, listeners, and writers instead take
project-supplied functional callbacks (`Function`, `Consumer`, `Supplier`) or small interfaces
(`MigrationStatusUpdater`, `MigrationOutputItem`, `DocumentNumberReference`) so they can operate on
whatever entity shape a project has, without the library dictating a table schema. This keeps each
project's database schema fully independent of the library's release version.

### What it provides

| Area | Classes |
|---|---|
| Reading source files | `FileItemReader`, `XmlDocumentItemReader`, `DeletionCsvItemReaderFactory` |
| Writing output | `FileItemWriter<T extends MigrationOutputItem>`, `S3DeletionWriter<T>` |
| S3 transfer (cloud profile) | `S3MigrationService`, `BucketPrefixBuilder`, `ChangeLogService` |
| Migration checkpoint | `MigrationStatusService`, `MigrationStatusUpdater` (you implement this) |
| Orchestration | `DailyMigrationOrchestrator` |
| Step/chunk listeners | `PrintTimeUsedStepListener`, `PrintProcessedItemsChunkListener`, `PrintMigrationErrorsListener`, `NoDataFoundStepListener` |
| Publishing | `PublishTasklet` |
| Configuration | `MigrationJobProperties` (`app.*` properties) |
| Shared model types | `MigrationStatus`, `MigrationErrorType`, `CountedError`, `JurisDocument`, `DocumentNumberReference` |

## B. How to integrate

### 1. Add the dependency

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

### 2. Configure application properties

```yaml
app:
  input:
    directory: /data/input
  output:
    directory: /data/output
  migration-type: daily     # "daily" or "monthly"
  monthly-offset: 3         # how many months back to allow recursive monthly search
```

These are bound to `MigrationJobProperties` (`input.directory()`, `output.directory()`,
`migrationType()`, `monthlyOffset()`), available to inject anywhere in your job configuration.

### 3. What's auto-configured

The library registers two auto-configurations:

| Class | Active when | Beans registered |
|---|---|---|
| `MigrationAutoConfiguration` | always | `ChangeLogService`, `ImportService`, and `MigrationStatusService` (only once you expose a `MigrationStatusUpdater` bean — see step 5) |
| `S3AutoConfiguration` | `@Profile("cloud")` | `S3MigrationService`, plus default `BucketPrefixBuilder`/`s3KeyFilter` beans |

No `@EntityScan` or `@EnableJpaRepositories` is required for anything in this library — it has no
entities of its own.

### 4. Wire up S3 (cloud profile only)

`S3AutoConfiguration` expects three qualified beans from your project:

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

Required properties:

```yaml
aws:
  bucket: source-bucket-name
  destination:
    bucket: destination-bucket-name
```

Optional overrides:

| Bean | Purpose | Default |
|---|---|---|
| `BucketPrefixBuilder` | Adds a sub-path inside S3 prefixes | `""` (no sub-path) |
| `Predicate<String> s3KeyFilter` (name `s3KeyFilter`) | Filters which S3 keys are downloaded | accept all |

Example — a project needs a `"BZSt/"` sub-folder and only downloads `.xml` keys:

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

If your project runs without S3 (no `cloud` profile active), `ImportService`, `PublishTasklet`, and
`S3DeletionWriter` all degrade to no-ops for their S3-facing side and log that they're running in local
mode — the rest of the job still runs against whatever files already sit in the input directory.

### 5. Set up your own persistence

Declare all entities and repositories in your own project. The library only needs a small
interface implementation and a couple of functional callbacks to plug into them.

#### Migration checkpoint (`IncrementalMigrationStatus`)

Any shape works, as long as you can produce a `Supplier<Optional<LocalDate>>` for the orchestrator
(step 6) and an implementation of `MigrationStatusUpdater` for `MigrationStatusService`:

```java
@Entity
@Table(name = "incremental_migration_status")
@Builder(toBuilder = true)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IncrementalMigrationStatus {

    @Id
    @GeneratedValue
    private UUID id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDate lastDailyImportVersion;

    private LocalDate lastHistoricImportVersion;
}

public interface IncrementalMigrationStatusRepository
        extends JpaRepository<IncrementalMigrationStatus, UUID> {
    Optional<IncrementalMigrationStatus> findFirstByOrderByCreatedAtDesc();
}
```

Add the accompanying Flyway migration:

```sql
CREATE TABLE incremental_migration_status (
    id                           UUID      NOT NULL PRIMARY KEY,
    created_at                   TIMESTAMP NOT NULL,
    last_daily_import_version    DATE      NOT NULL,
    last_historic_import_version DATE
);
```

Implement `MigrationStatusUpdater` and expose it as a `@Component` so `MigrationStatusService`
auto-activates:

```java
@Component
@RequiredArgsConstructor
public class ProjectMigrationStatusUpdater implements MigrationStatusUpdater {

    private final IncrementalMigrationStatusRepository statusRepository;

    @Override
    public void updateDaily(LocalDate date) {
        save(builder().lastDailyImportVersion(date));
    }

    @Override
    public void updateHistoricAndDaily(LocalDate date) {
        save(builder().lastHistoricImportVersion(date).lastDailyImportVersion(date));
    }

    private IncrementalMigrationStatus.IncrementalMigrationStatusBuilder builder() {
        return statusRepository.findFirstByOrderByCreatedAtDesc()
                .orElseGet(IncrementalMigrationStatus::new)
                .toBuilder()
                .id(null)
                .createdAt(null);
    }

    private void save(IncrementalMigrationStatus.IncrementalMigrationStatusBuilder builder) {
        statusRepository.save(builder.build());
    }
}
```

#### Migration record and error entities

Declare the scalar fields your project needs directly on your own entities:

```java
@Entity
public class MigrationRecord {
    @Id @GeneratedValue private UUID id;

    @Enumerated(EnumType.STRING)
    @Basic(optional = false)
    private MigrationStatus migrationStatus;

    @OneToOne(mappedBy = "migrationRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private MyDocument document;

    @OneToMany(mappedBy = "migrationRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MigrationError> migrationErrors = new ArrayList<>();
}

@Entity
public class MigrationError {
    @Id @GeneratedValue private UUID id;

    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    private MigrationErrorType type;

    @Basic(optional = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "migration_record_id")
    private MigrationRecord migrationRecord;
}
```

`migrationStatus` uses the library's `MigrationStatus` enum; `type` uses `MigrationErrorType`
(`ERROR`/`WARNING`). Repositories are plain Spring Data interfaces:

```java
public interface MigrationErrorJpaRepository extends JpaRepository<MigrationError, UUID> {
    @Query("SELECT e FROM MigrationError e GROUP BY e.description")
    List<CountedError> countAllGroupByDescription();
}

public interface MigrationRecordJpaRepository extends JpaRepository<MigrationRecord, UUID> {
    long countAllByMigrationStatusNot(MigrationStatus status);
}
```

### 6. Wire the orchestrator

Extend `DailyMigrationOrchestrator` and annotate with `@Component`. Inject the concrete `Job` bean
your project defines, plus a `Supplier<Optional<LocalDate>>` that looks up the last successfully
processed daily version from your status repository (step 5):

```java
@Component
public class MigrationOrchestrator extends DailyMigrationOrchestrator {

    public MigrationOrchestrator(
            Job migrationJob,
            JobOperator jobOperator,
            IncrementalMigrationStatusRepository statusRepository,
            MigrationJobProperties properties) {
        super(
            migrationJob,
            jobOperator,
            () -> statusRepository.findFirstByOrderByCreatedAtDesc()
                    .map(IncrementalMigrationStatus::getLastDailyImportVersion),
            properties);
    }

    @Override
    protected void preRunSetup(LocalDate date) {
        // optional: clear caches, set thread-locals, etc.
    }
}
```

In **daily** mode the orchestrator iterates every date from `lastSuccessfulRun + 1` to today, clearing
input/output directories between runs. In **monthly** mode it runs the job once. If the supplied
`Supplier<Optional<LocalDate>>` returns `Optional.empty()` (e.g. no status row yet), the orchestrator
defaults `lastRun = today − 1` and processes only today.

### 7. Build your batch job

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

#### Listeners available

| Class | Attach to |
|---|---|
| `PrintTimeUsedStepListener` | any step |
| `PrintProcessedItemsChunkListener` | any chunk-oriented step |
| `PrintMigrationErrorsListener` | the final step; needs a `Supplier<List<CountedError>>` and a `LongSupplier` for your error/record counts |
| `NoDataFoundStepListener` | a source-reading step; sets exit status `NO_DATA_FOUND` when it (and any named companion steps) read zero items |
| `MigrationStatusService` (call manually) | status-update tasklet after a successful run |

`PrintMigrationErrorsListener` takes a `Supplier<List<CountedError>>` (your error-count query) and a
`LongSupplier` (your failed-documents count query):

```java
@Bean
public PrintMigrationErrorsListener printMigrationErrorsListener(
        MigrationErrorJpaRepository migrationErrorRepository,
        MigrationRecordJpaRepository migrationRecordRepository) {
    return new PrintMigrationErrorsListener(
            migrationErrorRepository::countAllGroupByDescription,
            () -> migrationRecordRepository.countAllByMigrationStatusNot(
                    MigrationStatus.TRANSFORMATION_SUCCEEDED));
}
```

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

#### Deletion steps — `DeletionCsvItemReaderFactory` + `S3DeletionWriter`

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

#### Publish step — `PublishTasklet`

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

### 8. Output items

Implement `MigrationOutputItem` on your processor's output type so `FileItemWriter` can write it:

```java
public record TransformedDocument(String documentNumber, String xmlContent)
        implements MigrationOutputItem {

    @Override public String getDocumentNumber() { return documentNumber; }
    @Override public String getXmlContent()      { return xmlContent; }
}
```
