package line4thon.boini.audience.feedback.repository;

import java.util.List;
import line4thon.boini.audience.feedback.entity.FeedbackAnswerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface FeedbackAnswerRepository extends JpaRepository<FeedbackAnswerEntity, Long> {
  List<FeedbackAnswerEntity> findByRoomId(String roomId);

  List<FeedbackAnswerEntity> findByRoomIdAndAudienceId(String roomId, String audienceId);

  @Transactional
  void deleteByRoomIdAndAudienceId(String roomId, String audienceId);
}
