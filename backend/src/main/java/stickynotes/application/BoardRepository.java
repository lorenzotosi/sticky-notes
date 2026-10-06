// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.application;

import java.util.Optional;
import stickynotes.contract.BoardSnapshot;

/**
 * Persistence boundary for the complete {@code main} board aggregate.
 *
 * <p>Snapshots are validated against the shared domain rules. Conditional saves protect the stored
 * board from updates based on an obsolete revision.
 */
public interface BoardRepository {

    /**
     * Loads the current board and validates its persisted state.
     *
     * <p>Invalid persisted data and storage failures are reported to the caller rather than treated
     * as an absent board. Loading does not create or repair a board.
     *
     * @return the validated snapshot, or an empty result if the board does not exist
     * @throws IllegalArgumentException if the persisted state violates the snapshot or domain rules
     * @throws IllegalStateException if storage access fails
     */
    Optional<BoardSnapshot> load();

    /**
     * Atomically replaces the board when its stored revision matches {@code expectedRevision}.
     *
     * <p>The candidate snapshot must still carry the expected revision. A successful save persists
     * and returns revision {@code expectedRevision + 1}. The operation neither creates a missing
     * board nor retries an unmatched revision, and requires an acknowledged write.
     *
     * @param updated the complete candidate snapshot of {@code main}, before revision increment
     * @param expectedRevision the non-negative revision on which the candidate is based
     * @return the saved snapshot with its new revision, or an empty result if the board is absent
     *     or its stored revision does not match
     * @throws IllegalArgumentException if the board ID, candidate revision, or domain state is invalid
     * @throws ArithmeticException if incrementing the revision would overflow a Java {@code int}
     * @throws IllegalStateException if storage access fails or the system does not acknowledge the
     *     write
     */
    Optional<BoardSnapshot> save(BoardSnapshot updated, int expectedRevision);
}
