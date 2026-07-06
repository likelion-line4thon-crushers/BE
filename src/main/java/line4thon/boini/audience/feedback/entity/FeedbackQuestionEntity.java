package line4thon.boini.audience.feedback.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "feedback_questions", indexes = @Index(name = "idx_fq_room", columnList = "roomId"))
public class FeedbackQuestionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String roomId;

  @Column(nullable = false)
  private int orderIndex;

  @Column(nullable = false, length = 500)
  private String questionText;
}
