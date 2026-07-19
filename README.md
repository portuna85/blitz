# blitz

Java 25, Spring Boot 4.1.0, Gradle 9.6.1 기반의 Spring MVC 애플리케이션입니다.

## 요구 사항

- JDK 25
- 전역 Gradle 설치는 필요하지 않습니다. 프로젝트의 Gradle Wrapper를 사용합니다.

## 빌드와 실행

Windows:

```powershell
.\gradlew.bat clean build
.\gradlew.bat bootRun
```

macOS/Linux:

```shell
./gradlew clean build
./gradlew bootRun
```

Thymeleaf 템플릿(`src/main/resources/templates`)과 게시글 CRUD용 HTTP 엔드포인트(`/`, `/posts/**`, `/api/v1/posts/**`)가 이미 포함되어 있습니다.

## 프로필

프로필을 지정하지 않으면 `spring.profiles.default: local` 설정에 따라 `local` 프로필이 자동으로 활성화됩니다. `local` 프로필은 MariaDB에 접속하며, 스키마는 Flyway 마이그레이션(`src/main/resources/db/migration`)이 관리합니다 (`spring.jpa.hibernate.ddl-auto=validate`로 엔티티와 스키마 일치만 검증).

자동화 테스트(`./gradlew test`)는 `local`과 무관하게 `test` 프로필(`src/test/resources/application-test.yml`)을 명시적으로 사용하며, 인메모리 H2를 사용해 MariaDB 없이도 실행됩니다 (Flyway도 비활성화되어 있습니다).

## 로컬 개발 환경 준비 (.env + Docker Compose)

1. 환경변수 템플릿을 복사합니다.

   ```shell
   cp .env.example .env
   ```

2. 필요하면 `.env`를 열어 값을 채웁니다. 기본값만으로도 로컬 MariaDB는 바로 동작하며, Google/Naver 로그인을 실제로 테스트하려면 `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`/`NAVER_CLIENT_ID`/`NAVER_CLIENT_SECRET`을 채워야 합니다. 비워두면 자리표시자 값으로 기동은 되지만 로그인은 동작하지 않습니다.

3. Docker Compose로 로컬 MariaDB를 띄웁니다.

   ```shell
   docker compose up -d
   ```

4. 애플리케이션을 실행합니다. `bootRun`은 `.env` 파일을 자동으로 읽어 환경변수로 주입하므로 별도로 `$env:`/`export`를 설정할 필요가 없습니다.

   ```shell
   ./gradlew bootRun
   ```

`.env`는 `.gitignore`에 포함되어 커밋되지 않습니다. `.env.example`만 저장소에 커밋된 템플릿입니다.
