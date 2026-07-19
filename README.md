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

프로필을 지정하지 않으면 `spring.profiles.default: local` 설정에 따라 `local` 프로필이 자동으로 활성화됩니다. `local` 프로필은 다음 환경변수로 로컬에 떠 있는 MariaDB에 접속합니다 (필수).

- `MARIADB_URL`: 예) `jdbc:mariadb://localhost:3306/blitz`
- `MARIADB_USERNAME`
- `MARIADB_PASSWORD`

Windows PowerShell 예시:

```powershell
$env:MARIADB_URL = "jdbc:mariadb://localhost:3306/blitz"
$env:MARIADB_USERNAME = "blitz"
$env:MARIADB_PASSWORD = "change-me"
.\gradlew.bat bootRun
```

`local` 프로필은 개발 편의를 위해 `spring.jpa.hibernate.ddl-auto=update`와 `spring.session.jdbc.initialize-schema=always`를 사용합니다 (스키마를 매번 자동으로 맞춰줌). 아직 Flyway 등 마이그레이션 도구가 없어서 운영 환경에는 맞지 않는 설정이며, 폐기 가능한 로컬/개발용 데이터베이스에서만 사용하세요.

자동화 테스트(`./gradlew test`)는 `local`과 무관하게 `test` 프로필(`src/test/resources/application-test.yml`)을 명시적으로 사용하며, 인메모리 H2를 사용해 MariaDB 없이도 실행됩니다.
