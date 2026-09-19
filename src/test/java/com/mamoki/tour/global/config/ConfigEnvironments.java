package com.mamoki.tour.global.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * 설정 파일만 실은 환경을 세운다.
 *
 * <p>"이 파일만 있어도 되는가" 를 확인하려면 <b>운영체제 환경변수가 섞이지 않아야</b> 한다.
 * 평범한 {@link StandardEnvironment} 는 시스템 환경과 시스템 프로퍼티를 끼고 태어나므로,
 * 테스트를 돌리는 사람이 {@code DB_USERNAME} 을 내보내 두었다는 이유만으로 빈 파일도
 * 통과해 버린다. 그 두 소스를 떼고 시작한다.
 */
final class ConfigEnvironments {

    private ConfigEnvironments() {
    }

    static ConfigurableEnvironment isolated(String... activeProfiles) {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();

        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);

        if (activeProfiles.length > 0) {
            environment.setActiveProfiles(activeProfiles);
        }

        return environment;
    }

    /** 설정 파일을 가장 낮은 우선순위로 얹는다. */
    static void addYaml(ConfigurableEnvironment environment, String classpathLocation) {
        try {
            for (PropertySource<?> source :
                    new YamlPropertySourceLoader()
                            .load(classpathLocation, new ClassPathResource(classpathLocation))) {
                environment.getPropertySources().addLast(source);
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** 환경변수 자리에 놓을 값을 가장 높은 우선순위로 얹는다. */
    static void addValues(ConfigurableEnvironment environment, String name, Map<String, Object> values) {
        environment.getPropertySources().addFirst(new MapPropertySource(name, values));
    }

    /**
     * {@code .env} 계열 파일을 앱이 읽는 방식(properties)으로 읽는다.
     *
     * <p>값이 빈 줄도 그대로 담는다. 그 줄이 어떻게 취급되는지가 확인 대상이다.
     */
    static Map<String, Object> readEnvFile(Path file) {
        Properties properties = new Properties();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }

        Map<String, Object> values = new LinkedHashMap<>();
        properties.forEach((name, value) -> values.put(String.valueOf(name), value));

        return values;
    }
}
