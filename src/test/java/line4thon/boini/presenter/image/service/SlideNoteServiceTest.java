package line4thon.boini.presenter.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.jwt.service.JwtService;
import line4thon.boini.presenter.image.entity.SlideNote;
import line4thon.boini.presenter.image.repository.SlideNoteRepository;
import line4thon.boini.presenter.pdf.dto.SlideNoteDraft;
import line4thon.boini.presenter.room.entity.SessionStatus;
import line4thon.boini.presenter.room.service.RoomService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class SlideNoteServiceTest {

    private final SlideNoteRepository repository = Mockito.mock(SlideNoteRepository.class);
    private final JwtService jwtService = Mockito.mock(JwtService.class);
    private final RoomService roomService = Mockito.mock(RoomService.class);
    private final SlideNoteService service = new SlideNoteService(repository, jwtService, roomService);

    @Test
    void storesOnlyNonBlankNotesWithSlideNumbers() {
        service.replaceNotes("room-1", "deck-1", List.of(
            new SlideNoteDraft(1, " first note "),
            new SlideNoteDraft(2, " "),
            new SlideNoteDraft(3, "third note")
        ));

        verify(repository).deleteByRoomIdAndDeckId("room-1", "deck-1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SlideNote>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue())
            .extracting(SlideNote::getSlideNumber, SlideNote::getNotes)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "first note"),
                org.assertj.core.groups.Tuple.tuple(3, "third note")
            );
    }

    @Test
    void clearsExistingNotesWhenReplacementHasNoNotes() {
        service.replaceNotes("room-1", "deck-1", List.of(
            new SlideNoteDraft(1, " "),
            new SlideNoteDraft(2, null)
        ));

        verify(repository).deleteByRoomIdAndDeckId("room-1", "deck-1");
        verify(repository, Mockito.never()).saveAll(any());
    }

    @Test
    void returnsNotesForValidPresenterToken() {
        when(jwtService.parse("presenter-token")).thenReturn(claims("room-1", "presenter"));
        when(repository.findByRoomIdAndDeckIdOrderBySlideNumberAsc("room-1", "deck-1"))
            .thenReturn(List.of(SlideNote.builder()
                .roomId("room-1")
                .deckId("deck-1")
                .slideNumber(2)
                .notes("note")
                .createdAt(Instant.now())
                .build()));

        var response = service.getPresenterNotes("room-1", "deck-1", "Bearer presenter-token");

        assertThat(response.getNotes()).hasSize(1);
        assertThat(response.getNotes().get(0).getPage()).isEqualTo(2);
        assertThat(response.getNotes().get(0).getNotes()).isEqualTo("note");
    }

    @Test
    void createsManualPresenterNoteForValidPresenterToken() {
        when(jwtService.parse("presenter-token")).thenReturn(claims("room-1", "presenter"));
        when(roomService.getSessionStatus("room-1")).thenReturn(SessionStatus.waiting);
        when(repository.findByRoomIdAndDeckIdAndSlideNumber("room-1", "deck-1", 2))
            .thenReturn(Optional.empty());
        when(repository.save(any(SlideNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updatePresenterNote(
            "room-1",
            "deck-1",
            2,
            " manual note ",
            "Bearer presenter-token"
        );

        verify(roomService).getSessionStatus("room-1");
        ArgumentCaptor<SlideNote> captor = ArgumentCaptor.forClass(SlideNote.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRoomId()).isEqualTo("room-1");
        assertThat(captor.getValue().getDeckId()).isEqualTo("deck-1");
        assertThat(captor.getValue().getSlideNumber()).isEqualTo(2);
        assertThat(captor.getValue().getNotes()).isEqualTo("manual note");
        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getNotes()).isEqualTo("manual note");
    }

    @Test
    void updatesExistingManualPresenterNote() {
        SlideNote existing = SlideNote.builder()
            .id(7L)
            .roomId("room-1")
            .deckId("deck-1")
            .slideNumber(2)
            .notes("old")
            .createdAt(Instant.now())
            .build();
        when(jwtService.parse("presenter-token")).thenReturn(claims("room-1", "presenter"));
        when(roomService.getSessionStatus("room-1")).thenReturn(SessionStatus.waiting);
        when(repository.findByRoomIdAndDeckIdAndSlideNumber("room-1", "deck-1", 2))
            .thenReturn(Optional.of(existing));
        when(repository.save(any(SlideNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updatePresenterNote("room-1", "deck-1", 2, "new", "Bearer presenter-token");

        verify(repository).save(existing);
        assertThat(existing.getNotes()).isEqualTo("new");
        assertThat(response.getNotes()).isEqualTo("new");
    }

    @Test
    void clearsManualPresenterNoteWhenBlank() {
        when(jwtService.parse("presenter-token")).thenReturn(claims("room-1", "presenter"));
        when(roomService.getSessionStatus("room-1")).thenReturn(SessionStatus.waiting);

        var response = service.updatePresenterNote("room-1", "deck-1", 2, " ", "Bearer presenter-token");

        verify(repository).deleteByRoomIdAndDeckIdAndSlideNumber("room-1", "deck-1", 2);
        verify(repository, never()).save(any());
        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getNotes()).isEmpty();
    }

    @Test
    void rejectsAudienceTokenWhenUpdatingManualNote() {
        when(jwtService.parse("audience-token")).thenReturn(claims("room-1", "audience"));

        assertThatThrownBy(() ->
            service.updatePresenterNote("room-1", "deck-1", 2, "note", "Bearer audience-token")
        ).isInstanceOf(CustomException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsManualNoteUpdateAfterSessionStarts() {
        when(jwtService.parse("presenter-token")).thenReturn(claims("room-1", "presenter"));
        when(roomService.getSessionStatus("room-1")).thenReturn(SessionStatus.live);

        assertThatThrownBy(() ->
            service.updatePresenterNote("room-1", "deck-1", 2, "note", "Bearer presenter-token")
        ).isInstanceOf(CustomException.class);

        verify(repository, never()).save(any());
        verify(repository, never()).deleteByRoomIdAndDeckIdAndSlideNumber(any(), any(), anyInt());
    }

    @Test
    void rejectsManualNoteUpdateAfterSessionEnded() {
        when(jwtService.parse("presenter-token")).thenReturn(claims("room-1", "presenter"));
        when(roomService.getSessionStatus("room-1")).thenReturn(SessionStatus.ended);

        assertThatThrownBy(() ->
            service.updatePresenterNote("room-1", "deck-1", 2, "note", "Bearer presenter-token")
        ).isInstanceOf(CustomException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsMissingAudienceAndWrongRoomTokens() {
        assertThatThrownBy(() -> service.getPresenterNotes("room-1", "deck-1", null))
            .isInstanceOf(CustomException.class);

        when(jwtService.parse("audience-token")).thenReturn(claims("room-1", "audience"));
        assertThatThrownBy(() -> service.getPresenterNotes("room-1", "deck-1", "Bearer audience-token"))
            .isInstanceOf(CustomException.class);

        when(jwtService.parse("wrong-room")).thenReturn(claims("room-2", "presenter"));
        assertThatThrownBy(() -> service.getPresenterNotes("room-1", "deck-1", "Bearer wrong-room"))
            .isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsInvalidToken() {
        when(jwtService.parse(any())).thenThrow(new RuntimeException("bad token"));

        assertThatThrownBy(() -> service.getPresenterNotes("room-1", "deck-1", "Bearer bad"))
            .isInstanceOf(CustomException.class);
    }

    private Claims claims(String roomId, String role) {
        Claims claims = Jwts.claims();
        claims.put("roomId", roomId);
        claims.put("role", role);
        return claims;
    }
}
