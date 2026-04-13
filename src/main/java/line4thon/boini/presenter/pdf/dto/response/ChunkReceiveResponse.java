package line4thon.boini.presenter.pdf.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChunkReceiveResponse {

    private String uploadId;
    private int chunkIndex;
    private long receivedChunks;
    private int totalChunks;
    private long chunkSize;
    private String status;
}
