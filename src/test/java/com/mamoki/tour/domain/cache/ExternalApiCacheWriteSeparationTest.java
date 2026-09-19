package com.mamoki.tour.domain.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheWriter;

/**
 * 캐시 쓰기의 분리가 유지되는지 구조로 본다(#85).
 *
 * <p>{@link ExternalApiCacheReadOnlyCallerTest} 가 동작을 지키고, 이 테스트는 그 동작이
 * 기대는 <b>배선</b>을 지킨다. 둘을 나눈 이유는 여기서 막는 회귀가 조용하기 때문이다.
 * 쓰기를 {@link ExternalApiCacheService} 안으로 다시 옮기고 전파 속성만 붙여 두면,
 * 같은 클래스 내부 호출이라 프록시를 타지 않아 애너테이션이 아무 일도 하지 않는다.
 * 컴파일도 되고 기동도 되고 대부분의 테스트도 통과한다 — 읽기 전용 호출자의 첫 조회에서만
 * 다시 깨진다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ExternalApiCacheWriteSeparationTest {

    private static final Path CACHE_SERVICE_SOURCE = Path.of(
            "src/main/java/com/mamoki/tour/domain/cache/service/ExternalApiCacheService.java");

    @Autowired
    private ExternalApiCacheWriter cacheWriter;

    @Test
    @DisplayName("별도 트랜잭션 쓰기는 REQUIRES_NEW 로 선언된다")
    void writeInNewTransactionIsRequiresNew() throws NoSuchMethodException {
        Transactional annotation = writeMethod("writeInNewTransaction").getAnnotation(Transactional.class);

        assertThat(annotation)
                .withFailMessage("읽기 전용 호출자를 위한 쓰기에는 @Transactional 이 있어야 한다.")
                .isNotNull();
        assertThat(annotation.propagation())
                .withFailMessage("읽기 전용 호출자의 쓰기는 호출자 트랜잭션 밖에서 커밋돼야 한다(#85).")
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("합류 쓰기는 기본 전파(REQUIRED)를 그대로 쓴다")
    void joiningWriteStaysRequired() throws NoSuchMethodException {
        Transactional annotation = writeMethod("write").getAnnotation(Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.propagation())
                .withFailMessage("쓰기를 받아 주는 호출자에게는 합류해야 한다. "
                        + "합류하지 않으면 호출자가 자기 쓰기를 다시 읽지 못한다.")
                .isEqualTo(Propagation.REQUIRED);
    }

    @Test
    @DisplayName("쓰기 빈은 프록시로 주입된다 - 전파 속성이 실제로 적용되는 자리")
    void writerIsProxied() {
        assertThat(AopUtils.isAopProxy(cacheWriter))
                .withFailMessage("쓰기 빈이 프록시가 아니면 전파 속성은 선언만 남고 동작하지 않는다.")
                .isTrue();
    }

    @Test
    @DisplayName("캐시 서비스는 스스로 캐시를 적지 않는다")
    void cacheServiceDoesNotWriteByItself() throws IOException {
        assertThat(CACHE_SERVICE_SOURCE)
                .withFailMessage("캐시 서비스 소스를 찾지 못했다. 경로가 바뀌었으면 이 테스트도 함께 옮겨야 한다: %s",
                        CACHE_SERVICE_SOURCE.toAbsolutePath())
                .exists();

        String source = Files.readString(CACHE_SERVICE_SOURCE, StandardCharsets.UTF_8);
        String body = source.substring(source.indexOf("public class ExternalApiCacheService"));

        assertThat(body)
                .withFailMessage("캐시 저장을 다시 이 클래스로 옮기면 내부 호출이라 전파 속성이 무시된다. "
                        + "쓰기는 ExternalApiCacheWriter 에 둔다(#85).")
                .doesNotContain("cacheRepository.save")
                .doesNotContain(".refresh(");
    }

    private static Method writeMethod(String name) throws NoSuchMethodException {
        return ExternalApiCacheWriter.class.getMethod(name,
                com.mamoki.tour.global.enums.ApiProvider.class, String.class, String.class,
                java.time.LocalDateTime.class, java.time.LocalDateTime.class);
    }
}
