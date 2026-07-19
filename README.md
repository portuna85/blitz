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

기본 프로필은 인메모리 H2 데이터베이스를 사용합니다. Thymeleaf는 의존성과 자동 구성만 제공하며 샘플 화면이나 HTTP 엔드포인트는 추가하지 않았습니다.

## MariaDB 프로필

`mariadb` 프로필은 다음 환경변수를 필수로 사용합니다.

- `MARIADB_URL`: 예) `jdbc:mariadb://localhost:3306/blitz`
- `MARIADB_USERNAME`
- `MARIADB_PASSWORD`

Windows PowerShell 예시:

```powershell
$env:MARIADB_URL = "jdbc:mariadb://localhost:3306/blitz"
$env:MARIADB_USERNAME = "blitz"
$env:MARIADB_PASSWORD = "change-me"
.\gradlew.bat bootRun --args="--spring.profiles.active=mariadb"
```

MariaDB에서는 Spring Session 테이블을 기본적으로 자동 생성하지 않습니다. 운영 환경에서는 마이그레이션으로 테이블을 관리하고, 폐기 가능한 개발 데이터베이스에서만 `SPRING_SESSION_JDBC_INITIALIZE_SCHEMA=always`를 설정합니다.
