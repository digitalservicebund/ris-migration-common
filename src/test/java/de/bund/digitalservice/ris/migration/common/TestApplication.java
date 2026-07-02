package de.bund.digitalservice.ris.migration.common;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/** Minimal bootstrap config so {@code @DataJpaTest} et al. can find a root configuration class. */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TestApplication {}
