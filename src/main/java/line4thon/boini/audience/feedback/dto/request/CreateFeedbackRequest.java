package line4thon.boini.audience.feedback.dto.request;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class CreateFeedbackRequest {
  private String audienceId;

  @Min(value = 1, message = "별점은 1~5 입니다.")
  @Max(value = 5, message = "별점은 1~5 입니다.")
  private int rating;    // 필수

  @Size(max = 2000, message = "후기는 최대 2000자까지 입력 가능합니다.")
  private String comment; // 선택
}