package line4thon.boini.presenter.image.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import line4thon.boini.global.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * 슬라이드 이미지의 S3 키 생성 및 URL 발급 공통 유틸.
 *
 * 기존에 DeckAssetService 에만 있던 buildKey, normalizePrefix, buildPresignedUrl 등의
 * 메서드를 PdfParseService 에서도 동일하게 사용해야 해서 공통 컴포넌트로 추출했습니다.
 *
 * 연결:
 *   - DeckAssetService: 기존 이미지 직접 업로드 방식에서 URL 생성 시 사용
 *   - PdfParseService:  청크 조립 후 PDF 파싱 방식에서 URL 생성 시 사용
 */
@Component
@RequiredArgsConstructor
public class SlideS3Helper {

    private final S3Presigner presigner;
    private final AppProperties props;

    /**
     * S3 오브젝트 키를 생성합니다.
     * 형식: {rootPrefix}/{roomId}/{deckId}/{pages or thumbs}/{0001}.{ext}
     *
     * @param thumb true → thumbs 폴더 (썸네일), false → pages 폴더 (원본)
     */
    public String buildKey(String roomId, String deckId, int page, boolean thumb, String ext) {
        String root = normalizePrefix(props.getS3().getRootPrefix());
        String folder = thumb ? "thumbs" : "pages";
        String fileName = "%04d.%s".formatted(page, ext);

        return root.isEmpty()
            ? "%s/%s/%s/%s".formatted(roomId, deckId, folder, fileName)
            : "%s/%s/%s/%s/%s".formatted(root, roomId, deckId, folder, fileName);
    }

    /**
     * CloudFront 도메인 설정 여부에 따라 공개 URL 또는 Presigned URL 을 반환합니다.
     *
     * @param forcePresign true → CloudFront 무시하고 무조건 Presigned URL 반환
     *                     false → CloudFront 설정 있으면 공개 URL, 없으면 Presigned URL 반환
     *
     * 연결: application.properties → app.s3.cloudfront-domain 값으로 분기 결정
     */
    public String buildUrl(String key, boolean forcePresign) {
        String cf = props.getS3().getCloudfrontDomain();
        if (!forcePresign && cf != null && !cf.isBlank()) {
            // CloudFront CDN 공개 URL (캐싱 적용, 빠름)
            return "https://%s/%s".formatted(cf.replaceAll("/+$", ""), urlEncodePath(key));
        }
        // AWS S3 Presigned URL (서명된 임시 URL, TTL: app.s3.presign-seconds)
        return buildPresignedUrl(key);
    }

    /**
     * S3 Presigned GET URL 을 생성합니다.
     * 유효 시간은 application.properties → app.s3.presign-seconds 설정값을 따릅니다.
     * http → https 강제 변환 처리 포함.
     */
    private String buildPresignedUrl(String key) {
        GetObjectRequest get = GetObjectRequest.builder()
            .bucket(props.getS3().getBucket())
            .key(key)
            .build();
        PresignedGetObjectRequest pre = presigner.presignGetObject(b -> b
            .signatureDuration(Duration.ofSeconds(props.getS3().getPresignSeconds()))
            .getObjectRequest(get));
        String url = pre.url().toString();
        return url.startsWith("http://") ? "https://" + url.substring(7) : url;
    }

    /**
     * rootPrefix 앞뒤의 슬래시와 공백을 제거합니다.
     * 예: " /presentations/ " → "presentations"
     */
    private String normalizePrefix(String s) {
        if (s == null || s.isBlank()) return "";
        String t = s.trim();
        while (t.startsWith("/")) t = t.substring(1);
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t;
    }

    /**
     * S3 키의 각 경로 세그먼트를 URL 인코딩합니다.
     * CloudFront URL 생성 시 한글·특수문자 포함된 키를 안전하게 처리하기 위해 사용합니다.
     * 슬래시(/)는 경로 구분자이므로 인코딩하지 않습니다.
     */
    private String urlEncodePath(String path) {
        return String.join("/",
            Arrays.stream(path.split("/"))
                .map(p -> URLEncoder.encode(p, StandardCharsets.UTF_8))
                .toList());
    }
}
