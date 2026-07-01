package line4thon.boini.presenter.image.service;

import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.List;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.exception.GlobalErrorCode;
import line4thon.boini.global.jwt.service.JwtService;
import line4thon.boini.presenter.image.dto.SlideNoteDto;
import line4thon.boini.presenter.image.dto.response.SlideNotesResponse;
import line4thon.boini.presenter.image.entity.SlideNote;
import line4thon.boini.presenter.image.repository.SlideNoteRepository;
import line4thon.boini.presenter.pdf.dto.SlideNoteDraft;
import line4thon.boini.presenter.room.entity.SessionStatus;
import line4thon.boini.presenter.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SlideNoteService {

    private final SlideNoteRepository repository;
    private final JwtService jwtService;
    private final RoomService roomService;

    @Transactional
    public void replaceNotes(String roomId, String deckId, List<SlideNoteDraft> drafts) {
        repository.deleteByRoomIdAndDeckId(roomId, deckId);

        if (drafts == null || drafts.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        List<SlideNote> notes = drafts.stream()
            .filter(draft -> hasText(draft.notes()))
            .map(draft -> SlideNote.builder()
                .roomId(roomId)
                .deckId(deckId)
                .slideNumber(draft.slideNumber())
                .notes(draft.notes().trim())
                .createdAt(now)
                .build())
            .toList();

        if (notes.isEmpty()) {
            return;
        }

        repository.saveAll(notes);
    }

    @Transactional(readOnly = true)
    public SlideNotesResponse getPresenterNotes(String roomId, String deckId, String authorization) {
        validatePresenterToken(roomId, authorization);
        List<SlideNoteDto> notes = repository.findByRoomIdAndDeckIdOrderBySlideNumberAsc(roomId, deckId)
            .stream()
            .map(note -> new SlideNoteDto(note.getSlideNumber(), note.getNotes()))
            .toList();
        return new SlideNotesResponse(roomId, deckId, notes);
    }

    @Transactional
    public SlideNoteDto updatePresenterNote(
        String roomId,
        String deckId,
        int slideNumber,
        String notes,
        String authorization
    ) {
        validatePresenterToken(roomId, authorization);
        validateSessionNotStarted(roomId);
        if (slideNumber < 1) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        String normalized = notes == null ? "" : notes.trim();
        if (!hasText(normalized)) {
            repository.deleteByRoomIdAndDeckIdAndSlideNumber(roomId, deckId, slideNumber);
            return new SlideNoteDto(slideNumber, "");
        }

        Instant now = Instant.now();
        SlideNote note = repository.findByRoomIdAndDeckIdAndSlideNumber(roomId, deckId, slideNumber)
            .orElseGet(() -> SlideNote.builder()
                .roomId(roomId)
                .deckId(deckId)
                .slideNumber(slideNumber)
                .createdAt(now)
                .build());
        note.setNotes(normalized);
        if (note.getCreatedAt() == null) {
            note.setCreatedAt(now);
        }

        SlideNote saved = repository.save(note);
        return new SlideNoteDto(saved.getSlideNumber(), saved.getNotes());
    }

    private void validatePresenterToken(String roomId, String authorization) {
        String token = extractBearer(authorization);
        if (!hasText(token)) {
            throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
        }

        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (RuntimeException ex) {
            throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
        }

        String tokenRoomId = String.valueOf(claims.get("roomId"));
        String role = String.valueOf(claims.get("role"));
        if (!roomId.equals(tokenRoomId) || !"presenter".equalsIgnoreCase(role)) {
            throw new CustomException(GlobalErrorCode.FORBIDDEN);
        }
    }

    private void validateSessionNotStarted(String roomId) {
        if (roomService.getSessionStatus(roomId) != SessionStatus.waiting) {
            throw new CustomException(GlobalErrorCode.FORBIDDEN);
        }
    }

    private String extractBearer(String authorization) {
        if (!hasText(authorization)) {
            return null;
        }
        String prefix = "Bearer ";
        return authorization.startsWith(prefix) ? authorization.substring(prefix.length()).trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
