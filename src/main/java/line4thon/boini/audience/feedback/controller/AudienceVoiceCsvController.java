package line4thon.boini.audience.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import java.nio.charset.StandardCharsets;
import line4thon.boini.audience.feedback.service.AudienceVoiceCsvService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class AudienceVoiceCsvController {

  private final AudienceVoiceCsvService csvService;

  @GetMapping("/{roomId}/audience-voice/csv")
  @Operation(summary = "청중의 목소리 CSV 다운로드",
      description = "질문별 청중 답변을 청중 단위로 집계한 CSV 파일을 반환합니다.")
  public ResponseEntity<byte[]> downloadCsv(@PathVariable String roomId) {
    String csv = csvService.buildCsv(roomId);
    byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    byte[] body = csv.getBytes(StandardCharsets.UTF_8);
    byte[] out = new byte[bom.length + body.length];
    System.arraycopy(bom, 0, out, 0, bom.length);
    System.arraycopy(body, 0, out, bom.length, body.length);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
    headers.setContentDisposition(
        ContentDisposition.attachment()
            .filename("audience-voice-" + roomId + ".csv", StandardCharsets.UTF_8)
            .build());
    return ResponseEntity.ok().headers(headers).body(out);
  }
}
