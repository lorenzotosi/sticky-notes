// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.application;

/** Application failure carrying a stable error code, optional field, and conflict revision. */
public final class BoardServiceException extends RuntimeException {
    private final String code;
    private final String field;
    private final Integer currentRevision;

    /**
     * Creates a failure without assigning an HTTP status.
     *
     * @param code the application or domain error code
     * @param field the related field, or {@code null} if the failure is not field-specific
     */
    public BoardServiceException(String code, String field) {
        this(code, field, null);
    }

    /** Creates a failure with the revision observed when reporting a conflict. */
    public BoardServiceException(String code, String field, Integer currentRevision) {
        super(code);
        this.code = code;
        this.field = field;
        this.currentRevision = currentRevision;
    }

    /** Returns the application or domain error code. */
    public String getCode() {
        return code;
    }

    /** Returns the related field, or {@code null} if none applies. */
    public String getField() {
        return field;
    }

    /** Returns the observed conflict revision, or {@code null} if unavailable or not applicable. */
    public Integer getCurrentRevision() {
        return currentRevision;
    }
}
