package line4thon.boini.presenter.pdf.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ChunkUploadRequest {

    @NotNull
    private MultipartFile chunk;

    @NotBlank
    private String uploadId;

    @NotBlank
    private String roomId;

    @NotBlank
    private String deckId;

    @Min(0)
    private int chunkIndex;

    @Min(1)
    private int totalChunks;

    @NotBlank
    private String fileName;

    @Positive
    private long fileSize;
}
