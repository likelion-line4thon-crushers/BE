package line4thon.boini.audience.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import line4thon.boini.audience.feedback.entity.FeedbackQuestionEntity;
import line4thon.boini.audience.feedback.repository.FeedbackQuestionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
class FeedbackQuestionRepositoryTest {

  @Autowired FeedbackQuestionRepository repository;

  @Test
  void findsByRoomIdInOrder() {
    repository.save(FeedbackQuestionEntity.builder().roomId("r1").orderIndex(1).questionText("b").build());
    repository.save(FeedbackQuestionEntity.builder().roomId("r1").orderIndex(0).questionText("a").build());
    repository.save(FeedbackQuestionEntity.builder().roomId("r2").orderIndex(0).questionText("x").build());

    List<FeedbackQuestionEntity> result = repository.findByRoomIdOrderByOrderIndexAsc("r1");

    assertThat(result).extracting(FeedbackQuestionEntity::getQuestionText).containsExactly("a", "b");
  }

  @Test
  void deleteByRoomIdRemovesOnlyThatRoom() {
    repository.save(FeedbackQuestionEntity.builder().roomId("r1").orderIndex(0).questionText("a").build());
    repository.save(FeedbackQuestionEntity.builder().roomId("r2").orderIndex(0).questionText("x").build());

    repository.deleteByRoomId("r1");

    assertThat(repository.findByRoomIdOrderByOrderIndexAsc("r1")).isEmpty();
    assertThat(repository.findByRoomIdOrderByOrderIndexAsc("r2")).hasSize(1);
  }
}
