package line4thon.boini.presenter.image.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
    name = "slide_note",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_slide_note_room_deck_slide",
            columnNames = {"room_id", "deck_id", "slide_number"}
        )
    }
)
public class SlideNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, length = 64)
    private String roomId;

    @Column(name = "deck_id", nullable = false, length = 64)
    private String deckId;

    @Column(name = "slide_number", nullable = false)
    private int slideNumber;

    @Column(name = "notes", columnDefinition = "TEXT", nullable = false)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
