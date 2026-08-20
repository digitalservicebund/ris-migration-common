package de.bund.digitalservice.ris.migration.common.model;

/**
 * An aggregated migration problem, grouped so a run can be summarised by how often each distinct
 * problem occurred rather than per affected document. Projects implement this on their own error
 * projection or query result.
 */
public interface CountedError {
  /**
   * How widespread the problem is.
   *
   * @return how many documents in the run hit this problem
   */
  Long getCount();

  /**
   * What went wrong, in operator-facing terms.
   *
   * @return the problem as reported to the operator reviewing the run
   */
  String getDescription();
}
