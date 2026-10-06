package stickynotes.infrastructure;

import stickynotes.contract.BoardSnapshot;
import stickynotes.domain.NoteColor;

/**
 * BoardService
 */
public interface BoardService {

    public BoardSnapshot createNote(int expectedRevision, String content, NoteColor color);
}
