package line4thon.boini.presenter.pdf.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import line4thon.boini.global.jwt.util.JwtUtil;
import line4thon.boini.presenter.pdf.dto.response.AssemblyCompleteResponse;
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
        when(pdfChunkService.storeFonts(eq("u1"), any())).thenReturn(List.of());
        mvc.perform(multipart("/api/upload/u1/fonts")
                .file(new MockMultipartFile("fonts", "a.ttf", "font/ttf", new byte[] {1})))
            .andExpect(status().isOk());
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
