package com.mamoki.tour.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mamoki.tour.infra.datalab.DataLabProperties;
import com.mamoki.tour.infra.its.ItsProperties;
import com.mamoki.tour.infra.korservice.KorServiceProperties;

/**
 * {@code .env.example} 을 그대로 복사한 환경에서도 앱이 뜨는지 확인한다.
 *
 * <p>값이 빈 줄을 그대로 실은 파일을 {@code .env} 와 같은 방식으로 읽는다. 이 테스트가
 * 실행되는 것 자체가 검증의 절반이다. 빈 값이 걸러지지 않으면
 * {@code DATA_LAB_LAG_DAYS=} 가 {@code int} 로 바뀌지 못해 컨텍스트가 아예 뜨지 않는다.
 *
 * <p>나머지 절반은 빈 값 자리에 <b>기본값이 들어왔는지</b>다. URL 계열은 빈 문자열이
 * 살아남아도 기동은 되고 호출 시점에야 {@code URI is not absolute} 로 드러나기 때문에,
 * 기동 여부만으로는 충분하지 않다.
 */
@SpringBootTest
@ActiveProfiles({"test", "blankenv"})
class BlankEnvValueDefaultsTest {

    @Autowired
    private DataLabProperties dataLabProperties;

    @Autowired
    private KorServiceProperties korServiceProperties;

    @Autowired
    private ItsProperties itsProperties;

    @Test
    @DisplayName("빈 값으로 둔 정수 설정은 기본값을 쓴다")
    void blankIntegerFallsBackToDefault() {
        assertThat(dataLabProperties.lagDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("빈 값으로 둔 주소 설정은 기본 주소를 쓴다")
    void blankUrlFallsBackToDefault() {
        assertThat(korServiceProperties.baseUrl())
                .isEqualTo("https://apis.data.go.kr/B551011/KorService2");
        assertThat(itsProperties.baseUrl()).isEqualTo("https://openapi.its.go.kr:9443");
    }

    @Test
    @DisplayName("공백만 있는 값도 비어 있는 것으로 보고 기본 주소를 쓴다")
    void whitespaceOnlyUrlFallsBackToDefault() {
        assertThat(dataLabProperties.baseUrl())
                .isEqualTo("https://apis.data.go.kr/B551011/DataLabService");
    }

    @Test
    @DisplayName("빈 값으로 둔 소수 설정은 기본값을 쓴다")
    void blankDecimalFallsBackToDefault() {
        assertThat(itsProperties.halfSpan()).isEqualByComparingTo(new BigDecimal("0.005"));
    }

    @Test
    @DisplayName("채워 둔 값은 기본값으로 덮이지 않는다")
    void filledValueWins() {
        assertThat(dataLabProperties.windowDays()).isEqualTo(5);
    }
}
