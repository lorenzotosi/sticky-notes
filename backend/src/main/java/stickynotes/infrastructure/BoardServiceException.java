// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.infrastructure;

/** Application failure carrying a stable error code and an optional field. */
public final class BoardServiceException extends RuntimeException {
    private final String code;
    private final String field;

    /**
     * Creates a failure without assigning an HTTP status.
     *
     * @param code the application or domain error code
     * @param field the related field, or {@code null} if the failure is not field-specific
     */
    public BoardServiceException(String code, String field) {
        super(code);
        this.code = code;
        this.field = field;
    }

    /** Returns the application or domain error code. */
    public String getCode() {
        return code;
    }

    /** Returns the related field, or {@code null} if none applies. */
    public String getField() {
        return field;
    }
}
