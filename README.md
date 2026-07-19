# Blitz

Blitz는 Java 25와 Spring Boot 4.1로 만든 작은 게시판 애플리케이션입니다. Thymeleaf 화면, OAuth 2.0 로그인(Google/Naver), 작성자 권한 검사, 낙관적 잠금, 페이지네이션, MariaDB/Flyway 기반 영속성을 포함합니다.

## 요구 사항

- JDK 25
- Docker와 Docker Compose(로컬 MariaDB를 컨테이너로 실행할 때)

전역 Gradle 설치는 필요하지 않습니다. 저장소에 포함된 Gradle Wrapper를 사용합니다.

## 먼저 테스트하기

테스트는 `test` 프로필과 인메모리 H2를 사용하므로 MariaDB나 OAuth 자격 증명 없이 실행할 수 있습니다.

Windows PowerShell:

```powershell
.\gradlew.bat test
```

macOS/Linux:

```shell
./gradlew test
```

컴파일, 테스트, 패키징을 모두 확인하려면 `test` 대신 `clean build`를 실행합니다.

## 로컬 실행

### 1. 로컬 설정 파일 준비

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

macOS/Linux:

```shell
cp .env.example .env
```

`.env`는 Git에서 제외됩니다. 애플리케이션은 다음 표준 Spring 설정 import를 통해 파일이 있을 때만 읽습니다.

```yaml
spring:
  config:
    import: optional:file:./.env[.properties]
```

따라서 DB/OAuth 값을 읽기 위한 별도 dotenv 로더는 필요하지 않습니다. 운영체제 환경변수는 `.env` 값보다 우선합니다. 이 파일은 Java properties 문법으로 읽히므로 값에 따옴표를 붙이지 말고 단순한 `KEY=value` 형식을 사용하세요. 실행 프로필은 아래처럼 셸에서 명시적으로 선택합니다.

### 2. MariaDB 실행

```shell
docker compose up -d
```

컨테이너 상태가 `healthy`가 되면 애플리케이션을 실행합니다.

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

macOS/Linux:

```shell
./gradlew bootRun
```

애플리케이션은 [http://localhost:8080](http://localhost:8080), 상태 확인은 [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)에서 할 수 있습니다.

MariaDB만 종료하려면 다음 명령을 사용합니다. 명명된 볼륨의 데이터는 유지됩니다.

```shell
docker compose down
```

## 환경변수

| 이름 | 용도 | 로컬 예시 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 선택적 Spring profile override. IntelliJ와 bootRun은 기본으로 `local`을 사용합니다. | `local` |
| `MARIADB_ROOT_PASSWORD` | Compose가 생성하는 MariaDB root 암호 | `change-me-root` |
| `MARIADB_DATABASE` | Compose가 생성하는 데이터베이스 | `blitz` |
| `MARIADB_USERNAME` | Compose와 애플리케이션의 DB 사용자 | `blitz` |
| `MARIADB_PASSWORD` | Compose와 애플리케이션의 DB 암호 | `change-me` |
| `MARIADB_HOST_PORT` | localhost에 바인딩할 MariaDB 포트 | `13306` |
| `MARIADB_URL` | 애플리케이션 JDBC URL | `jdbc:mariadb://localhost:13306/blitz` |
| `GOOGLE_CLIENT_ID` | Google OAuth 클라이언트 ID | 개발자 콘솔에서 발급 |
| `GOOGLE_CLIENT_SECRET` | Google OAuth 클라이언트 보안 비밀 | 개발자 콘솔에서 발급 |
| `NAVER_CLIENT_ID` | Naver OAuth 클라이언트 ID | 개발자 센터에서 발급 |
| `NAVER_CLIENT_SECRET` | Naver OAuth 클라이언트 보안 비밀 | 개발자 센터에서 발급 |

OAuth ID와 secret은 `.env`에 쌍으로 입력합니다. 값이 없으면 로컬 화면 확인용 자리표시자를 사용하므로 외부 OAuth 로그인은 동작하지 않습니다. `.env`와 `secret/`은 Git에서 제외되며 실제 자격 증명 값은 소스, 로그, 오류 메시지에 출력하지 마세요.

Google/Naver JSON 내보내기 파일에서 값을 옮길 때는 파일을 각각 `secret/google-oauth.json`, `secret/naver-oauth.json`으로 저장한 뒤 다음 명령을 실행할 수 있습니다. 스크립트는 `web.client_id`와 `web.client_secret`만 `.env`로 옮기며 값을 화면에 출력하지 않습니다.

```powershell
.\scripts\import-oauth-secrets.ps1
```

로컬 OAuth 클라이언트에는 다음 콜백 URL을 등록합니다.

- Google: `http://localhost:8080/login/oauth2/code/google`
- Naver: `http://localhost:8080/login/oauth2/code/naver`

## 프로필과 데이터베이스

기본 프로필은 `local`입니다. IntelliJ Run Application과 `bootRun`은 별도 VM option 없이 MariaDB, Flyway, OAuth, `.env` 설정을 로드합니다. 테스트 클래스는 필요한 경우 `test` 프로필을 직접 활성화합니다.

| 프로필 | 데이터베이스 | 스키마 관리 | 용도 |
| --- | --- | --- | --- |
| `local` | MariaDB | Flyway 적용 후 Hibernate `validate` | 로컬 실행 |
| `test` | 인메모리 H2 | 테스트 전용 초기화 | 자동화 테스트 |

도메인 및 Spring Session 스키마는 `src/main/resources/db/migration`의 Flyway 마이그레이션으로 관리합니다. 영속 환경의 테이블을 Hibernate가 임의로 생성하거나 변경하지 않습니다.

사용자 ID 기반 작성자 권한으로 전환하는 V10 마이그레이션은 이전 형식으로 직렬화된 로그인 세션을 한 번 비웁니다. 배포 후 기존 사용자는 다시 로그인해야 하며, 게시글이나 사용자 데이터에는 영향을 주지 않습니다.

## 화면과 접근 권한

| 경로 | 접근 | 설명 |
| --- | --- | --- |
| `GET /` | 공개 | 페이지네이션된 게시글 목록 |
| `GET /posts/{id}` | 공개 | 게시글 상세 |
| `GET /posts/save` | 로그인 | 게시글 등록 화면 |
| `GET /posts/{id}/edit` | 작성자 | 게시글 수정/삭제 화면 |
| `POST /logout` | 로그인 | 로그아웃 |
| `GET /actuator/health` | 공개 | 상세 정보를 숨긴 상태 확인 |

새 OAuth 사용자는 일반 사용자 권한을 받습니다. 화면에서 버튼을 숨기는 것과 별개로 서버가 모든 수정/삭제 요청의 로그인 여부, 역할, 작성자, CSRF 토큰을 다시 검증합니다.

## 게시글 API

| 메서드와 경로 | 접근 | 성공 응답 | 설명 |
| --- | --- | --- | --- |
| `POST /api/v1/posts` | 로그인 | `201 Created` | 게시글 등록 |
| `GET /api/v1/posts/{id}` | 공개 | `200 OK` | 게시글 한 건 조회 |
| `GET /api/v1/posts?page=0&size=10` | 공개 | `200 OK` | 게시글 페이지 조회 |
| `PUT /api/v1/posts/{id}` | 작성자 | `200 OK` | 게시글 수정 |
| `DELETE /api/v1/posts/{id}?version={version}` | 작성자 | `204 No Content` | 게시글 삭제 |

등록 요청 본문:

```json
{
  "title": "제목",
  "content": "내용"
}
```

수정 요청은 사용자가 편집을 시작한 시점의 `version`을 함께 보냅니다.

```json
{
  "title": "수정한 제목",
  "content": "수정한 내용",
  "version": 3
}
```

삭제도 같은 버전을 쿼리 매개변수로 전달합니다. 다른 요청이 먼저 게시글을 변경했다면 서버는 `409 Conflict`를 반환하므로 오래 열린 화면이 최신 내용을 덮어쓰지 않습니다.

상태를 변경하는 API는 로그인 세션과 CSRF 토큰이 모두 필요합니다. 브라우저 화면은 Thymeleaf가 제공한 토큰과 헤더 이름을 자동으로 전송합니다. 직접 호출하는 클라이언트는 세션 쿠키와 함께 서버가 발급한 CSRF 토큰을 해당 헤더로 보내야 합니다.

대표 오류 상태는 다음과 같습니다.

- `400 Bad Request`: 필수값 누락, 공백, 길이 제한 위반 등 입력 오류
- `401 Unauthorized`: 로그인하지 않았거나 세션 만료
- `403 Forbidden`: 역할 또는 작성자 권한 부족, CSRF 검증 실패
- `404 Not Found`: 존재하지 않는 게시글
- `409 Conflict`: 오래된 버전으로 수정 또는 삭제 시도

## 프로젝트 구조

```text
src/main/java/com/blitz/              애플리케이션, 도메인, 서비스, 웹 계층
src/main/resources/templates/         Thymeleaf 화면
src/main/resources/static/            로컬 CSS와 JavaScript
src/main/resources/db/migration/      MariaDB/Flyway 마이그레이션
src/test/                              자동화 테스트와 test 프로필 설정
```

## 문제 해결

- 애플리케이션이 DB에 연결하지 못하면 `docker compose ps`로 MariaDB 상태를 확인하고 `.env`의 URL, 사용자, 암호가 Compose 설정과 일치하는지 확인하세요.
- OAuth 로그인 화면에서 오류가 나면 개발자 콘솔의 콜백 URL과 `.env`의 클라이언트 값이 일치하는지 확인하세요.
- 스키마 검증에 실패하면 로그에 표시된 Flyway 버전을 확인하세요. 기존 마이그레이션을 수정하기보다 새 버전의 마이그레이션을 추가해야 합니다.
