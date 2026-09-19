package com.mamoki.tour.global.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.boot.origin.SystemEnvironmentOrigin;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * 값이 비어 있는 설정 항목을 "지정하지 않은 것"으로 되돌린다.
 *
 * <p>스프링의 {@code ${VAR:기본값}} 은 빈 문자열을 <b>설정된 값</b>으로 본다. 그래서
 * {@code .env} 에 {@code DATA_LAB_LAG_DAYS=} 한 줄만 있어도 기본값 30 이 적용되지 않고
 * 빈 문자열이 그대로 내려간다. 빈 문자열은 숫자로 바뀌지 않으므로
 * {@code int}·{@code double} 프로퍼티는 바인딩 단계에서 터지고, 그 결과 앱 기동과
 * 모든 {@code @SpringBootTest} 가 실패한다. URL 계열은 더 조용해서, 빈 문자열이
 * 그대로 살아남았다가 호출 시점에 {@code URI is not absolute} 로 드러난다.
 *
 * <p>빈 값을 프로퍼티 클래스에서 걸러내는 방법은 통하지 않는다. 변환 실패가 생성자 호출보다
 * 먼저 일어나서 {@code record} 의 압축 생성자까지 도달하지 못하기 때문이다. 그래서 값이
 * 바인더에 닿기 전에 지운다.
 *
 * <p>대상은 맵으로 뒷받침되는 모든 열거 가능 프로퍼티 소스다. {@code .env} 파일만 보면
 * 부족한데, 도커 배포는 {@code env_file: .env.docker} 로 같은 값을 <b>컨테이너 환경변수</b>로
 * 넘겨서 빈 줄이 시스템 환경 소스로 들어오기 때문이다.
 *
 * <p>따라서 이 앱에서 "빈 값 = 지정하지 않음" 은 전역 규칙이다. 빈 문자열을 뜻 있는 값으로
 * 쓰고 싶은 설정은 이 규칙과 어긋나므로 두지 않는다. 지금 빈 기본값을 갖는 항목들
 * ({@code KOR_SERVICE_KEY}, {@code NAVER_QUERY_SUFFIX} 등) 은 지워도 기본값이 다시
 * 빈 문자열이라 동작이 같다.
 *
 * <p>설정 파일을 다 읽은 뒤에 돌아야 하므로 거의 마지막 순서로 둔다. 무엇이 "비어 있는가"
 * 를 이 클래스가 정하므로, 비어 있는 필수 값을 잡는
 * {@link RequiredEnvironmentVariablesPostProcessor} 보다는 반드시 먼저 돈다.
 */
public class BlankValueEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public int getOrder() {
        // 필수 값 확인(RequiredEnvironmentVariablesPostProcessor)이 바로 뒤에 온다.
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MutablePropertySources propertySources = environment.getPropertySources();

        // 순회 도중 교체하므로 목록을 먼저 복사해 둔다.
        for (PropertySource<?> propertySource : propertySources.stream().toList()) {
            PropertySource<?> replacement = withoutBlankValues(propertySource);

            if (replacement != null) {
                propertySources.replace(propertySource.getName(), replacement);
            }
        }
    }

    /** 지울 것이 없으면 {@code null} 을 돌려준다. */
    private static PropertySource<?> withoutBlankValues(PropertySource<?> propertySource) {
        if (!(propertySource instanceof EnumerablePropertySource<?> enumerable)) {
            return null;
        }

        if (!(propertySource.getSource() instanceof Map<?, ?> backing)) {
            return null;
        }

        Set<String> blankNames = blankNames(enumerable);

        if (blankNames.isEmpty()) {
            return null;
        }

        // 뒷받침 맵의 값을 그대로 옮긴다. OriginTrackedValue 를 벗기지 않아야
        // 오류 메시지에 "어느 파일 몇 번째 줄" 이 남는다.
        Map<String, Object> kept = new LinkedHashMap<>();
        backing.forEach((name, value) -> {
            if (!blankNames.contains(String.valueOf(name))) {
                kept.put(String.valueOf(name), value);
            }
        });

        return copyOf(propertySource, kept);
    }

    private static Set<String> blankNames(EnumerablePropertySource<?> enumerable) {
        Set<String> blankNames = new LinkedHashSet<>();

        for (String name : enumerable.getPropertyNames()) {
            if (isBlank(enumerable.getProperty(name))) {
                blankNames.add(name);
            }
        }

        return blankNames;
    }

    /**
     * 문자열만 본다. 빈 목록·빈 맵은 값이 없다는 뜻이 아니라 "비어 있음" 자체가 뜻이라
     * 건드리지 않는다.
     */
    private static boolean isBlank(Object value) {
        return value instanceof CharSequence text && text.toString().isBlank();
    }

    private static PropertySource<?> copyOf(PropertySource<?> original, Map<String, Object> values) {
        String name = original.getName();

        // 시스템 환경 소스는 SPRING_PROFILES_ACTIVE 같은 이름을 느슨하게 맞춰주므로
        // 평범한 MapPropertySource 로 바꾸면 그 기능을 잃는다. 같은 타입을 유지한다.
        if (original instanceof SystemEnvironmentPropertySource) {
            return new FilteredSystemEnvironmentPropertySource(name, values);
        }

        if (original instanceof OriginTrackedMapPropertySource originTracked) {
            return new OriginTrackedMapPropertySource(name, values, originTracked.isImmutable());
        }

        return new MapPropertySource(name, values);
    }

    /**
     * 시스템 환경변수의 느슨한 이름 매칭과 출처 추적을 모두 유지하는 사본.
     *
     * <p>스프링 부트가 원래 끼워 넣는 소스는 패키지 전용 클래스라 직접 만들 수 없어,
     * 같은 두 성질만 다시 구현한다.
     */
    private static final class FilteredSystemEnvironmentPropertySource
            extends SystemEnvironmentPropertySource implements OriginLookup<String> {

        private FilteredSystemEnvironmentPropertySource(String name, Map<String, Object> source) {
            super(name, source);
        }

        @Override
        public Origin getOrigin(String key) {
            String propertyName = resolvePropertyName(key);

            if (super.containsProperty(propertyName)) {
                return new SystemEnvironmentOrigin(propertyName);
            }

            return null;
        }
    }
}
