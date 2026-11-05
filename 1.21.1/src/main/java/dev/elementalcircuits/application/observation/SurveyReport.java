package dev.elementalcircuits.application.observation;

import java.util.*;

/** Detached, bounded instrument report. No live world, entity, inventory or graph references. */
public record SurveyReport(
        int version,
        String title,
        String location,
        List<Row> rows,
        String note,
        int checked,
        int total,
        boolean done) {
    public record Row(String name, String value) {
        public Row {
            if (name == null || value == null || name.length() > 64 || value.length() > 256)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.application.observation._survey_report.001"));
        }
    }

    public SurveyReport {
        if (version != 1
                || title == null
                || title.length() > 96
                || location == null
                || location.length() > 128
                || note == null
                || note.length() > 256
                || rows == null
                || rows.size() > 32
                || checked < 0
                || total < 0
                || checked > total)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.application.observation._survey_report.002"));
        rows = List.copyOf(rows);
    }
}
