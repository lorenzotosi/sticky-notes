// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api.dto;

import stickynotes.domain.NoteStatus;

public record MoveNoteRequest(NoteStatus targetStatus, Integer destinationIndex, Integer expectedRevision) {}
