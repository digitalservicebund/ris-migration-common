package de.bund.digitalservice.ris.migration.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
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
    service =
        new S3MigrationService(
            sourceClient,
            destClient,
            transferManager,
            "source-bucket",
            "dest-bucket",
            changeLogService,
            bucketPrefixBuilder,
            Predicate.not(String::isEmpty),
            LocalDate.of(2020, Month.JANUARY, 1));
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

    service.writeChangeLog("daily");

    verify(changeLogService).buildChangeLog();
    verify(destClient).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void writeChangeLog_monthlyType_callsBuildChangeLogAll() {
    when(changeLogService.buildChangeLogAll()).thenReturn("{\"change_all\":true}");

    service.writeChangeLog("monthly");

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
    var iterable = org.mockito.Mockito.mock(ListObjectsV2Iterable.class);
    when(sourceClient.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(iterable);
    when(iterable.iterator()).thenReturn(List.<ListObjectsV2Response>of().iterator());

    service.downloadFolder("daily/prefix/", localDest.toString());

    verify(sourceClient, never()).getObject(any(GetObjectRequest.class), any(Path.class));
  }

  @Test
  void downloadFolder_withObjects_downloadsMatchingFiles(@TempDir Path localDest) {
    var s3Object = S3Object.builder().key("daily/prefix/file.xml").build();
    var page = ListObjectsV2Response.builder().contents(List.of(s3Object)).build();
    var iterable = org.mockito.Mockito.mock(ListObjectsV2Iterable.class);
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
    var iterable = org.mockito.Mockito.mock(ListObjectsV2Iterable.class);
    when(sourceClient.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(iterable);
    when(iterable.iterator()).thenReturn(List.of(page).iterator());

    service.downloadFolder("daily/prefix/", localDest.toString());

    verify(sourceClient, never())
        .getObject(any(GetObjectRequest.class), any(Path.class));
  }

  @Test
  void uploadFolder_directoryNotExists_skipsUpload(@TempDir Path base) throws IOException {
    Path nonExistent = base.resolve("nonexistent");

    service.uploadFolder(nonExistent.toString(), "daily");

    verify(transferManager, never())
        .uploadDirectory(
            any(software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest.class));
  }

  @Test
  void uploadFolder_monthlyMode_uploadsWithoutChangelog(@TempDir Path localDir) throws IOException {
    Files.writeString(localDir.resolve("doc.xml"), "<root/>");
    var completed = org.mockito.Mockito.mock(CompletedDirectoryUpload.class);
    var upload = org.mockito.Mockito.mock(DirectoryUpload.class);
    when(transferManager.uploadDirectory(
            any(software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest.class)))
        .thenReturn(upload);
    when(upload.completionFuture())
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(completed));
    when(completed.failedTransfers()).thenReturn(List.of());

    service.uploadFolder(localDir.toString(), "monthly");

    verify(changeLogService, never()).addChanged(any());
  }

  @Test
  void uploadFolder_dailyMode_addsChangedFiles(@TempDir Path localDir) throws IOException {
    Files.writeString(localDir.resolve("doc.xml"), "<root/>");
    var completed = org.mockito.Mockito.mock(CompletedDirectoryUpload.class);
    var upload = org.mockito.Mockito.mock(DirectoryUpload.class);
    when(transferManager.uploadDirectory(
            any(software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest.class)))
        .thenReturn(upload);
    when(upload.completionFuture())
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(completed));
    when(completed.failedTransfers()).thenReturn(List.of());

    service.uploadFolder(localDir.toString(), "daily");

    verify(changeLogService).addChanged(any());
  }
}
