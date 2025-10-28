package line4thon.boini.presenter.room.entity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomOptions {

  // 청중 원탭 이모지 반응
  private boolean reactionsEnabled = false;

  // 실시간 질문
  private boolean qnaEnabled = false;

  // 실시간 피드백 분석
  private boolean realtimeFeedbackEnabled = false;

  // 세션 경과 타이머 표시
  private boolean timerEnabled = false;

  // 다음 슬라이드 미리보기 허용
  private boolean nextSlidePreviewEnabled = false;
}
