package line4thon.boini.presenter.room.entity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomOptions {

  private boolean reactionsEnabled = false;

  private boolean qnaEnabled = false;

  private boolean realtimeFeedbackEnabled = false;

  private boolean timerEnabled = false;

  private boolean nextSlidePreviewEnabled = false;
}
