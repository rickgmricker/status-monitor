package com.gmricker.monitor.ingame;

import java.util.*;

/** Parses the documented PresentMon 1.x schema, requested explicitly by the collector. */
public final class PresentMonCsv {
    public record Frame(long processId, String swapChain, double timeSeconds, double frameMs) { }
    private Map<String, Integer> header = Map.of();
    public Optional<Frame> accept(String line) {
        var fields = split(line.replace("\uFEFF", ""));
        if (fields.stream().anyMatch(field -> field.equalsIgnoreCase("MsBetweenPresents"))
                && fields.stream().anyMatch(field -> field.equalsIgnoreCase("ProcessID"))) {
            var columns = new HashMap<String, Integer>();
            for (int i = 0; i < fields.size(); i++) columns.put(fields.get(i).trim().toLowerCase(Locale.ROOT), i);
            header = columns;
            return Optional.empty();
        }
        if (header.isEmpty()) return Optional.empty();
        try {
            return Optional.of(new Frame(Long.parseLong(value(fields, "ProcessID")),
                    value(fields, "SwapChainAddress"), Double.parseDouble(value(fields, "TimeInSeconds")),
                    Double.parseDouble(value(fields, "MsBetweenPresents"))));
        } catch (IllegalArgumentException | IndexOutOfBoundsException invalid) { return Optional.empty(); }
    }
    private String value(List<String> fields, String column) {
        var index = header.get(column.toLowerCase(Locale.ROOT));
        if (index == null) throw new IllegalArgumentException(column);
        return fields.get(index).trim();
    }
    static List<String> split(String line) {
        var fields = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { field.append('"'); i++; }
                else quoted = !quoted;
            } else if (character == ',' && !quoted) { fields.add(field.toString()); field.setLength(0); }
            else field.append(character);
        }
        fields.add(field.toString());
        return fields;
    }
}
