package line4thon.boini.presenter.pdf.dto.response;

public record ChunkUploadResult(
    boolean complete,
    ChunkReceiveResponse progress,
    AssemblyCompleteResponse assembled
) {

    public static ChunkUploadResult inProgress(ChunkReceiveResponse progress) {
        return new ChunkUploadResult(false, progress, null);
    }

    public static ChunkUploadResult assembled(AssemblyCompleteResponse assembled) {
        return new ChunkUploadResult(true, null, assembled);
    }
}
