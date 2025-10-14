package line4thon.boini.global.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(title = "BaseResponse DTO", description = "공통 API 응답 형식")
public class BaseResponse<T> {

  @Schema(description = "요청 성공 여부", example = "true")
  private boolean success;
  private String code;

  @Schema(description = "응답 메시지", example = "요청이 성공적으로 처리되었습니다.")
  private String message;

  @Schema(description = "응답 데이터")
  private T data;

  // 성공 응답
  public static <T> BaseResponse<T> success(T data) {
    return BaseResponse.<T>builder()
        .success(true)
        .message("요청이 성공적으로 처리되었습니다.")
        .data(data)
        .build();
  }

  public static <T> BaseResponse<T> success(String message, T data) {
    return BaseResponse.<T>builder()
        .success(true)
        .message(message)
        .data(data)
        .build();
  }

  // 실패 응답
  public static <T> BaseResponse<T> failure(String code, String message) {
    return BaseResponse.<T>builder()
        .success(false)
        .code(code)
        .message(message)
        .data(null)
        .build();
  }
}