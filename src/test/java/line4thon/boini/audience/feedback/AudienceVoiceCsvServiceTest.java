package line4thon.boini.audience.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import line4thon.boini.audience.feedback.entity.FeedbackAnswerEntity;
import line4thon.boini.audience.feedback.entity.FeedbackEntity;
import line4thon.boini.audience.feedback.entity.FeedbackQuestionEntity;
import line4thon.boini.audience.feedback.repository.FeedbackAnswerRepository;
import line4thon.boini.audience.feedback.repository.FeedbackQuestionRepository;
import line4thon.boini.audience.feedback.repository.FeedbackRepository;
import line4thon.boini.audience.feedback.service.AudienceVoiceCsvService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AudienceVoiceCsvServiceTest {

  @Mock FeedbackQuestionRepository questionRepository;
  @Mock FeedbackAnswerRepository answerRepository;
  @Mock FeedbackRepository feedbackRepository;
  @InjectMocks AudienceVoiceCsvService service;

  @Test
  void buildsHeaderAndPerAudienceRowsWithEscaping() {
    when(questionRepository.findByRoomIdOrderByOrderIndexAsc("r1")).thenReturn(List.of(
        FeedbackQuestionEntity.builder().id(10L).roomId("r1").orderIndex(0).questionText("How, good?").build(),
        FeedbackQuestionEntity.builder().id(11L).roomId("r1").orderIndex(1).questionText("Why?").build()));
    when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("r1")).thenReturn(List.of(
        FeedbackEntity.builder().roomId("r1").audienceId("u1").rating(4).comment("").createdAt(Instant.now()).build()));
    when(answerRepository.findByRoomId("r1")).thenReturn(List.of(
        FeedbackAnswerEntity.builder().roomId("r1").audienceId("u1").questionId(10L).answerText("said \"hi\"").createdAt(Instant.now()).build(),
        FeedbackAnswerEntity.builder().roomId("r1").audienceId("u1").questionId(11L).answerText("because").createdAt(Instant.now()).build()));

    String csv = service.buildCsv("r1");
    String[] lines = csv.split("\r\n");

    assertThat(lines[0]).isEqualTo("audienceId,rating,\"How, good?\",Why?");
    assertThat(lines[1]).isEqualTo("u1,4,\"said \"\"hi\"\"\",because");
  }

  @Test
  void neutralizesFormulaInjection() {
    when(questionRepository.findByRoomIdOrderByOrderIndexAsc("r1")).thenReturn(List.of(
        FeedbackQuestionEntity.builder().id(10L).roomId("r1").orderIndex(0).questionText("Comment").build()));
    when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("r1")).thenReturn(List.of(
        FeedbackEntity.builder().roomId("r1").audienceId("u1").rating(4).comment("").createdAt(Instant.now()).build()));
    when(answerRepository.findByRoomId("r1")).thenReturn(List.of(
        FeedbackAnswerEntity.builder().roomId("r1").audienceId("u1").questionId(10L).answerText("=SUM(A1)").createdAt(Instant.now()).build()));

    String csv = service.buildCsv("r1");
    String[] lines = csv.split("\r\n");

    assertThat(lines[1]).isEqualTo("u1,4,'=SUM(A1)");
  }
}
