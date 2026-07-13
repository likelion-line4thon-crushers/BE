package line4thon.boini.presenter.pdf.dto.response;

public record ChunkUploadResult(
    boolean complete,
    ChunkReceiveResponse progress,
    AssemblyCompleteResponse assembled,
    NeedsFontsResponse needsFonts
) {
    public static ChunkUploadResult inProgress(ChunkReceiveResponse progress) {
        return new ChunkUploadResult(false, progress, null, null);
    }
    public static ChunkUploadResult assembled(AssemblyCompleteResponse assembled) {
        return new ChunkUploadResult(true, null, assembled, null);
    }
    public static ChunkUploadResult needsFonts(NeedsFontsResponse needsFonts) {
        return new ChunkUploadResult(true, null, null, needsFonts);
    }
}
