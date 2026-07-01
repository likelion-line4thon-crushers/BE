package line4thon.boini.presenter.image.repository;

import java.util.List;
import java.util.Optional;
import line4thon.boini.presenter.image.entity.SlideNote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlideNoteRepository extends JpaRepository<SlideNote, Long> {
    List<SlideNote> findByRoomIdAndDeckIdOrderBySlideNumberAsc(String roomId, String deckId);

    Optional<SlideNote> findByRoomIdAndDeckIdAndSlideNumber(String roomId, String deckId, int slideNumber);

    void deleteByRoomIdAndDeckId(String roomId, String deckId);

    void deleteByRoomIdAndDeckIdAndSlideNumber(String roomId, String deckId, int slideNumber);
}
