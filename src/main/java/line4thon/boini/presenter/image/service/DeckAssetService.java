package line4thon.boini.presenter.image.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.presenter.image.exception.ImageAssetErrorCode;
import line4thon.boini.presenter.room.exception.RoomErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import line4thon.boini.global.config.AppProperties;
import line4thon.boini.presenter.image.dto.response.OriginalUrlResponse;
import line4thon.boini.presenter.image.dto.response.SlidesMetaResponse;
import line4thon.boini.presenter.image.dto.ThumbnailDto;
import line4thon.boini.presenter.image.dto.response.UploadPagesResponse;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckAssetService {

  private final S3Client s3;
  private final AppProperties props;
  private final SlideS3Helper slideS3Helper;

  public OriginalUrlResponse getOriginalUrl(String roomId, String deckId, int page, String extHint) {
    return getOriginalInternal(roomId, deckId, page, extHint);
  }

  // =====================================================================
  // [과거 방식] 프론트에서 PDF → 이미지 변환 후 직접 업로드하는 방식
  // 현재는 백엔드 청크 수신 → PDF 조립 → 이미지 변환 → SSE 스트리밍 방식으로 대체됨
  // =====================================================================
  public UploadPagesResponse uploadPages(String roomId, String deckId, List<MultipartFile> files) {
    String bucket = props.getS3().getBucket();

    if (files == null || files.isEmpty()) {
      log.warn("[검증] 업로드 파일이 비어 있음: roomId={}, deckId={}", roomId, deckId);
      throw new CustomException(ImageAssetErrorCode.NO_FILES);
    }

    List<ThumbnailDto> thumbs = new ArrayList<>();
    String firstPageOriginalUrl = null;

    int page = 1;
    for (MultipartFile f : files) {
      String contentType = f.getContentType();
      String ext      = guessExt(contentType);
      String origKey  = slideS3Helper.buildKey(roomId, deckId, page, false, ext);
      String thumbKey = slideS3Helper.buildKey(roomId, deckId, page, true, "webp");

      try (InputStream inForThumb = f.getInputStream();
          ByteArrayOutputStream thumbOut = new ByteArrayOutputStream()) {

        try {
          s3.putObject(
              PutObjectRequest.builder()
                  .bucket(bucket)
                  .key(origKey)
                  .contentType(contentType)
                  .build(),
              RequestBody.fromInputStream(f.getInputStream(), f.getSize())
          );
          log.info("[S3] 원본 업로드 완료 → s3://{}/{}", bucket, origKey);
        } catch (SdkException e) {
          log.error("[오류] 원본 업로드 실패: key={}, 이유={}", origKey, e.getMessage(), e);
          throw new CustomException(ImageAssetErrorCode.ORIGINAL_UPLOAD_FAILED);
        }

        try {
          Thumbnails.of(inForThumb)
              .size(320, 320)
              .outputFormat("webp")
              .outputQuality(0.8f)
              .toOutputStream(thumbOut);
        } catch (IOException tEx) {
          log.error("[오류] 썸네일 생성 실패: key={}, 이유={}", thumbKey, tEx.getMessage(), tEx);
          throw new CustomException(ImageAssetErrorCode.THUMBNAIL_GENERATION_FAILED);
        }

        try {
          s3.putObject(
              PutObjectRequest.builder()
                  .bucket(bucket)
                  .key(thumbKey)
                  .contentType("image/webp")
                  .build(),
              RequestBody.fromBytes(thumbOut.toByteArray())
          );
          log.info("[S3] 썸네일 업로드 완료 → s3://{}/{}", bucket, thumbKey);
        } catch (SdkException e) {
          log.error("[오류] 썸네일 업로드 실패: key={}, 이유={}", thumbKey, e.getMessage(), e);
          throw new CustomException(ImageAssetErrorCode.THUMBNAIL_UPLOAD_FAILED);
        }

        thumbs.add(new ThumbnailDto(page, slideS3Helper.buildUrl(thumbKey, false)));

        if (page == 1) {
          firstPageOriginalUrl = slideS3Helper.buildUrl(origKey, true);
        }

        page++;
      } catch (IOException e) {
        log.error("[오류] 페이지 업로드 중 IO 예외: page={}, 이유={}", page, e.getMessage(), e);
        throw new CustomException(ImageAssetErrorCode.UNKNOWN_ERROR);
      }
    }

    if (!thumbs.isEmpty()) {
      log.info("[응답] 첫 번째 썸네일 URL: {}", thumbs.get(0).getThumbnailUrl());
      log.info("[응답] 첫 번째 원본 URL: {}", firstPageOriginalUrl);
    }

    return new UploadPagesResponse(deckId, files.size(), firstPageOriginalUrl, thumbs);
  }

  public SlidesMetaResponse getThumbnails(String roomId, String deckId, int totalPages) {
    if (totalPages <= 0) {
      throw new CustomException(ImageAssetErrorCode.INVALID_PAGE_NUMBER);
    }

    List<ThumbnailDto> list = new ArrayList<>(totalPages);
    for (int p = 1; p <= totalPages; p++) {
      String key = slideS3Helper.buildKey(roomId, deckId, p, true, "webp");
      list.add(new ThumbnailDto(p, slideS3Helper.buildUrl(key, false)));
    }
    log.info("[S3] 총 {}개의 썸네일 메타 URL 생성 완료", totalPages);
    return new SlidesMetaResponse(roomId, deckId, totalPages, list);
  }

  private OriginalUrlResponse getOriginalInternal(String roomId, String deckId, int page, String extHint) {
    if (page <= 0) {
      throw new CustomException(ImageAssetErrorCode.INVALID_PAGE_NUMBER);
    }

    String ext = (extHint == null || extHint.isBlank()) ? "png" : extHint.toLowerCase();
    if (!List.of("png", "jpg", "jpeg", "webp").contains(ext)) {
      throw new CustomException(RoomErrorCode.INVALID_REQUEST);
    }

    String key = slideS3Helper.buildKey(roomId, deckId, page, false, ext);
    return new OriginalUrlResponse(roomId, deckId, page, slideS3Helper.buildUrl(key, true));
  }

  private String guessExt(String contentType) {
    if (contentType == null) return "png";
    if (contentType.contains("png")) return "png";
    if (contentType.contains("jpeg") || contentType.contains("jpg")) return "jpg";
    if (contentType.contains("webp")) return "webp";
    return "png";
  }
}
