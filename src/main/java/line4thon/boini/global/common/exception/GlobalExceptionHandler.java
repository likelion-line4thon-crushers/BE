package line4thon.boini.global.common.exception;

import java.util.stream.Collectors;
import line4thon.boini.global.common.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  // 커스텀 예외
  @ExceptionHandler(CustomException.class)
  public ResponseEntity<BaseResponse<Object>> handleCustomException(CustomException ex) {
    log.error("Custom 오류 발생: {}", ex.getMessage());
    return ResponseEntity
        .status(ex.getErrorCode().getStatus())
        .body(BaseResponse.failure(
            ex.getErrorCode().getCode(),       // 예: "E001"
            ex.getErrorCode().getMessage()     // 예: "임시 주소 저장 실패"
        ));
  }

  // Validation 실패 (@RequestBody + @Valid)
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<BaseResponse<Object>> handleValidationException(
      MethodArgumentNotValidException ex) {
    return buildValidationErrorResponse(ex);
  }

  // Validation 실패 (@ModelAttribute + @Valid)
  // @RequestBody 는 MethodArgumentNotValidException 을 던지지만,
  // @ModelAttribute (multipart/form-data) 는 BindException 을 던집니다.
  // ChunkUploadRequest 가 @ModelAttribute 로 바인딩되므로 이 핸들러가 필요합니다.
  // 연결: ChunkUploadController → @Valid @ModelAttribute ChunkUploadRequest
  @ExceptionHandler(BindException.class)
  public ResponseEntity<BaseResponse<Object>> handleBindException(BindException ex) {
    return buildValidationErrorResponse(ex);
  }

  // 두 Validation 핸들러의 공통 응답 생성 로직
  private ResponseEntity<BaseResponse<Object>> buildValidationErrorResponse(BindException ex) {
    String errorMessages = ex.getBindingResult().getFieldErrors().stream()
        .map(e -> String.format("[%s] %s", e.getField(), e.getDefaultMessage()))
        .collect(Collectors.joining(" / "));

    log.warn("Validation 오류 발생: {}", errorMessages);

    return ResponseEntity
        .badRequest()
        .body(BaseResponse.failure("VALIDATION_ERROR", errorMessages));
  }

  // 예상치 못한 예외
  @ExceptionHandler(Exception.class)
  public ResponseEntity<BaseResponse<Object>>  handleException(Exception ex) {
    log.error("Server 오류 발생: ", ex);

    return ResponseEntity
        .status(GlobalErrorCode.INTERNAL_SERVER_ERROR.getStatus())
        .body(BaseResponse.failure(
            GlobalErrorCode.INTERNAL_SERVER_ERROR.getCode(),
            GlobalErrorCode.INTERNAL_SERVER_ERROR.getMessage()
        ));
  }
}