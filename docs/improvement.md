# 댓글 기능 구현 계획

## 1. 목적과 범위

이 문서는 Blitz 게시판에 댓글과 1단계 답글 기능을 추가하기 위한 구현 명세다. 현재 저장소의 Git 추적 파일 75개를 기준으로 애플리케이션 구조와 변경 영향을 분석했으며, 실제 구현 전에 데이터 모델, API, 권한, 화면 동작과 검증 기준을 고정한다.

이번 문서 작성 작업에서는 애플리케이션 코드, 설정, 테스트, 마이그레이션을 변경하지 않는다. 향후 댓글 기능 구현도 아래에 명시한 파일과 책임만 수정하며, 기존에 적용된 Flyway 마이그레이션 `V1`부터 `V10`까지는 체크섬 보존을 위해 주석을 포함해 수정하지 않는다.

### 기준선

- 프로젝트: Java 25, Spring Boot 4.1, Spring MVC, Spring Security OAuth2, Spring Data JPA, Thymeleaf, Flyway, MariaDB
- 인증: Google/Naver OAuth2와 JDBC 세션
- 게시글 읽기: 공개
- 게시글 쓰기: 로그인 및 CSRF 필요
- 소유권: 변경 가능한 이메일이 아니라 `SessionUser.userId`로 판단
- 동시성: 게시글의 `@Version`과 요청 버전을 사용한 낙관적 잠금
- 프론트엔드: Thymeleaf 최초 렌더링과 공통 JavaScript의 `fetch` 요청
- 분석 시점 테스트: `./gradlew test --rerun-tasks` 성공

## 2. 기능 요구사항

### 2.1 조회와 권한

- 댓글과 답글은 인증 없이 조회할 수 있다.
- 댓글과 답글의 작성, 수정, 삭제에는 Spring Security 인증과 유효한 CSRF 토큰이 모두 필요하다.
- 작성자는 `SessionUser.userId`와 댓글의 `authorUserId`가 같은 경우에만 수정하거나 삭제할 수 있다.
- 작성 당시 표시 이름은 스냅샷으로 저장하되 권한 판정에는 사용하지 않는다.
- 관리자 권한, 신고, 차단, 좋아요, 알림 기능은 이번 범위에 포함하지 않는다.

### 2.2 계층과 정렬

- 상위 댓글과 그에 속한 1단계 답글만 지원한다.
- `parentId`가 없으면 상위 댓글이고, 있으면 해당 상위 댓글의 답글이다.
- 답글을 부모로 지정하거나 삭제된 댓글을 부모로 지정할 수 없다.
- 부모 댓글은 같은 게시글에 속해야 한다. 다른 게시글의 댓글 ID를 부모로 전달하면 존재하지 않는 댓글과 동일하게 처리한다.
- 상위 댓글은 `createdDate`, `id` 오름차순으로 정렬하고 20개씩 페이지 처리한다.
- 각 상위 댓글의 답글도 `createdDate`, `id` 오름차순으로 정렬한다.
- 페이지 크기는 20으로 고정하고 클라이언트가 임의로 늘리지 못하게 한다.

### 2.3 입력과 삭제

- 댓글과 답글은 같은 본문 규칙을 사용한다.
- 본문은 `null`이 아니어야 하고, 앞뒤 공백 제거 후 1자 이상 1,000자 이하여야 한다.
- 삭제는 구조 보존을 위한 tombstone 방식으로 처리한다.
- 삭제 시 본문과 작성자 표시 이름을 빈 값으로 덮어쓰고 `deleted=true`, `deletedAt=현재 시각`으로 변경한다.
- 내부 무결성과 감사 목적의 `authorUserId`, `postId`, `parentId`, 생성 시각은 유지하지만 응답으로 작성자 ID를 노출하지 않는다.
- 삭제된 댓글의 공개 응답은 `content=null`, `author=null`, `owner=false`로 마스킹한다.
- 삭제된 댓글은 수정, 재삭제, 새 답글 작성을 허용하지 않는다.
- 삭제된 답글도 tombstone으로 남겨 정렬과 화면 맥락을 보존한다.
- 게시글이 삭제되면 해당 게시글의 활성 댓글과 tombstone을 모두 물리적으로 삭제한다.

### 2.4 개수 표시

- 게시글 상세 화면에만 활성 댓글 수를 표시한다.
- 활성 댓글 수에는 삭제되지 않은 상위 댓글과 답글을 모두 포함한다.
- 삭제된 tombstone은 개수에서 제외한다.
- 게시글 목록 응답과 목록 프로젝션에는 댓글 수를 추가하지 않는다.

## 3. 데이터 모델

새 Flyway 파일 `V11__create_comments_table.sql`에서 다음 구조를 생성한다. 기존 마이그레이션은 수정하지 않는다.

| 열 | 형식 | 규칙 |
| --- | --- | --- |
| `id` | `BIGINT` | 자동 증가 기본 키 |
| `post_id` | `BIGINT` | 필수, `posts.id` 외래 키 |
| `parent_id` | `BIGINT` | 상위 댓글은 `NULL`, 답글은 부모 댓글 ID |
| `author_user_id` | `BIGINT` | 필수, 권한 판정용 사용자 ID |
| `author` | `VARCHAR(255)` | 작성자 표시 이름 스냅샷, 삭제 시 빈 문자열 |
| `content` | `VARCHAR(1000)` | 댓글 본문, 삭제 시 빈 문자열 |
| `deleted` | `BOOLEAN` | 기본값 `FALSE` |
| `deleted_at` | `DATETIME(6)` | 활성 댓글은 `NULL` |
| `created_date` | `DATETIME(6)` | 필수 |
| `modified_date` | `DATETIME(6)` | 필수 |
| `version` | `BIGINT` | 필수, 기본값 `0`, 낙관적 잠금 |

추가 제약과 인덱스는 다음과 같이 고정한다.

- `post_id`는 `posts(id)`를 참조하고 `ON DELETE CASCADE`를 적용한다.
- `parent_id`는 `comments(id)`를 참조하고 `ON DELETE CASCADE`를 적용한다. 개별 댓글은 물리 삭제하지 않지만 게시글 삭제 시 부모와 답글을 함께 정리한다.
- `author_user_id`는 `users(id)`를 참조하고 `ON DELETE RESTRICT`를 적용해 기존 게시글 소유권과 같은 안정적인 사용자 식별자로 유지한다.
- 애플리케이션 서비스는 부모가 같은 게시글의 상위 댓글이며 활성 상태인지 트랜잭션 안에서 검증한다.
- 상위 댓글 페이지 조회용 `(post_id, parent_id, created_date, id)` 복합 인덱스를 생성한다.
- 작성자 관련 유지보수를 위해 `author_user_id` 인덱스를 생성한다.
- 엔티티는 기존 도메인 스타일에 맞춰 연관 객체 대신 `Long postId`, `Long parentId`, `Long authorUserId`를 보유한다.
- `Comments`는 `BaseTimeEntity`를 상속하고 `@Version Long version`을 선언한다.

## 4. 서버 설계

### 4.1 저장소와 서비스

`CommentsRepository`는 다음 조회를 제공한다.

- 게시글별 상위 댓글 페이지 조회
- 한 페이지에 포함된 상위 댓글 ID 목록으로 모든 답글 일괄 조회
- 게시글별 삭제되지 않은 댓글과 답글 수 집계
- 게시글 ID와 댓글 ID를 함께 사용하는 단건 조회
- 게시글 삭제 전 해당 게시글 댓글 전체 삭제

목록 서비스는 상위 댓글 페이지 조회 1회와 답글 일괄 조회 1회로 응답을 조립해 N+1 조회를 방지한다. tombstone도 페이지와 답글 구조에 포함하지만 `activeCount`에서는 제외한다.

생성 서비스는 게시글 존재 여부와 로그인 사용자를 검사한다. `parentId`가 있으면 부모가 같은 게시글의 활성 상위 댓글인지 확인한다. 상위 댓글 생성 후 전체 상위 댓글 수를 기준으로 `targetPage=(상위 댓글 수-1)/20`을 계산한다. 답글 생성 시에는 부모 상위 댓글이 속한 페이지를 `targetPage`로 반환한다.

수정과 삭제 서비스는 댓글 조회, 로그인 검사, 작성자 검사, 삭제 상태 검사, 요청 버전 검사를 순서대로 수행한다. 명시적 버전 검사는 일관된 오류 메시지를 제공하고, 검사 이후 발생한 실제 동시 갱신은 JPA의 낙관적 잠금 예외를 동일한 `409` 응답으로 변환한다.

게시글 삭제 서비스는 댓글을 먼저 삭제한 뒤 게시글을 삭제한다. 데이터베이스 외래 키의 cascade도 방어 수단으로 유지한다.

### 4.2 API 계약

#### 댓글 페이지 조회

`GET /api/v1/posts/{postId}/comments?page={page}`

- 공개 API다.
- `page`는 0 이상이어야 하며 생략하면 0이다.
- 응답은 다음 구조를 사용한다.

```json
{
  "content": [
    {
      "comment": {
        "id": 10,
        "parentId": null,
        "content": "댓글 본문",
        "author": "작성자",
        "version": 0,
        "deleted": false,
        "owner": true,
        "createdDate": "2026-07-20T09:00:00",
        "modifiedDate": "2026-07-20T09:00:00"
      },
      "replies": []
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasPrevious": false,
  "hasNext": false,
  "activeCount": 1
}
```

`owner`는 현재 로그인 사용자를 기준으로 계산한다. 익명 요청과 tombstone에서는 항상 `false`다.

#### 댓글 또는 답글 작성

`POST /api/v1/posts/{postId}/comments`

```json
{
  "content": "새 댓글",
  "parentId": null
}
```

- `parentId=null`이면 상위 댓글, 숫자이면 답글이다.
- 성공 시 `201 Created`와 `/api/v1/posts/{postId}/comments/{commentId}` 형식의 `Location`을 반환한다.
- 응답 본문은 `{"comment": CommentResponse, "targetPage": 0}` 형식이다.

#### 댓글 수정

`PUT /api/v1/posts/{postId}/comments/{commentId}`

```json
{
  "content": "수정한 댓글",
  "version": 0
}
```

- 작성자만 호출할 수 있다.
- 성공 시 `200 OK`와 갱신된 `comment`, `targetPage`를 반환한다.

#### 댓글 삭제

`DELETE /api/v1/posts/{postId}/comments/{commentId}?version={version}`

- 작성자만 호출할 수 있다.
- tombstone 렌더링에 필요한 결과가 있으므로 `204`가 아니라 `200 OK`를 사용한다.
- 응답은 마스킹된 `comment`와 `targetPage`를 반환한다.

#### 오류 계약

기존 `ApiExceptionHandler.ApiError` 구조를 유지한다.

| 상황 | 상태 | 코드 |
| --- | --- | --- |
| 본문·경로·버전 검증 실패 | `400` | `validation_failed` |
| 답글을 부모로 지정하거나 삭제된 부모 지정 | `400` | `invalid_parent_comment` |
| 인증 또는 세션 만료 | `401` | `authentication_required` |
| 다른 사용자의 댓글 변경 | `403` | `access_denied` |
| 게시글 없음 | `404` | `post_not_found` |
| 댓글 없음 또는 다른 게시글의 댓글 | `404` | `comment_not_found` |
| 오래된 버전 또는 동시 갱신 | `409` | `comment_version_conflict` |

### 4.3 보안 설정

- `GET /api/v1/posts/{postId}/comments`를 명시적으로 공개한다.
- 댓글 API의 `POST`, `PUT`, `DELETE`는 기존 `/api/v1/**` 규칙에 따라 `ROLE_USER`를 요구한다.
- 상태 변경 요청의 CSRF 검증은 비활성화하지 않는다.
- 게시글 상세 페이지가 댓글 작성 화면도 담당하므로 CSRF 메타 태그를 렌더링한다.
- 댓글 응답에는 `authorUserId`와 사용자 이메일을 포함하지 않는다.

## 5. 화면과 JavaScript 설계

게시글 상세 컨트롤러는 `commentPage` 쿼리 매개변수를 받고 게시글과 첫 댓글 페이지를 함께 조회한다. 최초 HTML에는 다음 요소를 서버 렌더링한다.

- 활성 댓글 수
- 상위 댓글과 답글 목록
- 익명 사용자의 로그인 안내
- 로그인 사용자의 댓글 작성 폼
- 활성 상위 댓글에 대한 답글 작성 버튼
- 작성자에게만 보이는 수정·삭제 버튼
- 삭제 항목의 `삭제된 댓글입니다.` tombstone
- 이전·다음 페이지 컨트롤과 빈 상태

기존 `index.js`의 `apiRequest`, CSRF, 오류 메시지, busy 상태 처리 방식을 재사용한다. 댓글 DOM은 `textContent`와 요소 속성 API로 생성하며 사용자 입력을 `innerHTML`에 삽입하지 않는다.

- 상위 댓글 작성 후 응답의 `targetPage`를 조회해 새 댓글로 초점을 이동한다.
- 답글 작성 후 부모가 있는 `targetPage`를 다시 조회한다.
- 수정과 삭제 후 현재 페이지를 다시 조회한다.
- 비동기 페이지 이동 시 `commentPage` 쿼리를 History API로 갱신하고 `popstate`에서 해당 페이지를 복원한다.
- 요청 중에는 관련 폼과 버튼을 비활성화하고 `aria-busy`를 설정한다.
- 성공 메시지는 `role=status`, 오류는 `role=alert`로 알리고 해당 메시지에 초점을 이동한다.
- 댓글 작성·수정 폼에도 HTML `required`, `maxlength=1000`을 적용하되 서버 검증을 최종 기준으로 삼는다.

## 6. 테스트 계획

### 도메인과 저장소

- 상위 댓글과 답글 저장, 오래된 순 정렬
- 공백 및 1,000자 초과 본문 거부
- 작성자 ID 소유권 판정
- 수정 시 버전 증가와 동시에 수정한 엔티티의 낙관적 잠금 충돌
- 삭제 시 원문·표시 이름 제거와 tombstone 필드 설정
- 게시글 삭제 시 댓글과 답글 전체 정리
- 상위 댓글 21개 이상에서 20개 페이지 경계와 답글 일괄 조회
- 삭제되지 않은 댓글과 답글만 `activeCount`에 포함

### API와 보안

- 익명 사용자의 댓글 페이지 조회 성공
- 익명 작성 요청 `401`, 인증됐지만 CSRF가 없는 요청 `403`
- 렌더링된 실제 CSRF 메타 값을 사용한 작성 성공
- 같은 이메일이어도 사용자 ID가 다르면 수정·삭제 `403`
- 다른 게시글의 부모 댓글 `404`
- 답글을 부모로 한 요청과 삭제된 부모 요청 `400`
- 없는 댓글 `404`, 오래된 버전의 수정·삭제 `409`
- tombstone 응답에서 원문, 표시 이름, 내부 사용자 ID가 노출되지 않음
- 상위 댓글 생성 응답의 `targetPage`와 답글 생성 응답의 부모 페이지 확인

### MVC와 화면

- 공개 상세 페이지에 첫 댓글 페이지와 활성 댓글 수 렌더링
- 익명·로그인·작성자 상태별 폼과 작업 버튼 노출
- 삭제 댓글 placeholder 및 기존 답글 유지
- 댓글 페이지 링크와 CSRF 메타 태그 렌더링
- 빈 댓글 상태와 20개 초과 페이지 탐색

## 7. 파일별 영향 분석

표의 `변경`은 향후 기능 구현 시 예상되는 조치다. 이번 문서 작성에서는 아래 파일을 실제로 수정하지 않는다.

### 7.1 프로젝트 루트와 빌드

| 파일 | 현재 역할과 분석 | 댓글 기능 조치 |
| --- | --- | --- |
| `.env.example` | MariaDB와 OAuth 로컬 변수 예시를 제공한다. | 변경 없음. 댓글은 새 환경 변수가 필요 없다. |
| `.gitattributes` | LF/CRLF와 JAR 바이너리 속성을 고정한다. | 변경 없음. |
| `.gitignore` | 빌드, IDE, 로그, 비밀 파일을 제외한다. | 변경 없음. |
| `README.md` | 실행, 인증, API, DB 운용 방법을 설명한다. | 구현 완료 시 댓글 API와 화면 권한을 추가 문서화한다. |
| `build.gradle.kts` | Spring MVC/JPA/Security/Validation/Flyway 의존성과 Java 25를 설정한다. | 기존 의존성으로 구현 가능하므로 변경 없음. |
| `docker-compose.yml` | 로컬 MariaDB 11과 영속 볼륨을 구성한다. | 변경 없음. V11은 기존 DB에 Flyway로 적용한다. |
| `gradle/wrapper/gradle-wrapper.jar` | Gradle Wrapper 실행 바이너리다. | 생성 바이너리이므로 분석·수정 대상에서 제외한다. |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 9.6.1 배포본과 체크섬을 고정한다. | 변경 없음. |
| `gradlew` | Unix 계열 Gradle Wrapper 생성 스크립트다. | 생성 파일이므로 수정하지 않는다. |
| `gradlew.bat` | Windows Gradle Wrapper 생성 스크립트다. | 생성 파일이므로 수정하지 않는다. |
| `scripts/import-oauth-secrets.ps1` | OAuth JSON을 검증하고 `.env`에 안전하게 병합한다. | 댓글 기능과 무관하므로 변경 없음. |
| `settings.gradle.kts` | 루트 프로젝트 이름을 `blitz`로 설정한다. | 변경 없음. |

### 7.2 애플리케이션과 설정

| 파일 | 현재 역할과 분석 | 댓글 기능 조치 |
| --- | --- | --- |
| `src/main/java/com/blitz/Application.java` | Spring Boot 진입점이다. | 변경 없음. |
| `src/main/java/com/blitz/config/JpaConfig.java` | JPA 감사 시각을 활성화한다. | 변경 없음. 댓글 엔티티가 `BaseTimeEntity`를 재사용한다. |
| `src/main/java/com/blitz/config/WebConfig.java` | `LoginUserArgumentResolver`를 MVC에 등록한다. | 변경 없음. 댓글 컨트롤러도 `@LoginUser`를 사용한다. |
| `src/main/java/com/blitz/config/auth/CustomOAuth2UserService.java` | OAuth 사용자를 제공자 ID로 생성·갱신하고 세션 DTO를 저장한다. | 변경 없음. |
| `src/main/java/com/blitz/config/auth/LoginUser.java` | 컨트롤러의 세션 사용자 주입 표시자다. | 변경 없음. |
| `src/main/java/com/blitz/config/auth/LoginUserArgumentResolver.java` | 새 세션을 만들지 않고 기존 세션의 사용자를 주입한다. | 변경 없음. 댓글 조회의 익명 `owner=false` 계산에 재사용한다. |
| `src/main/java/com/blitz/config/auth/SecurityConfig.java` | CSP, 공개 경로, API 인증 오류, OAuth 로그인과 로그아웃을 설정한다. | 댓글 GET 경로를 공개하고 쓰기는 기존 API 인증 규칙에 둔다. |
| `src/main/java/com/blitz/config/auth/dto/OAuthAttributes.java` | Google/Naver 응답을 내부 사용자 모델로 정규화하고 검증한다. | 변경 없음. |
| `src/main/java/com/blitz/config/auth/dto/SessionUser.java` | 세션에 안정적인 사용자 ID, 이름, 이메일을 직렬화한다. | 변경 없음. `userId`와 `name`을 댓글 소유권과 표시 이름에 사용한다. |
| `src/main/resources/application.yml` | 기본 local 프로필, 세션 쿠키, JPA와 Actuator 정책을 설정한다. | 변경 없음. |
| `src/main/resources/application-local.yml` | MariaDB, Flyway, JDBC 세션과 OAuth 로컬 설정을 제공한다. | 변경 없음. V11은 자동 적용된다. |
| `src/main/resources/logback-spring.xml` | 환경별 콘솔·구조화 파일 로그를 설정한다. | 변경 없음. 비정상 댓글 요청 원문은 로그에 남기지 않는다. |

### 7.3 사용자와 게시글 도메인

| 파일 | 현재 역할과 분석 | 댓글 기능 조치 |
| --- | --- | --- |
| `src/main/java/com/blitz/domain/BaseTimeEntity.java` | 생성·수정 시각을 JPA 감사 기능으로 채운다. | 댓글 엔티티가 상속한다. |
| `src/main/java/com/blitz/domain/user/Role.java` | `GUEST`, `USER`와 Spring Security 권한 키를 정의한다. | 변경 없음. 댓글 쓰기는 `USER`를 요구한다. |
| `src/main/java/com/blitz/domain/user/User.java` | OAuth 사용자와 제공자 식별자를 저장·검증한다. | 변경 없음. 댓글은 사용자 객체 연관 대신 ID 스냅샷을 저장한다. |
| `src/main/java/com/blitz/domain/user/UserRepository.java` | 제공자와 제공자 ID로 사용자를 조회한다. | 변경 없음. |
| `src/main/java/com/blitz/domain/posts/Posts.java` | 게시글 값 검증, 소유권, 수정과 `@Version`을 담당한다. | 직접 연관 컬렉션은 추가하지 않는다. 삭제는 서비스와 DB cascade로 댓글을 정리한다. |
| `src/main/java/com/blitz/domain/posts/PostsRepository.java` | 게시글 CRUD와 목록 프로젝션 페이지 조회를 제공한다. | 변경 없음. 게시글 존재 확인과 삭제에는 기존 메서드를 사용한다. |
| `src/main/java/com/blitz/domain/posts/PostsSummary.java` | 본문을 읽지 않는 게시글 목록 폐쇄형 프로젝션이다. | 댓글 수를 목록에 표시하지 않으므로 변경 없음. |
| `src/main/java/com/blitz/service/PostsService.java` | 게시글 CRUD, ID 소유권, 버전 검사, 페이지 크기 제한을 처리한다. | 삭제 트랜잭션에서 댓글 전체 삭제를 먼저 호출하도록 확장한다. |
| `src/main/java/com/blitz/service/exception/PostNotFoundException.java` | 게시글 없음 오류를 정의한다. | 댓글 생성·조회에서도 재사용한다. |
| `src/main/java/com/blitz/service/exception/PostVersionConflictException.java` | 게시글 버전 충돌을 정의한다. | 변경 없음. 댓글 전용 충돌 예외를 별도로 추가한다. |

### 7.4 웹 API와 DTO

| 파일 | 현재 역할과 분석 | 댓글 기능 조치 |
| --- | --- | --- |
| `src/main/java/com/blitz/web/ApiExceptionHandler.java` | 게시글 API의 검증·인증·권한·404·409 오류를 구조화한다. | 댓글 컨트롤러를 적용 범위에 추가하고 댓글 오류 코드를 매핑한다. |
| `src/main/java/com/blitz/web/HelloController.java` | 샘플 문자열과 DTO API를 제공한다. | 변경 없음. |
| `src/main/java/com/blitz/web/IndexController.java` | 게시글 목록·상세·등록·수정 Thymeleaf 화면을 제공한다. | 상세 조회에 `commentPage`와 댓글 섹션 응답을 추가한다. |
| `src/main/java/com/blitz/web/PostsApiController.java` | 게시글 CRUD, 페이지 조회, ETag와 상태 코드를 제공한다. | 변경 없음. 댓글 API는 별도 컨트롤러로 분리한다. |
| `src/main/java/com/blitz/web/ProfileController.java` | 활성 배포 프로필을 반환한다. | 변경 없음. |
| `src/main/java/com/blitz/web/dto/HelloResponseDto.java` | 샘플 응답 레코드다. | 변경 없음. |
| `src/main/java/com/blitz/web/dto/PageResponse.java` | 일반 페이지 메타데이터를 제공한다. | 댓글의 `activeCount`와 thread 구조 때문에 댓글 전용 페이지 응답을 사용하며 이 타입은 변경하지 않는다. |
| `src/main/java/com/blitz/web/dto/PostsListResponseDto.java` | 목록용 게시글 요약 응답이다. | 댓글 수를 표시하지 않으므로 변경 없음. |
| `src/main/java/com/blitz/web/dto/PostsResponseDto.java` | 공개 게시글 상세와 버전을 제공한다. | 댓글 섹션과 결합하지 않고 별도 모델 속성으로 유지한다. |
| `src/main/java/com/blitz/web/dto/PostsSaveRequestDto.java` | 게시글 생성 본문을 검증한다. | 변경 없음. |
| `src/main/java/com/blitz/web/dto/PostsUpdateRequestDto.java` | 게시글 수정 본문과 버전을 검증한다. | 변경 없음. |

향후 새 파일은 댓글 엔티티·저장소, 서비스·예외, API 컨트롤러, 요청·응답 DTO로 책임을 분리한다. 댓글 엔티티를 게시글 패키지에 넣지 않고 `domain/comments`로 분리한다.

### 7.5 데이터베이스 마이그레이션

| 파일 | 현재 역할과 분석 | 댓글 기능 조치 |
| --- | --- | --- |
| `src/main/resources/db/migration/V1__create_users_table.sql` | 사용자 테이블과 제공자 식별자 유일성을 생성한다. | 적용된 파일이므로 수정 금지. |
| `src/main/resources/db/migration/V2__create_posts_table.sql` | 게시글 테이블과 버전을 생성한다. | 적용된 파일이므로 수정 금지. |
| `src/main/resources/db/migration/V3__create_spring_session_tables.sql` | JDBC 세션 테이블과 인덱스를 생성한다. | 적용된 파일이므로 수정 금지. |
| `src/main/resources/db/migration/V4__normalize_audit_timestamps.sql` | 기존 사용자·게시글의 감사 시각을 보정한다. | 적용된 파일이므로 수정 금지. |
| `src/main/resources/db/migration/V5__harden_users_constraints.sql` | 사용자 감사 시각, 사진 길이, 제공자 ID 비교 규칙을 강화한다. | 적용된 파일이므로 수정 금지. |
| `src/main/resources/db/migration/V6__add_posts_ownership.sql` | 게시글 사용자 ID 소유권과 버전 기본값을 도입한다. | 적용된 파일이므로 수정 금지. |
| `src/main/resources/db/migration/V7__backfill_post_owners.sql` | 이메일이 유일한 레거시 게시글만 소유자를 역채운다. | 적용된 파일이므로 수정 금지. |
| `src/main/resources/db/migration/V8__index_post_owners.sql` | 게시글 작성자 사용자 ID 인덱스를 생성한다. | 적용된 파일이므로 수정 금지. |
| `src/main/resources/db/migration/V9__constrain_post_owners.sql` | 게시글 작성자와 사용자의 외래 키를 생성한다. | 적용된 파일이므로 수정 금지. |
| `src/main/resources/db/migration/V10__invalidate_legacy_sessions.sql` | 사용자 ID가 없는 직렬화 세션을 무효화한다. | 적용된 파일이므로 수정 금지. 새 `V11`만 추가한다. |

### 7.6 Thymeleaf와 정적 자원

| 파일 | 현재 역할과 분석 | 댓글 기능 조치 |
| --- | --- | --- |
| `src/main/resources/templates/index.html` | 로그인 상태와 페이지형 게시글 목록을 렌더링한다. | 댓글 수를 표시하지 않으므로 변경 없음. |
| `src/main/resources/templates/layout/footer.html` | 공통 푸터와 `index.js`를 로드한다. | 기존 공통 스크립트를 유지하므로 변경 없음. |
| `src/main/resources/templates/layout/header.html` | 제목, 앱 기준 URL, 선택적 CSRF 메타 태그를 렌더링한다. | 구조 변경 없이 상세 화면 호출 시 `includeCsrf=true`를 전달한다. |
| `src/main/resources/templates/posts-detail.html` | 공개 게시글 본문과 작성자 수정 링크를 렌더링한다. | 댓글 수, 목록, 작성·답글·수정·삭제 UI와 페이지 탐색을 추가한다. |
| `src/main/resources/templates/posts-save.html` | 게시글 작성 폼과 CSRF 메타 태그를 제공한다. | 변경 없음. |
| `src/main/resources/templates/posts-update.html` | 버전을 포함한 게시글 수정·삭제 화면을 제공한다. | 변경 없음. |
| `src/main/resources/static/js/app/index.js` | API 요청, CSRF, flash, 폼 검증과 게시글 CRUD 화면 동작을 담당한다. | 댓글 CRUD, 페이지 로딩, 안전한 DOM 생성, History API와 접근성 상태 처리를 추가한다. |
| `src/main/resources/static/css/app.css` | 반응형 게시글 목록·상세·폼 스타일을 제공한다. | 댓글 thread, 답글 들여쓰기, tombstone, 인라인 편집, 페이지·busy 상태 스타일을 추가한다. |

### 7.7 테스트와 테스트 설정

| 파일 | 현재 역할과 분석 | 댓글 기능 조치 |
| --- | --- | --- |
| `src/test/java/com/blitz/ApplicationTests.java` | H2, JDBC 세션, Thymeleaf를 포함한 컨텍스트 기동을 검증한다. | 댓글 빈 구성이 컨텍스트 기동을 깨지 않는지 기존 테스트로 확인한다. |
| `src/test/java/com/blitz/config/auth/LoginUserArgumentResolverTest.java` | 익명 요청의 세션 비생성과 기존 사용자 주입을 검증한다. | 변경 없음. |
| `src/test/java/com/blitz/config/auth/dto/OAuthAttributesTest.java` | OAuth 제공자별 응답 정규화와 오류를 검증한다. | 변경 없음. |
| `src/test/java/com/blitz/domain/posts/PostsRepositoryTest.java` | 게시글 저장, 감사 시각과 낙관적 잠금을 검증한다. | 댓글 삭제 연계는 서비스 또는 댓글 통합 테스트에 추가하고 이 테스트의 책임은 유지한다. |
| `src/test/java/com/blitz/web/HelloControllerTest.java` | 샘플 API 응답을 검증한다. | 변경 없음. |
| `src/test/java/com/blitz/web/IndexControllerTest.java` | 공개 목록·상세와 작성자 편집 화면을 검증한다. | 상세 댓글 최초 렌더링, 권한별 UI, 페이지와 CSRF 메타 검증을 추가한다. |
| `src/test/java/com/blitz/web/PostsApiControllerTest.java` | 게시글 검증, CSRF, 공개 조회, 소유권, 페이지와 충돌 응답을 검증한다. | 게시글 삭제 시 댓글 정리 시나리오만 추가하고 댓글 API는 별도 테스트 클래스로 둔다. |
| `src/test/java/com/blitz/web/ProfileControllerTest.java` | 프로필 경로의 인증 정책과 응답을 검증한다. | 변경 없음. |
| `src/test/java/com/blitz/web/ProfileControllerUnitTest.java` | 배포 프로필 선택 우선순위를 검증한다. | 변경 없음. |
| `src/test/java/com/blitz/web/dto/HelloResponseDtoTest.java` | 샘플 레코드 접근자를 검증한다. | 변경 없음. |
| `src/test/resources/application-test.yml` | H2 create-drop, JDBC 세션과 테스트 OAuth를 설정한다. | 변경 없음. 댓글 엔티티 스키마는 Hibernate가 생성한다. |
| `src/test/resources/logback-test.xml` | 테스트 로그 수준과 콘솔 출력을 설정한다. | 변경 없음. |

향후 새 테스트는 `CommentsRepositoryTest`, `CommentsServiceTest` 또는 API 중심 통합 테스트, `CommentsApiControllerTest`로 분리한다. JavaScript 단위 테스트 인프라는 현재 없으므로 MVC 렌더링과 브라우저 수동 검증 항목을 함께 유지한다.

## 8. 구현 순서와 완료 기준

1. `V11`과 댓글 엔티티·저장소를 추가하고 저장, 계층, 정렬, tombstone 테스트를 통과시킨다.
2. 댓글 서비스와 DTO를 추가하고 권한, 부모 검증, 페이지 조립, 활성 개수와 버전 충돌을 검증한다.
3. 댓글 API, 예외 매핑과 보안 공개 GET 규칙을 추가하고 인증·CSRF·상태 코드 테스트를 통과시킨다.
4. 게시글 상세 서버 렌더링과 게시글 삭제 정리를 연결한다.
5. JavaScript와 CSS로 비동기 CRUD, 페이지 전환, 접근성 상태와 반응형 화면을 완성한다.
6. 전체 `./gradlew test`와 MariaDB/Flyway 기동 검증을 수행한다.

완료 조건은 공개 사용자가 댓글과 답글을 읽을 수 있고, 로그인 사용자가 자신의 댓글만 안전하게 작성·수정·삭제할 수 있으며, 동시 수정에서 데이터 유실이 없고, 삭제 원문이 API와 데이터베이스에 남지 않는 것이다. 21개 이상의 상위 댓글, 답글, tombstone이 함께 존재하는 상세 화면에서도 정렬·페이지·활성 개수가 명세와 일치해야 한다.
