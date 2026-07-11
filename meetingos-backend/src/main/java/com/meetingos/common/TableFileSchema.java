package com.meetingos.common;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 导入文件表头约束。一个 requirement 可以提供多组可选方案，每组方案内的列必须同时存在。
 */
public final class TableFileSchema {

    private static final TableFileSchema PERMISSIVE = new TableFileSchema(List.of(), List.of());

    private final List<Requirement> requirements;
    private final List<Column> textColumns;

    public TableFileSchema(List<Requirement> requirements, List<Column> textColumns) {
        this.requirements = List.copyOf(requirements);
        this.textColumns = List.copyOf(textColumns);
    }

    public static TableFileSchema permissive() {
        return PERMISSIVE;
    }

    public static Column column(String label, String... aliases) {
        Set<String> normalizedAliases = new LinkedHashSet<>();
        Arrays.stream(aliases).map(TableRow::normalizeHeader).forEach(normalizedAliases::add);
        return new Column(label, Set.copyOf(normalizedAliases));
    }

    @SafeVarargs
    public static Requirement require(String label, List<Column>... alternatives) {
        return new Requirement(label, Arrays.stream(alternatives).map(List::copyOf).toList());
    }

    public static List<Column> option(Column... columns) {
        return List.of(columns);
    }

    void validateHeaders(List<String> normalizedHeaders) {
        Set<String> available = new LinkedHashSet<>(normalizedHeaders);
        for (Requirement requirement : requirements) {
            boolean satisfied = requirement.alternatives().stream()
                    .anyMatch(option -> option.stream().allMatch(column -> column.presentIn(available)));
            if (!satisfied) {
                throw BusinessException.badRequest("导入文件缺少必填表头: " + requirement.label());
            }
        }
    }

    void validateTextValue(String normalizedHeader, String value, int rowNumber) {
        if (value == null || value.isBlank() || !looksLikeScientificNotation(value)) {
            return;
        }
        textColumns.stream()
                .filter(column -> column.aliases().contains(normalizedHeader))
                .findFirst()
                .ifPresent(column -> {
                    throw BusinessException.badRequest(
                            "第 " + rowNumber + " 行的“" + column.label() + "”疑似被 Excel 转成科学计数法，请将该列设置为文本后重新填写"
                    );
                });
    }

    private boolean looksLikeScientificNotation(String value) {
        return value.trim().matches("^[+-]?\\d+(?:\\.\\d+)?[eE][+-]?\\d+$");
    }

    public record Column(String label, Set<String> aliases) {
        private boolean presentIn(Set<String> headers) {
            return aliases.stream().anyMatch(headers::contains);
        }
    }

    public record Requirement(String label, List<List<Column>> alternatives) {
    }
}
