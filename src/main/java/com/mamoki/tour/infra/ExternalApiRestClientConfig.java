package com.mamoki.tour.infra;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.mamoki.tour.infra.datalab.DataLabProperties;
import com.mamoki.tour.infra.gnits.GnItsProperties;
import com.mamoki.tour.infra.its.ItsProperties;
import com.mamoki.tour.infra.kakao.KakaoLocalProperties;
import com.mamoki.tour.infra.korservice.KorServiceProperties;
import com.mamoki.tour.infra.locgohub.LocgoHubProperties;
import com.mamoki.tour.infra.naver.NaverApiHubProperties;
import com.mamoki.tour.infra.tarrltetar.TarRlteTarProperties;
import com.mamoki.tour.infra.tatscnctrrate.TatsCnctrRateProperties;

/**
 * 외부 API 클라이언트가 쓸 {@link RestClient.Builder} 를 공급자별로 하나씩 만든다.
 *
 * <p>클라이언트가 생성자 안에서 빌더를 직접 만들면 테스트가 HTTP 계층을 갈아 끼울 자리가
 * 없다(#66). 그래서 만드는 일을 여기로 옮기고, 클라이언트는 받은 빌더를 {@code build()}
 * 하기만 한다.
 *
 * <p><b>요청 팩토리를 클라이언트가 아니라 여기서 붙이는 것이 핵심이다.</b>
 * {@code MockRestServiceServer.bindTo(builder)} 는 빌더의 요청 팩토리를 가짜로 바꿔 끼우는
 * 방식으로 동작한다. 클라이언트가 받은 빌더에 다시 {@code requestFactory(...)} 를 부르면
 * 그 가짜가 덮여 사라져 목이 아무것도 가로채지 못한다. 제한 시간은 공급자마다 다르므로
 * 그 설정을 빌더를 만드는 쪽이 함께 맡는다.
 *
 * <p>빌더는 공급자마다 따로 둔다. 하나를 나눠 쓰면 한 공급자의 제한 시간이 다른 공급자에게
 * 딸려 간다.
 *
 * <p>공급자 기본 주소({@code *_BASE_URL})는 여기서 다루지 않는다. 각 클라이언트가 설정값으로
 * 절대 URI 를 만들어 호출하므로 빌더에 {@code baseUrl} 을 박으면 두 곳에서 같은 것을 정하게
 * 된다.
 */
@Configuration
public class ExternalApiRestClientConfig {

    @Bean
    public RestClient.Builder korServiceRestClientBuilder(KorServiceProperties properties) {
        return builder(properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RestClient.Builder locgoHubRestClientBuilder(LocgoHubProperties properties) {
        return builder(properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RestClient.Builder tatsCnctrRateRestClientBuilder(TatsCnctrRateProperties properties) {
        return builder(properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RestClient.Builder tarRlteTarRestClientBuilder(TarRlteTarProperties properties) {
        return builder(properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RestClient.Builder dataLabRestClientBuilder(DataLabProperties properties) {
        return builder(properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RestClient.Builder itsRestClientBuilder(ItsProperties properties) {
        return builder(properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RestClient.Builder gnItsRestClientBuilder(GnItsProperties properties) {
        return builder(properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RestClient.Builder naverBlogSearchRestClientBuilder(NaverApiHubProperties properties) {
        return builder(properties.connectTimeout(), properties.readTimeout());
    }

    /**
     * 이미지 검색은 블로그 검색과 같은 허브·같은 설정을 쓰지만 빌더는 따로 만든다.
     *
     * <p>하나를 나눠 쓰면 {@code MockRestServiceServer} 가 한쪽 클라이언트를 가로채는 순간
     * 다른 쪽까지 함께 가짜가 된다. 두 클라이언트를 한 테스트에서 쓰는 자리(배치)가 있어
     * 그때 무엇을 가로챘는지가 흐려진다.
     */
    @Bean
    public RestClient.Builder naverImageSearchRestClientBuilder(NaverApiHubProperties properties) {
        return builder(properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RestClient.Builder kakaoLocalRestClientBuilder(KakaoLocalProperties properties) {
        return builder(properties.connectTimeout(), properties.readTimeout());
    }

    private static RestClient.Builder builder(Duration connectTimeout, Duration readTimeout) {
        return RestClient.builder().requestFactory(requestFactory(connectTimeout, readTimeout));
    }

    private static ClientHttpRequestFactory requestFactory(Duration connectTimeout,
                                                           Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        return factory;
    }
}
