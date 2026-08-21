package de.bund.digitalservice.ris.migration.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.bund.digitalservice.ris.migration.common.config.MigrationType;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.DirectoryUpload;

@ExtendWith(MockitoExtension.class)
class S3MigrationServiceTest {

  @Mock private S3Client sourceClient;
  @Mock private S3Client destClient;
  @Mock private S3TransferManager transferManager;
  @Mock private ChangeLogService changeLogService;
  @Mock private BucketPrefixBuilder bucketPrefixBuilder;

  private S3MigrationService service;

  @BeforeEach
  void setUp() {
    service = serviceWithCleanup(false, false);
  }

  private S3MigrationService serviceWithCleanup(boolean cleanupEnabled, boolean dryRun) {
    return new S3MigrationService(
        sourceClient,
        destClient,
        transferManager,
        "source-bucket",
        "dest-bucket",
        changeLogService,
        bucketPrefixBuilder,
        Predicate.not(String::isEmpty),
        LocalDate.of(2020, Month.JANUARY, 1),
        cleanupEnabled,
        dryRun);
  }

  @Test
  void resolveDailySourcePath_returnsPrefixForPreviousDay() {
    when(bucketPrefixBuilder.buildDailyPrefix(LocalDate.of(2025, Month.JANUARY, 14)))
        .thenReturn("daily/2025/01/14/");

    String result = service.resolveDailySourcePath(LocalDate.of(2025, Month.JANUARY, 15));

    assertThat(result).isEqualTo("daily/2025/01/14/");
  }

  @Test
  void assertFolderExists_folderExists_doesNotThrow() {
    var s3Object = S3Object.builder().key("daily/prefix/file.xml").build();
    var response = ListObjectsV2Response.builder().contents(List.of(s3Object)).build();
    when(sourceClient.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

    assertThatCode(() -> service.assertFolderExists("daily/prefix/")).doesNotThrowAnyException();
  }

  @Test
  void assertFolderExists_folderEmpty_throwsFileNotFoundException() {
    var response = ListObjectsV2Response.builder().contents(List.of()).build();
    when(sourceClient.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

    assertThatThrownBy(() -> service.assertFolderExists("daily/missing/"))
        .isInstanceOf(FileNotFoundException.class)
        .hasMessageContaining("daily/missing/");
  }

  @Test
  void delete_callsDestClientAndChangeLog() {
    service.delete("doc001.xml");

    verify(destClient).deleteObject(any(DeleteObjectRequest.class));
    verify(changeLogService).addDeleted("doc001.xml");
  }

  @Test
  void writeChangeLog_dailyType_callsBuildChangeLog() {
    when(changeLogService.buildChangeLog()).thenReturn("{\"changed\":[],\"deleted\":[]}");

    service.writeChangeLog(MigrationType.DAILY);

    verify(changeLogService).buildChangeLog();
    verify(destClient).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void writeChangeLog_monthlyType_callsBuildChangeLogAll() {
    when(changeLogService.buildChangeLogAll()).thenReturn("{\"change_all\":true}");

    service.writeChangeLog(MigrationType.MONTHLY);

    verify(changeLogService).buildChangeLogAll();
    verify(destClient).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void saveChangelog_writesToDestBucket() {
    service.saveChangelog("changelogs/test.json", "{\"changed\":[]}");

    verify(destClient).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void downloadFolder_emptyResponse_downloadsNothing(@TempDir Path localDest) {
    var iterable = mock(ListObjectsV2Iterable.class);
    when(sourceClient.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(iterable);
    when(iterable.iterator()).thenReturn(Collections.emptyIterator());

    service.downloadFolder("daily/prefix/", localDest.toString());

    verify(sourceClient, never()).getObject(any(GetObjectRequest.class), any(Path.class));
  }

  @Test
  void downloadFolder_withObjects_downloadsMatchingFiles(@TempDir Path localDest) {
    var s3Object = S3Object.builder().key("daily/prefix/file.xml").build();
    var page = ListObjectsV2Response.builder().contents(List.of(s3Object)).build();
    var iterable = mock(ListObjectsV2Iterable.class);
    when(sourceClient.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(iterable);
    when(iterable.iterator()).thenReturn(List.of(page).iterator());
    when(sourceClient.getObject(any(GetObjectRequest.class), any(Path.class))).thenReturn(null);

    service.downloadFolder("daily/prefix/", localDest.toString());

    verify(sourceClient).getObject(any(GetObjectRequest.class), any(Path.class));
  }

  @Test
  void downloadFolder_keyFilterExcludesEmpty_skipsEmptyKey(@TempDir Path localDest) {
    var s3Object = S3Object.builder().key("").build();
    var page = ListObjectsV2Response.builder().contents(List.of(s3Object)).build();
    var iterable = mock(ListObjectsV2Iterable.class);
    when(sourceClient.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(iterable);
    when(iterable.iterator()).thenReturn(List.of(page).iterator());

    service.downloadFolder("daily/prefix/", localDest.toString());

    verify(sourceClient, never()).getObject(any(GetObjectRequest.class), any(Path.class));
  }

  @Test
  void uploadFolder_directoryNotExists_skipsUpload(@TempDir Path base) throws IOException {
    Path nonExistent = base.resolve("nonexistent");

    Set<String> result = service.uploadFolder(nonExistent.toString(), MigrationType.DAILY);

    assertThat(result).isEmpty();
    verify(transferManager, never())
        .uploadDirectory(
            any(software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest.class));
  }

  @Test
  void uploadFolder_monthlyMode_uploadsWithoutChangelog(@TempDir Path localDir) throws IOException {
    Files.writeString(localDir.resolve("doc.xml"), "<root/>");
    var completed = mock(CompletedDirectoryUpload.class);
    var upload = mock(DirectoryUpload.class);
    when(transferManager.uploadDirectory(
            any(software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest.class)))
        .thenReturn(upload);
    when(upload.completionFuture())
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(completed));
    when(completed.failedTransfers()).thenReturn(List.of());

    Set<String> result = service.uploadFolder(localDir.toString(), MigrationType.MONTHLY);

    assertThat(result).containsExactly("doc.xml");
    verify(changeLogService, never()).addChanged(any());
  }

  @Test
  void uploadFolder_hasFailedTransfers_throwsIOException(@TempDir Path localDir)
      throws IOException {
    Files.writeString(localDir.resolve("doc.xml"), "<root/>");
    var completed = mock(CompletedDirectoryUpload.class);
    var upload = mock(DirectoryUpload.class);
    var failedTransfer =
        mock(software.amazon.awssdk.transfer.s3.model.FailedFileUpload.class, RETURNS_DEEP_STUBS);
    when(transferManager.uploadDirectory(
            any(software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest.class)))
        .thenReturn(upload);
    when(upload.completionFuture())
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(completed));
    when(completed.failedTransfers()).thenReturn(List.of(failedTransfer));
    when(failedTransfer.exception().getMessage()).thenReturn("boom");

    assertThatThrownBy(() -> service.uploadFolder(localDir.toString(), MigrationType.DAILY))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Failed to upload local directory");
  }

  @Test
  void deleteObjects_s3ExceptionThrown_wrapsInUncheckedIOException() {
    when(destClient.deleteObjects(any(DeleteObjectsRequest.class)))
        .thenThrow(S3Exception.builder().message("Access Denied").build());

    assertThatThrownBy(() -> service.deleteObjects(List.of("doc001.xml")))
        .isInstanceOf(java.io.UncheckedIOException.class);
  }

  @Test
  void deleteObjects_emptyCollection_doesNotCallS3() {
    service.deleteObjects(List.of());

    verify(destClient, never()).deleteObjects(any(DeleteObjectsRequest.class));
  }

  @Test
  void deleteObjects_singleChunk_deletesAndRecordsChangeLog() {
    var response =
        DeleteObjectsResponse.builder()
            .deleted(
                DeletedObject.builder().key("doc001.xml").build(),
                DeletedObject.builder().key("doc002.xml").build())
            .build();
    when(destClient.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(response);

    service.deleteObjects(List.of("doc001.xml", "doc002.xml"));

    verify(destClient, times(1)).deleteObjects(any(DeleteObjectsRequest.class));
    verify(changeLogService).addDeleted("doc001.xml");
    verify(changeLogService).addDeleted("doc002.xml");
  }

  @Test
  void deleteObjects_moreThanChunkSize_splitsIntoMultipleRequests() {
    List<String> keys = java.util.stream.IntStream.range(0, 1500).mapToObj(i -> "doc" + i).toList();
    when(destClient.deleteObjects(any(DeleteObjectsRequest.class)))
        .thenReturn(DeleteObjectsResponse.builder().build());

    service.deleteObjects(keys);

    verify(destClient, times(2)).deleteObjects(any(DeleteObjectsRequest.class));
  }

  @Test
  void deleteObjects_responseHasErrors_logsAndDoesNotThrow() {
    var response =
        DeleteObjectsResponse.builder()
            .errors(S3Error.builder().key("doc001.xml").message("Access Denied").build())
            .build();
    when(destClient.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(response);

    assertThatCode(() -> service.deleteObjects(List.of("doc001.xml"))).doesNotThrowAnyException();

    verify(changeLogService, never()).addDeleted(any());
  }

  @Test
  void reconcileDestination_cleanupDisabled_doesNotListOrDelete() {
    service.reconcileDestination(Set.of("doc.xml"));

    verify(destClient, never()).listObjectsV2Paginator(any(ListObjectsV2Request.class));
    verify(destClient, never()).deleteObjects(any(DeleteObjectsRequest.class));
  }

  @Test
  void reconcileDestination_noStaleObjects_doesNotDelete() {
    S3MigrationService enabled = serviceWithCleanup(true, false);
    var current = S3Object.builder().key("doc.xml").build();
    var page = ListObjectsV2Response.builder().contents(List.of(current)).build();
    var iterable = mock(ListObjectsV2Iterable.class);
    when(destClient.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(iterable);
    when(iterable.iterator()).thenReturn(List.of(page).iterator());

    enabled.reconcileDestination(Set.of("doc.xml"));

    verify(destClient, never()).deleteObjects(any(DeleteObjectsRequest.class));
  }

  @Test
  void reconcileDestination_staleObjectsExcludingChangelogs_deletesOnlyStaleDocuments() {
    S3MigrationService enabled = serviceWithCleanup(true, false);
    var current = S3Object.builder().key("doc.xml").build();
    var stale = S3Object.builder().key("old-doc.xml").build();
    var changelog = S3Object.builder().key("changelogs/2025-01-01T00:00:00Z.json").build();
    var page = ListObjectsV2Response.builder().contents(List.of(current, stale, changelog)).build();
    var iterable = mock(ListObjectsV2Iterable.class);
    when(destClient.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(iterable);
    when(iterable.iterator()).thenReturn(List.of(page).iterator());
    when(destClient.deleteObjects(any(DeleteObjectsRequest.class)))
        .thenReturn(
            DeleteObjectsResponse.builder()
                .deleted(DeletedObject.builder().key("old-doc.xml").build())
                .build());

    enabled.reconcileDestination(Set.of("doc.xml"));

    ArgumentCaptor<DeleteObjectsRequest> captor =
        ArgumentCaptor.forClass(DeleteObjectsRequest.class);
    verify(destClient).deleteObjects(captor.capture());
    assertThat(captor.getValue().delete().objects())
        .extracting(ObjectIdentifier::key)
        .containsExactly("old-doc.xml");
    verify(changeLogService).addDeleted("old-doc.xml");
  }

  @Test
  void reconcileDestination_dryRun_listsButDoesNotDelete() {
    S3MigrationService dryRun = serviceWithCleanup(true, true);
    var stale = S3Object.builder().key("old-doc.xml").build();
    var page = ListObjectsV2Response.builder().contents(List.of(stale)).build();
    var iterable = mock(ListObjectsV2Iterable.class);
    when(destClient.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(iterable);
    when(iterable.iterator()).thenReturn(List.of(page).iterator());

    dryRun.reconcileDestination(Set.of("doc.xml"));

    verify(destClient, never()).deleteObjects(any(DeleteObjectsRequest.class));
  }

  @Test
  void reconcileDestination_emptyUploadSet_throwsAndDoesNotTouchBucket() {
    S3MigrationService enabled = serviceWithCleanup(true, false);

    assertThatThrownBy(() -> enabled.reconcileDestination(Set.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("empty upload set");

    verify(destClient, never()).listObjectsV2Paginator(any(ListObjectsV2Request.class));
    verify(destClient, never()).deleteObjects(any(DeleteObjectsRequest.class));
  }

  @Test
  void reconcileDestination_emptyUploadSetInDryRun_stillThrows() {
    S3MigrationService dryRun = serviceWithCleanup(true, true);

    assertThatThrownBy(() -> dryRun.reconcileDestination(Set.of()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void reconcileDestination_emptyUploadSetButCleanupDisabled_doesNotThrow() {
    assertThatCode(() -> service.reconcileDestination(Set.of())).doesNotThrowAnyException();

    verify(destClient, never()).listObjectsV2Paginator(any(ListObjectsV2Request.class));
  }

  @Test
  void uploadFolder_dailyMode_addsChangedFiles(@TempDir Path localDir) throws IOException {
    Files.writeString(localDir.resolve("doc.xml"), "<root/>");
    var completed = mock(CompletedDirectoryUpload.class);
    var upload = mock(DirectoryUpload.class);
    when(transferManager.uploadDirectory(
            any(software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest.class)))
        .thenReturn(upload);
    when(upload.completionFuture())
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(completed));
    when(completed.failedTransfers()).thenReturn(List.of());

    service.uploadFolder(localDir.toString(), MigrationType.DAILY);

    verify(changeLogService).addChanged(any());
  }
}
