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
  @Operation(summary = "원본 이미지 URL 발급", description = "특정 페이지의 원본 이미지를 presigned GET URL로 발급합니다.")
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
  public BaseResponse<UploadPagesResponse> uploadPages(
      @PathVariable String roomId,
      @PathVariable String deckId,
      @RequestPart("files") List<MultipartFile> files
  ) {
    return BaseResponse.success(deckAssets.uploadPages(roomId, deckId, files));
  }

  @GetMapping("/{roomId}/{deckId}/meta")
  public BaseResponse<SlidesMetaResponse> meta(
      @PathVariable String roomId,
      @PathVariable String deckId,
      @RequestParam @Min(1) int totalPages
  ) {
    return BaseResponse.success(deckAssets.getThumbnails(roomId, deckId, totalPages));
  }
}
