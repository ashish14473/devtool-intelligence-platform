package com.devtools.intelligence.service;

import com.devtools.intelligence.model.JiraTicket;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads Jira tickets from the sample CSV file. This stands in for the
 * real JiraDocumentReader that would call the Jira REST API - same
 * output type (List<JiraTicket>), same place in the pipeline, just a
 * different data source. Swapping this for a real Jira client later
 * means changing only this class.
 */
@Service
@Slf4j
public class CsvIngestionService {

    private final ResourceLoader resourceLoader;
    private final String csvPath;

    public CsvIngestionService(ResourceLoader resourceLoader,
                                @Value("${ingestion.csv-path}") String csvPath) {
        this.resourceLoader = resourceLoader;
        this.csvPath = csvPath;
    }

    /**
     * Reads and parses every row of the CSV into a JiraTicket.
     * Expected column order (header row is skipped):
     * ticket_id,summary,description,comments,status,priority,labels,
     * created_date,resolved_date,tool_name
     */
    public List<JiraTicket> loadTickets() {
        List<JiraTicket> tickets = new ArrayList<>();
        Resource resource = resourceLoader.getResource(csvPath);

        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReader(reader)) {

            String[] header = csvReader.readNext(); // skip header row
            log.info("CSV header columns: {}", header == null ? "none" : header.length);

            String[] row;
            int rowNum = 1;
            while ((row = csvReader.readNext()) != null) {
                rowNum++;
                try {
                    tickets.add(parseRow(row));
                } catch (Exception e) {
                    // Don't let one malformed row kill the whole ingestion -
                    // log it and continue. In production this would also
                    // increment a metrics counter for "skipped rows".
                    log.warn("Skipping malformed CSV row {}: {}", rowNum, e.getMessage());
                }
            }

        } catch (IOException | CsvValidationException e) {
            throw new RuntimeException("Failed to read Jira CSV at " + csvPath, e);
        }

        log.info("Loaded {} tickets from CSV", tickets.size());
        return tickets;
    }

    private JiraTicket parseRow(String[] row) {
        if (row.length < 10) {
            throw new IllegalArgumentException("Expected 10 columns, got " + row.length);
        }

        return new JiraTicket(
                row[0].trim(),                          // ticketId
                row[1].trim(),                          // summary
                row[2].trim(),                          // description
                row[3].trim(),                          // comments
                row[4].trim(),                          // status
                row[5].trim(),                          // priority
                row[6].trim(),                          // labels
                parseDateOrNull(row[7]),                 // createdDate
                parseDateOrNull(row[8]),                 // resolvedDate
                row[9].trim()                            // toolName
        );
    }

    private LocalDate parseDateOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            log.warn("Could not parse date '{}', leaving null", value);
            return null;
        }
    }
}
