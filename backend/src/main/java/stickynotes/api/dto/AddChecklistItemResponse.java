// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api.dto;

import stickynotes.contract.BoardSnapshot;

public record AddChecklistItemResponse(BoardSnapshot board, String createdItemId) {}
