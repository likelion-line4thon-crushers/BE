package line4thon.boini.presenter.image.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.presenter.image.dto.response.OriginalUrlResponse;
import line4thon.boini.presenter.image.dto.response.SlidesMetaResponse;
import line4thon.boini.presenter.image.dto.response.UploadPagesResponse;
import line4thon.boini.presenter.image.service.DeckAssetService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/presentations")
@Validated
public class DeckAssetController {

  private final DeckAssetService deckAssets;

  // 표준 엔드포인트: 쿼리스트링으로 확장자 받기 + page는 숫자만 매칭
  @GetMapping("/{roomId}/{deckId}/pages/{page:\\d+}")
  @Operation(
      summary = "원본 이미지 Presigned URL 발급",
      description = """
      특정 프레젠테이션의 페이지(슬라이드) 원본 이미지를 다운로드할 수 있는 presigned GET URL을 발급합니다.
      - `ext` 파라미터로 확장자를 지정합니다 (기본값: png)
      - URL은 제한된 기간 동안만 유효합니다 (설정값: presignSeconds)
      """
  )
  public BaseResponse<OriginalUrlResponse> getOriginal(
      @PathVariable String roomId,
      @PathVariable String deckId,
      @PathVariable @Min(1) int page,
      @RequestParam(defaultValue = "png")
      @Pattern(regexp = "png|jpg|jpeg|webp") String ext
  ) {
    var res = deckAssets.getOriginalUrl(roomId, deckId, page, ext); // [수정] public API만 호출
    return BaseResponse.success(res);
  }

  // 레거시/직관적 경로 지원: /pages/2.png 같은 호출 처리
  @GetMapping("/{roomId}/{deckId}/pages/{page:\\d+}.{ext}")
  @Operation(hidden = true)
  public BaseResponse<OriginalUrlResponse> getOriginalWithExtInPath(
      @PathVariable String roomId,
      @PathVariable String deckId,
      @PathVariable @Min(1) int page,
      @PathVariable @Pattern(regexp = "png|jpg|jpeg|webp") String ext
  ) {
    return BaseResponse.success(deckAssets.getOriginalUrl(roomId, deckId, page, ext)); // [수정]
  }

  @PostMapping(value = "/{roomId}/{deckId}/pages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "프레젠테이션 페이지 이미지 업로드",
      description = """
      프론트엔드에서 PDF를 이미지로 분할한 결과를 업로드합니다.
      - 요청 형식: `multipart/form-data`
      - 필드명: `files[]` (여러 페이지 이미지)
      - 서버에서는 각 페이지를 S3에 업로드하고, 썸네일(webp)도 함께 생성합니다.
      - 응답에는 첫 페이지 원본 URL과 각 페이지 썸네일 목록이 포함됩니다.
      """
  )
  public BaseResponse<UploadPagesResponse> uploadPages(
      @PathVariable String roomId,
      @PathVariable String deckId,
      @RequestPart("files") List<MultipartFile> files
  ) {
    return BaseResponse.success(deckAssets.uploadPages(roomId, deckId, files));
  }

  @GetMapping("/{roomId}/{deckId}/meta")
  @Operation(
      summary = "프레젠테이션 썸네일 목록 조회",
      description = """
      업로드된 프레젠테이션의 모든 썸네일(webp) 이미지의 절대 URL 목록을 반환합니다.
      - `totalPages` 파라미터로 총 페이지 수를 전달해야 합니다.
      - CloudFront가 설정된 경우 공개 URL을, 아니면 presigned URL을 반환합니다.
      """
  )
  public BaseResponse<SlidesMetaResponse> meta(
      @PathVariable String roomId,
      @PathVariable String deckId,
      @RequestParam @Min(1) int totalPages
  ) {
    return BaseResponse.success(deckAssets.getThumbnails(roomId, deckId, totalPages));
  }
}
