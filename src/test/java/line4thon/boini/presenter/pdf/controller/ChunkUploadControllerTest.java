package line4thon.boini.presenter.pdf.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import line4thon.boini.global.jwt.util.JwtUtil;
import line4thon.boini.presenter.pdf.dto.FontEntry;
import line4thon.boini.presenter.pdf.dto.response.AssemblyCompleteResponse;
import line4thon.boini.presenter.pdf.dto.response.ChunkUploadResult;
import line4thon.boini.presenter.pdf.dto.response.FontUploadResponse;
import line4thon.boini.presenter.pdf.dto.response.NeedsFontsResponse;
import line4thon.boini.presenter.pdf.model.FontStatus;
import line4thon.boini.presenter.pdf.service.PdfChunkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ChunkUploadController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class)
class ChunkUploadControllerTest {
    @Autowired MockMvc mvc;
    @MockBean PdfChunkService pdfChunkService;
    // JwtAuthenticationFilter is a plain @Component (not gated by SecurityAutoConfiguration),
    // so @WebMvcTest still picks it up and needs JwtUtil to construct it.
    @MockBean JwtUtil jwtUtil;

    @Test
    void uploadFontsReturns200() throws Exception {
        when(pdfChunkService.storeFonts(eq("u1"), any(), any()))
            .thenReturn(new FontUploadResponse(null, null, List.of()));
        mvc.perform(multipart("/api/upload/u1/fonts")
                .file(new MockMultipartFile("fonts", "a.ttf", "font/ttf", new byte[] {1})))
            .andExpect(status().isOk());
    }

    @Test
    void uploadFontsReportsMatchAgainstTargetFont() throws Exception {
        when(pdfChunkService.storeFonts(eq("u1"), any(), eq("Malgun Gothic")))
            .thenReturn(new FontUploadResponse(false, "Malgun Gothic", List.of("Yoon Mokryn")));
        mvc.perform(multipart("/api/upload/u1/fonts")
                .file(new MockMultipartFile("fonts", "a.ttf", "font/ttf", new byte[] {1}))
                .param("targetFont", "Malgun Gothic"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.matched").value(false))
            .andExpect(jsonPath("$.data.uploadedFamilies[0]").value("Yoon Mokryn"));
    }

    @Test
    void uploadChunkReturns201WithNeedsFontsBody() throws Exception {
        when(pdfChunkService.receiveChunk(any())).thenReturn(
            ChunkUploadResult.needsFonts(NeedsFontsResponse.of(
                "11111111-1111-1111-1111-111111111111",
                List.of(new FontEntry("Malgun Gothic", FontStatus.MISSING, false, false)))));
        mvc.perform(multipart("/api/upload/chunk")
                .file(new MockMultipartFile("chunk", "c.bin", "application/octet-stream", new byte[] {1}))
                .param("uploadId", "11111111-1111-1111-1111-111111111111")
                .param("roomId", "r1")
                .param("deckId", "d1")
                .param("chunkIndex", "0")
                .param("totalChunks", "1")
                .param("fileName", "deck.pptx")
                .param("fileSize", "1"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("NEEDS_FONTS"))
            .andExpect(jsonPath("$.data.fontReport").isNotEmpty());
    }

    @Test
    void uploadChunkRejectsMalformedUploadIdBeforeServiceCall() throws Exception {
        mvc.perform(multipart("/api/upload/chunk")
                .file(new MockMultipartFile("chunk", "c.bin", "application/octet-stream", new byte[] {1}))
                .param("uploadId", "../../../etc/evil")
                .param("roomId", "r1")
                .param("deckId", "d1")
                .param("chunkIndex", "0")
                .param("totalChunks", "1")
                .param("fileName", "deck.pptx")
                .param("fileSize", "1"))
            .andExpect(status().isBadRequest());

        verify(pdfChunkService, never()).receiveChunk(any());
    }

    @Test
    void finalizeReturns201() throws Exception {
        when(pdfChunkService.finalize(eq("u1"), eq(false)))
            .thenReturn(AssemblyCompleteResponse.builder().status("READY").uploadId("u1")
                .pdfId("pdf-1").fileName("f.pptx").totalPages(3).streamUrl("/api/pdf/pdf-1/stream").build());
        mvc.perform(post("/api/upload/u1/finalize")
                .contentType(MediaType.APPLICATION_JSON).content("{\"proceedWithoutFonts\":false}"))
            .andExpect(status().isCreated());
    }
}
