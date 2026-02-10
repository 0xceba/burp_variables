package com._0xceba;

/**
 * Immutable record holding variable value and optional auto-update regex.
 *
 * @param value The variable's value.
 * @param regex The regex pattern to auto-update the variable value (can be empty).
 */
public record VariableData(String value, String regex) {
    /**
     * Constructs a VariableData with an empty regex.
     *
     * @param value The variable's value.
     */
    public VariableData(String value) {
        this(value, "");
    }
}
