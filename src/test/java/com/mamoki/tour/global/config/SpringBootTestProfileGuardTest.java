package com.mamoki.tour.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code @SpringBootTest} 가 반드시 {@code test} 프로파일로 뜨는지 지킨다.
 *
 * <p>프로파일을 지정하지 않으면 {@code application.yaml} 의 기본값인 {@code local} 로 떠서
 * 개발 스키마({@code DB_NAME}) 에 붙는다. 그 프로파일은 {@code ddl-auto: update} 라
 * 테스트 한 번이 개발 스키마에 DDL 과 {@code data.sql} 을 흘려보내고, {@code DB_NAME} 이
 * 운영 스키마를 가리키면 운영 테이블에 그대로 나간다.
 *
 * <p>{@link TestProfileDatabaseTest} 는 이것을 잡지 못한다. 그 테스트는 자신이 뜬
 * {@code test} 프로파일 컨텍스트의 접속 대상만 보기 때문에, 프로파일을 빠뜨린 다른
 * 클래스는 아예 시야에 없다. 그래서 규칙 자체를 클래스 목록에 대고 확인한다.
 *
 * <p>클래스패스를 직접 훑는다. 새 의존성(ArchUnit 등)을 들이지 않으려는 것이고, 대상이
 * 이 저장소의 테스트 클래스뿐이라 이 정도로 충분하다.
 */
class SpringBootTestProfileGuardTest {

    private static final String REQUIRED_PROFILE = "test";

    @Test
    @DisplayName("모든 @SpringBootTest 가 test 프로파일을 켠다")
    void everySpringBootTestActivatesTestProfile() throws Exception {
        List<Class<?>> springBootTests = testClasses().stream()
                .filter(SpringBootTestProfileGuardTest::isSpringBootTest)
                .toList();

        // 스캔이 빗나가면 위반이 없는 것처럼 보인다. 그 조용한 통과를 먼저 막는다.
        assertThat(springBootTests)
                .withFailMessage("@SpringBootTest 클래스를 하나도 찾지 못했다. 클래스 스캔 경로를 확인한다.")
                .isNotEmpty();

        List<String> missingProfile = springBootTests.stream()
                .filter(testClass -> !activatesTestProfile(testClass))
                .map(Class::getName)
                .sorted()
                .toList();

        assertThat(missingProfile)
                .withFailMessage(
                        "@SpringBootTest 에는 @ActiveProfiles(\"%s\") 가 있어야 한다. "
                                + "없으면 기본 local 프로파일로 개발 스키마에 붙는다. 빠진 클래스: %s",
                        REQUIRED_PROFILE, missingProfile)
                .isEmpty();
    }

    private static boolean isSpringBootTest(Class<?> testClass) {
        return MergedAnnotations.from(testClass, SearchStrategy.TYPE_HIERARCHY)
                .isPresent(SpringBootTest.class);
    }

    private static boolean activatesTestProfile(Class<?> testClass) {
        ActiveProfiles activeProfiles =
                AnnotatedElementUtils.findMergedAnnotation(testClass, ActiveProfiles.class);

        if (activeProfiles == null) {
            return false;
        }

        // value 와 profiles 는 서로의 별칭이라 한쪽만 채워도 병합된 값에 함께 들어온다.
        return Arrays.asList(activeProfiles.value()).contains(REQUIRED_PROFILE);
    }

    /** 컴파일된 테스트 클래스를 전부 읽어 온다. */
    private static List<Class<?>> testClasses() throws Exception {
        Path root = Path.of(SpringBootTestProfileGuardTest.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());

        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".class"))
                    .map(path -> className(root, path))
                    .flatMap(name -> load(name).stream())
                    .toList();
        }
    }

    private static String className(Path root, Path classFile) {
        String relative = root.relativize(classFile).toString().replace(File.separatorChar, '/');

        return relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
    }

    /**
     * 읽지 못하는 클래스는 건너뛴다. 초기화는 하지 않는다 — 규칙을 확인하려고 남의 테스트
     * 클래스의 정적 초기화를 돌릴 이유가 없다.
     */
    private static Optional<Class<?>> load(String name) {
        try {
            return Optional.of(Class.forName(
                    name, false, SpringBootTestProfileGuardTest.class.getClassLoader()));
        } catch (Throwable unreadable) {
            return Optional.empty();
        }
    }
}
