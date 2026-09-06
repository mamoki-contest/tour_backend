package com.mamoki.tour.global.rsdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RsDataTest {

    @Test
    @DisplayName("resultCode 앞자리에서 statusCode 를 파생한다")
    void deriveStatusCode() {
        assertThat(RsData.of("200-1", "성공").statusCode()).isEqualTo(200);
        assertThat(RsData.of("400-3", "잘못된 요청").statusCode()).isEqualTo(400);
        assertThat(RsData.of("500", "서버 오류").statusCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("statusCode 로 성공과 실패를 구분한다")
    void distinguishSuccessAndFail() {
        assertThat(RsData.of("200-1", "성공").isSuccess()).isTrue();
        assertThat(RsData.of("404-1", "없음").isSuccess()).isFalse();
        assertThat(RsData.of("500-1", "서버 오류").isFail()).isTrue();
    }

    @Test
    @DisplayName("data 를 담아 응답을 만들 수 있다")
    void carryData() {
        RsData<String> rsData = RsData.of("200-1", "조회했습니다.", "강릉");

        assertThat(rsData.data()).isEqualTo("강릉");
        assertThat(rsData.msg()).isEqualTo("조회했습니다.");
    }

    @Test
    @DisplayName("HTTP 상태 코드로 해석할 수 없는 resultCode 는 거부한다")
    void rejectInvalidResultCode() {
        assertThatThrownBy(() -> RsData.of("OK-1", "성공"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> RsData.of("", "성공"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
