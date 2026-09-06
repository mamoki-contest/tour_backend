package com.mamoki.tour.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mamoki.tour.global.rsdata.RsData;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("ServiceException 은 resultCode 에서 파생된 HTTP 상태로 응답한다")
    void serviceExceptionKeepsStatus() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"))
                .andExpect(jsonPath("$.statusCode").value(404))
                .andExpect(jsonPath("$.msg").value("관광지를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("검증 실패는 400 과 필드별 오류로 변환된다")
    void validationFailureBecomesFieldErrors() throws Exception {
        mvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"count\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].field").value("count"))
                .andExpect(jsonPath("$.data[1].field").value("name"));
    }

    @Test
    @DisplayName("필수 파라미터 누락은 400 으로 응답한다")
    void missingParameterBecomesBadRequest() throws Exception {
        mvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-3"));
    }

    @Test
    @DisplayName("파라미터 형식 불일치는 400 으로 응답한다")
    void typeMismatchBecomesBadRequest() throws Exception {
        mvc.perform(get("/test/param").param("size", "많이"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-4"));
    }

    @Test
    @DisplayName("미처리 예외는 내부 정보를 노출하지 않고 500 으로 응답한다")
    void unexpectedExceptionHidesInternals() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.resultCode").value("500-1"))
                .andExpect(jsonPath("$.msg").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("공급자 응답 원문"))));
    }

    @Test
    @DisplayName("정상 응답도 같은 RsData 봉투를 사용한다")
    void successUsesSameEnvelope() throws Exception {
        mvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data").value("강릉"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/ok")
        RsData<String> ok() {
            return RsData.of("200-1", "조회했습니다.", "강릉");
        }

        @GetMapping("/test/not-found")
        RsData<Void> notFound() {
            throw new ServiceException("404-1", "관광지를 찾을 수 없습니다.");
        }

        @GetMapping("/test/boom")
        RsData<Void> boom() {
            throw new IllegalStateException("공급자 응답 원문이 그대로 들어 있는 내부 메시지");
        }

        @GetMapping("/test/param")
        RsData<Integer> param(@RequestParam int size) {
            return RsData.of("200-1", "조회했습니다.", size);
        }

        @PostMapping("/test/validate")
        RsData<Void> validate(@Valid @RequestBody TestRequest request) {
            return RsData.of("200-1", "저장했습니다.");
        }
    }

    record TestRequest(@NotBlank String name, @Positive int count) {
    }
}
