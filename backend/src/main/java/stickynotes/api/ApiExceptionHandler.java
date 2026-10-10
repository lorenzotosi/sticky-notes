// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import stickynotes.api.dto.ApiErrorResponse;
import stickynotes.application.BoardServiceException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BoardServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleBoardServiceException(BoardServiceException ex) {
        HttpStatus status =
                switch (ex.getCode()) {
                    case "INVALID_REQUEST",
                            "INVALID_CONTENT",
                            "INVALID_COLOR",
                            "INVALID_STATUS",
                            "INVALID_INDEX",
                            "INVALID_WIP_LIMIT",
                            "INVALID_BLOCK_REASON",
                            "INVALID_LABEL",
                            "INVALID_REVISION" -> HttpStatus.BAD_REQUEST;
                    case "NOTE_NOT_FOUND", "ITEM_NOT_FOUND" -> HttpStatus.NOT_FOUND;
                    case "REVISION_CONFLICT",
                            "WIP_LIMIT_REACHED",
                            "WIP_BELOW_OCCUPANCY",
                            "INVALID_TRANSITION",
                            "NOTE_BLOCKED",
                            "CHECKLIST_INCOMPLETE",
                            "NOTE_DONE_READ_ONLY",
                            "BOARD_FULL",
                            "CHECKLIST_FULL",
                            "DUPLICATE_ID" -> HttpStatus.CONFLICT;
                    case "DATABASE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
                    default -> HttpStatus.INTERNAL_SERVER_ERROR;
                };

        ApiErrorResponse body = new ApiErrorResponse(
                ex.getCode(), defaultMessageFor(ex.getCode()), ex.getField(), ex.getCurrentRevision());

        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("INVALID_REQUEST", "Malformed JSON payload", null, null));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "INVALID_REVISION", "Missing expectedRevision parameter", ex.getParameterName(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_SERVER_ERROR", "An unexpected error occurred", null, null));
    }

    private static String defaultMessageFor(String code) {
        return switch (code) {
            case "INVALID_REQUEST" -> "Malformed or incomplete request payload.";
            case "INVALID_CONTENT" -> "Note content is blank or exceeds allowed size.";
            case "INVALID_COLOR" -> "Invalid note color provided.";
            case "INVALID_REVISION" -> "Missing or negative expectedRevision.";
            case "NOTE_NOT_FOUND" -> "Target note not found.";
            case "REVISION_CONFLICT" -> "Revision conflict detected.";
            case "DATABASE_UNAVAILABLE" -> "Database service is temporarily unavailable.";
            case "INVALID_STATUS" -> "Invalid note status.";
            case "INVALID_INDEX" -> "Target destination index is out of bounds.";
            case "INVALID_WIP_LIMIT" -> "WIP limit must be between 1 and 20.";
            case "INVALID_BLOCK_REASON" -> "Block reason must be between 1 and 200 characters.";
            case "WIP_LIMIT_REACHED" -> "Cannot move note to DOING: column capacity reached.";
            case "WIP_BELOW_OCCUPANCY" -> "Cannot lower WIP limit below current occupancy.";
            case "INVALID_TRANSITION" -> "Illegal note status transition.";
            case "NOTE_BLOCKED" -> "Blocked notes cannot be moved to another column.";
            case "NOTE_DONE_READ_ONLY" -> "Completed notes cannot be blocked or moved.";
            default -> code;
        };
    }
}
