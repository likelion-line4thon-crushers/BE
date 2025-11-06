package line4thon.boini.audience.feedback.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "feedbacks")
public class FeedbackEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String roomId;

  @Column(nullable = false)
  private String audienceId;

  @Column(nullable = false)
  private int rating;

  @Column(length = 2000)
  private String comment;

  @Column(nullable = false)
  private Instant createdAt;
}
