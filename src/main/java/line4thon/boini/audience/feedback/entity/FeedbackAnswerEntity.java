package line4thon.boini.audience.feedback.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "feedback_answers", indexes = @Index(name = "idx_fa_room", columnList = "roomId"))
public class FeedbackAnswerEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String roomId;

  @Column(nullable = false)
  private String audienceId;

  @Column(nullable = false)
  private Long questionId;

  @Column(length = 2000)
  private String answerText;

  @Column(nullable = false)
  private Instant createdAt;
}
