package de.bund.digitalservice.ris.migration.common.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

  @Mock private S3MigrationService s3MigrationService;
  @Mock private ObjectProvider<S3MigrationService> s3ServiceProvider;

  private ImportService service;

  @BeforeEach
  void setUp() {
    service = new ImportService(s3ServiceProvider);
  }

  @Test
  void importData_localMode_skipsS3(@TempDir Path tempDir) throws Exception {
    when(s3ServiceProvider.getIfAvailable()).thenReturn(null);

    service.importData(
        new ExecutionContext(), "daily", tempDir.toString(), LocalDate.of(2025, Month.JANUARY, 15));

    verify(s3MigrationService, never())
        .downloadFolder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void importData_dailyCloudMode_downloadsFromS3() throws Exception {
    when(s3ServiceProvider.getIfAvailable()).thenReturn(s3MigrationService);
    when(s3MigrationService.resolveDailySourcePath(LocalDate.of(2025, Month.JANUARY, 15)))
        .thenReturn("daily/2025/01/14/");

    var context = new ExecutionContext();
    service.importData(context, "daily", "/tmp/input", LocalDate.of(2025, Month.JANUARY, 15));

    verify(s3MigrationService).assertFolderExists("daily/2025/01/14/");
    verify(s3MigrationService).downloadFolder("daily/2025/01/14/", "/tmp/input");
  }

  @Test
  void importData_monthlyCloudMode_downloadsFromS3() throws Exception {
    when(s3ServiceProvider.getIfAvailable()).thenReturn(s3MigrationService);
    var monthlySource =
        new S3MigrationService.MonthlyImportSource("monthly/2025/01/", LocalDate.of(2024, Month.DECEMBER, 26));
    when(s3MigrationService.resolveMonthlyPrefix(LocalDate.of(2025, Month.JANUARY, 15)))
        .thenReturn(monthlySource);

    var context = new ExecutionContext();
    service.importData(context, "monthly", "/tmp/input", LocalDate.of(2025, Month.JANUARY, 15));

    verify(s3MigrationService).downloadFolder("monthly/2025/01/", "/tmp/input");
  }

  @Test
  void importData_localMode_missingDirectory_doesNotThrow() throws Exception {
    when(s3ServiceProvider.getIfAvailable()).thenReturn(null);

    assertThatCode(
            () ->
                service.importData(
                    new ExecutionContext(),
                    "daily",
                    "/nonexistent/path",
                    LocalDate.of(2025, Month.JANUARY, 15)))
        .doesNotThrowAnyException();
  }
}
