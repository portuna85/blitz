package com.blitz.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 프로젝트 루트의 .env 파일을 읽어 Spring Environment에 property source로 추가한다.
 * ./gradlew bootRun뿐 아니라 IDE의 직접 실행, java -jar 등 어떤 방식으로 애플리케이션을
 * 기동하더라도 동일하게 동작하도록 Gradle 태스크가 아닌 애플리케이션 부트스트랩
 * 단계에서 처리한다.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "dotenv";
    private static final Path ENV_FILE = Path.of(".env");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Files.exists(ENV_FILE)) {
            return;
        }

        Map<String, Object> values = readEnvFile();
        if (!values.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
        }
    }

    private Map<String, Object> readEnvFile() {
        List<String> lines;
        try {
            lines = Files.readAllLines(ENV_FILE);
        } catch (IOException e) {
            throw new UncheckedIOException("'" + ENV_FILE + "' 파일을 읽는 중 오류가 발생했습니다.", e);
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                continue;
            }

            int separatorIndex = line.indexOf('=');
            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();

            // 빈 값은 건너뛴다 - application-local.yml의 ${GOOGLE_CLIENT_ID:placeholder-...}
            // 같은 기본값이 계속 적용되도록 하기 위함 (빈 문자열을 주입하면 기본값이 무시된다).
            if (!value.isEmpty()) {
                values.put(key, value);
            }
        }

        return values;
    }
}
