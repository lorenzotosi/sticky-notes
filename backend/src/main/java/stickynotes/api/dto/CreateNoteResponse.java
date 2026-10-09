// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api.dto;

import stickynotes.contract.BoardSnapshot;

public record CreateNoteResponse(BoardSnapshot board, String createdNoteId) {}
