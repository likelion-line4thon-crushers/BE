package line4thon.boini.presenter.room.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "report")
public class Report {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "room_id", unique = true, nullable = false)
  private String roomId;

  private Integer emojiCount;
  private Integer questionCount;
  private Integer attentionSlide;

  @Column(columnDefinition = "json")
  private String top3Question;

  @Column(columnDefinition = "json")
  private String popularEmoji;

  @Column(columnDefinition = "json")
  private String popularQuestion;

  @Column(columnDefinition = "json")
  private String revisit;
}