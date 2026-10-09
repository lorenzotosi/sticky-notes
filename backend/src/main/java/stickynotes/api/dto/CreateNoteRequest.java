// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

package stickynotes.api.dto;

import stickynotes.domain.NoteColor;

public record CreateNoteRequest(String content, NoteColor color, Integer expectedRevision) {}
