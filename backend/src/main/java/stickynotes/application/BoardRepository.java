// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.application;

import java.util.Optional;
import stickynotes.contract.BoardSnapshot;

public interface BoardRepository {

    /**
     * Loads a board
     * @return the board
     */
    Optional<BoardSnapshot> load();
    
    Optional<BoardSnapshot> save(
        BoardSnapshot updated,
        int expectedRevision);
}
