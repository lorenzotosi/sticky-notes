// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.application;

import stickynotes.contract.BoardSnapshot;
import stickynotes.domain.NoteColor;
import stickynotes.domain.NoteStatus;

/**
 * BoardService
 */
public interface BoardService {

    BoardSnapshot getBoard();

    public BoardSnapshot createNote(int expectedRevision, String content, NoteColor color);

    /** Updates content and/or color; a null argument leaves that value unchanged. */
    public BoardSnapshot updateNote(int expectedRevision, String noteId, String content, NoteColor color);

    public BoardSnapshot deleteNote(int expectedRevision, String noteId);

    public BoardSnapshot moveNote(int expectedRevision, String noteId, NoteStatus targetStatus, int destinationIndex);

    public BoardSnapshot setWipLimit(int expectedRevision, int limit);

    public BoardSnapshot blockNote(int expectedRevision, String noteId, String reason);

    public BoardSnapshot unblockNote(int expectedRevision, String noteId);

    public BoardSnapshot addItem(int expectedRevision, String noteId, String label);

    /** Updates label and/or completion; a null argument leaves that value unchanged. */
    public BoardSnapshot updateItem(
            int expectedRevision, String noteId, String itemId, String label, Boolean completed);

    public BoardSnapshot deleteItem(int expectedRevision, String noteId, String itemId);
}
