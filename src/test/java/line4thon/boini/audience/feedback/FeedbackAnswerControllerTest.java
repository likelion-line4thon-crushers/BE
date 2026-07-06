package line4thon.boini.audience.feedback;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class FeedbackAnswerControllerTest {

  @Autowired WebApplicationContext context;
  MockMvc mockMvc;

  private MockMvc mvc() {
    if (mockMvc == null) mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    return mockMvc;
  }

  @Test
  void postSavesAnswersAndReturnsThem() throws Exception {
    String body = "{\"audienceId\":\"aud1\",\"answers\":[{\"questionId\":10,\"answerText\":\"좋았어요\"}]}";

    mvc().perform(post("/api/rooms/roomZ/feedback-answers")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.answers[0].questionId").value(10))
        .andExpect(jsonPath("$.data.answers[0].answerText").value("좋았어요"));
  }
}
