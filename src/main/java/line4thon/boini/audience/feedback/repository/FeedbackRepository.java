package line4thon.boini.audience.feedback.repository;

import java.util.List;
import line4thon.boini.audience.feedback.entity.FeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackRepository extends JpaRepository<FeedbackEntity, Long> {
  List<FeedbackEntity> findByRoomIdOrderByCreatedAtDesc(String roomId);
}
