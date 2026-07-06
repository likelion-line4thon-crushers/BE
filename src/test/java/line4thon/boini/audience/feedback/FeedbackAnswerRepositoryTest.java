package line4thon.boini.audience.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import line4thon.boini.audience.feedback.entity.FeedbackAnswerEntity;
import line4thon.boini.audience.feedback.repository.FeedbackAnswerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
class FeedbackAnswerRepositoryTest {

  @Autowired FeedbackAnswerRepository repository;

  @Test
  void savesAndFindsByRoomId() {
    repository.save(FeedbackAnswerEntity.builder()
        .roomId("r1").audienceId("a1").questionId(10L).answerText("좋았어요").createdAt(Instant.now()).build());

    assertThat(repository.findByRoomId("r1")).hasSize(1);
    assertThat(repository.findByRoomId("r1").get(0).getAnswerText()).isEqualTo("좋았어요");
  }
}
