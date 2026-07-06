package line4thon.boini.audience.feedback.repository;

import java.util.List;
import line4thon.boini.audience.feedback.entity.FeedbackQuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface FeedbackQuestionRepository extends JpaRepository<FeedbackQuestionEntity, Long> {
  List<FeedbackQuestionEntity> findByRoomIdOrderByOrderIndexAsc(String roomId);

  @Transactional
  void deleteByRoomId(String roomId);
}
