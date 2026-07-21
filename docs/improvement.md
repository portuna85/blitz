# Blitz 전체 개선 및 최적화 로드맵

## 1. 문서 목적과 감사 범위

이 문서는 이미 구현된 Blitz 게시판의 현재 상태를 기준으로 정확성, 보안, 성능, 운영성, 유지보수성, 접근성, 테스트 신뢰도를 개선하기 위한 실행 계획이다. 이전 문서는 댓글 기능을 미래 작업으로 설명했지만, 현재 저장소에는 댓글·1단계 답글·tombstone 삭제·페이지네이션·낙관적 잠금·화면·API·테스트가 모두 구현되어 있다. 따라서 이 문서는 기능 구현 계획이 아니라 저장소 전체 감사 결과와 우선순위가 있는 개선 백로그로 대체한다.

감사 기준일은 2026-07-21이며 다음 범위를 직접 확인했다.

- Git 추적 파일 91개 전부: 루트·도구 8개, Gradle Wrapper 4개, 메인 Java 42개, 메인 설정 3개, Flyway 마이그레이션 11개, 템플릿 6개, 정적 자원 2개, 테스트 Java 12개, 테스트 설정 2개, 문서 1개
- `gradle-wrapper.jar`: 바이너리 내용을 파일 목록과 SHA-256으로 확인하고, 배포 ZIP은 `distributionSha256Sum`으로 고정된 상태임을 확인했다.
- Git 비추적·무시 파일: `.env`와 OAuth JSON은 값을 출력하지 않고 키·JSON 구조만 확인했다. `.gradle`, `.idea`, `build`는 생성물 또는 로컬 상태로 분류하고 소스의 근거로 사용하지 않았다.
- 기존 테스트 결과: 최신 소스 파일보다 나중에 생성된 2026-07-20 결과에 12개 suite, 66개 test, 실패·오류·건너뜀 0개가 기록되어 있다. 이번 작업은 `docs/improvement.md` 외 파일을 변경하지 않아야 하므로 테스트를 다시 실행하지 않았다.
- 기존 패키지 산출물: `build/libs`의 두 JAR은 2026-07-19에 생성되어 댓글 소스보다 오래되었고, boot JAR 안에 댓글 클래스와 `V11` 마이그레이션이 없다. 현재 JAR은 배포 불가한 오래된 산출물이다.

## 2. 현재 기준선

### 잘 되어 있는 부분

- Java 25 toolchain, Spring Boot 4.1, Gradle Wrapper와 배포 ZIP 체크섬이 명시되어 개발 기준선이 분명하다.
- MariaDB 스키마를 Flyway로 관리하고 영속 환경에서 Hibernate를 `validate`로 제한한다.
- 게시글 목록은 폐쇄형 projection을 사용해 큰 `content` 열을 읽지 않는다.
- 게시글과 댓글은 요청 버전과 JPA `@Version`을 함께 사용하며, 소유권은 변경 가능한 이메일이 아니라 사용자 PK로 확인한다.
- 공개 읽기와 인증 쓰기를 분리하고, API 쓰기에도 CSRF를 유지한다. CSP도 인라인 스크립트를 허용하지 않는 강한 기본값이다.
- 댓글 조회는 상위 댓글과 답글을 일괄 조회해 전형적인 답글 N+1 문제를 피한다.
- tombstone 응답은 삭제된 본문과 작성자 이름을 마스킹하며, 게시글 삭제 시 댓글을 정리한다.
- `.env`, `secret/`, 로그, IDE, 빌드 산출물이 Git에서 제외되고 OAuth import 스크립트도 값을 화면에 출력하지 않는다.
- 운영 프로필의 구조화 로그, 크기·기간 기반 rotation, 상세 정보를 숨긴 health endpoint가 준비되어 있다.

### 우선순위 요약

| 상태 | 우선순위 | 문제 | 영향 | 핵심 조치 |
| --- | --- | --- | --- | --- |
| 미착수 | P0 | 배포 JAR이 현재 소스보다 오래됨 | 댓글 코드·V11이 없는 산출물 배포 가능 | CI에서 `clean check bootJar` 후 생성 산출물만 배포 |
| 미착수 | P0 | H2 테스트가 Flyway·MariaDB 제약을 검증하지 않음 | 운영 스키마 drift와 FK·collation 오류를 놓침 | MariaDB Testcontainers + Flyway 통합 테스트 추가 |
| 완료 (2026-07-21) | P0 | 댓글 History API와 인증별 action 렌더링 결함 | 뒤로 가기 history 증식, 익명 reply UI 노출 | history mode 분리, 인증 상태 기반 렌더링, JS 회귀 테스트 |
| 완료 (2026-07-21) | P0 | 익명 상세 조회에서도 CSRF 토큰 렌더링 | 공개 조회가 JDBC 세션을 만들 수 있어 DB 부하 증가 | 로그인 사용자에게만 CSRF 메타 렌더링 |
| 완료 (2026-07-21)* | P0 | 부모 댓글 검증과 동시 삭제 사이 경쟁 조건 | 삭제된 부모에 답글이 생성될 수 있음 | MariaDB 동시성 테스트 후 row lock 또는 조건부 쓰기 적용 |
| 미착수 | P1 | mutation마다 모든 상위 댓글 ID 조회 | 댓글 수에 비례하는 O(n) 메모리·DB 비용 | 대상 정렬 위치를 `COUNT`/rank query로 계산 |
| 미착수 | P1 | 한 상위 댓글의 답글을 무제한 일괄 반환 | 큰 thread가 응답 크기·메모리를 독점 | 답글 상한·별도 cursor pagination 설계 |
| 미착수 | P1 | 브라우저 JS가 단일 600여 줄 파일이며 테스트 없음 | 경합·history·focus 회귀가 발견되지 않음 | 모듈 분리와 DOM/API 단위 테스트 도입 |
| 미착수 | P1 | 운영 프로필·공급망·관측성 자동화 부족 | 재현성·배포 안전성·장애 분석 저하 | 명시적 prod 설정, dependency verification, CI, metrics 보강 |
| 미착수 | P2 | 샘플·레거시 endpoint와 중복 모델이 남음 | 공격 표면과 유지보수 비용 증가 | 사용 여부 확인 후 제거·deprecation |

\* 부모 댓글 락과 FK 위반 변환은 적용했지만, 실제 다중 스레드 기반 MariaDB 잠금 경합 검증은 §3.2 Testcontainers 도입 후로 남아 있다.

## 3. P0: 출시 전에 처리할 항목

### 3.1 재현 가능한 빌드와 배포 산출물

현재 테스트 결과는 최신이지만 boot JAR은 댓글 구현보다 오래되었다. 로컬 `build/`의 존재를 성공적인 배포 준비로 간주하면 안 된다.

개선안:

1. CI의 유일한 배포 경로를 `clean check bootJar`로 고정한다.
2. 테스트가 성공한 동일 job의 JAR만 artifact로 승격하고 source commit SHA를 artifact metadata와 로그에 기록한다.
3. boot JAR에 `CommentsApiController`, `CommentsService`, `comments` entity, `V11__create_comments_table.sql`이 포함되는 smoke check를 추가한다.
4. Gradle dependency verification metadata와 dependency locking을 도입하고, Wrapper JAR 검증도 자동화한다.
5. archive 재현성 옵션과 build-info를 점검해 같은 입력이 가능한 한 같은 산출물을 만들도록 한다.

완료 기준:

- 깨끗한 checkout에서 한 명령으로 66개 이상의 테스트, Flyway 통합 테스트, boot JAR 생성이 성공한다.
- 배포 후보 JAR의 생성 시각·commit이 소스와 일치하고 댓글 클래스와 V11이 포함된다.
- CI 밖에서 생성된 `build/libs` 파일은 배포 입력으로 사용할 수 없다.

### 3.2 MariaDB/Flyway 실환경 동등성 테스트

현재 `test` 프로필은 H2 `create-drop`을 사용하고 Flyway를 끈다. 이 때문에 다음 운영 특성이 테스트되지 않는다.

- V1부터 V11까지의 실제 SQL 문법과 순차 적용
- `posts`, `users`, `comments`의 FK와 `ON DELETE` 동작
- `provider_id`의 `utf8mb4_bin` 비교 규칙
- MariaDB `ENUM`, `DATETIME(6)`, `TEXT`, identity, boolean 표현
- JPA mapping과 Flyway 최종 스키마의 `validate` 일치

특히 repository test는 존재하지 않는 `postId`와 `authorUserId`로 댓글을 저장할 수 있어 운영 FK를 검증하지 못한다.

개선안:

1. 빠른 단위·MVC 테스트용 H2 suite는 유지한다.
2. 별도 integration suite에서 MariaDB Testcontainers를 실행하고 Flyway를 활성화한다.
3. 빈 DB에 V1→V11을 적용하는 fresh migration과, V10 상태에서 V11로 올리는 upgrade migration을 모두 검사한다.
4. FK restrict/cascade, tombstone 제약, 세션 테이블, Hibernate `validate`를 검증한다.
5. CI에서 최소 한 번 이 suite를 필수 check로 실행한다.

완료 기준:

- MariaDB에서 전체 migration과 애플리케이션 context가 성공한다.
- 사용자·게시글·부모 댓글 FK 위반과 게시글 cascade 삭제가 예상한 상태 코드와 데이터 결과를 낸다.
- 이미 적용된 V1~V11은 수정하지 않고 모든 보강은 V12 이후 migration으로 추가한다.

### 3.3 댓글 브라우저 상태와 인증별 UI 수정 — 완료 (2026-07-21)

`loadPage(page, historyMode)`로 분리해 `popstate`는 `pushState`를 다시 호출하지 않도록 수정했고(개선안 1), `commentPage`는 `CommentsService.findPage`에서 서버 측으로 마지막 유효 페이지로 클램프한다(개선안 2, 완료 기준의 "마지막 유효 페이지" 정책 채택). `data-current-user-id`는 `data-can-comment="true|false"`로 대체했고, 서버 템플릿과 JS 렌더링 양쪽에서 로그인 사용자에게만 reply 버튼을 노출한다(개선안 3). `popstate` 핸들러에 `commentPage` 파싱 NaN/음수 가드를 추가했다. 동적 pagination link의 실제 `href` fallback(개선안 4), `AbortController` 기반 오래된 응답 폐기(개선안 5), mutation 후 focus 이동과 busy 상태 알림(개선안 6)은 이번 범위에 포함하지 않았다 — 남은 작업으로 유지한다.

기존 문제: `popstate` handler가 `loadPage()`를 호출하고, `loadPage()`가 다시 `pushState()`를 실행해 사용자가 뒤로 가기를 누를 때 새 history entry가 생겼다. 서버 HTML과 동적 DOM 모두 활성 상위 댓글에 reply 버튼을 항상 만들어 익명 사용자도 완료할 수 없는 답글 폼을 보였고, `data-current-user-id`에는 JS가 사용하지 않는 내부 사용자 PK가 노출되었다.

개선안:

1. `loadPage(page, historyMode)`로 분리해 직접 클릭은 `push`, 초기화·`popstate`는 `none` 또는 `replace`를 사용한다.
2. URL의 `commentPage`를 유한한 0 이상 정수로 정규화하고 범위를 벗어난 페이지는 마지막 유효 페이지 또는 404 정책 중 하나로 일관되게 처리한다.
3. section에는 PK가 아닌 `data-can-comment="true|false"`만 렌더링하고, 서버·동적 DOM 양쪽에서 인증된 사용자에게만 reply action을 제공한다.
4. 동적 pagination link에도 실제 `href`를 만들어 새 탭·JS 비활성 환경에서 의미 있는 fallback을 제공한다.
5. 빠른 연속 페이지 요청은 `AbortController` 또는 증가하는 request token으로 오래된 응답을 폐기한다.
6. 생성·수정·삭제 후 대상 댓글로 focus를 이동하고 comment region의 busy 상태를 보조기술에 알린다.

완료 기준:

- 뒤로/앞으로 이동을 반복해도 history 길이가 불필요하게 늘지 않는다.
- 익명 HTML과 비동기 재렌더링 결과에 작성·답글·수정·삭제 action이 없다.
- HTML에 `currentUserId`가 없고 소유권은 서버 응답의 `owner` boolean만 사용한다.
- 느린 이전 요청이 최신 페이지를 덮어쓰지 않는다.

### 3.4 익명 조회의 세션 생성 방지 — 완료 (2026-07-21)

`posts-detail.html`의 `pageHead(...)` 호출을 `${userName != null}`로 바꿔 로그인 사용자에게만 CSRF meta를 렌더링한다(개선안 1, 2). `IndexControllerTest`에 익명 GET이 `_csrf` meta를 렌더링하지 않고 `request.getSession(false)`가 `null`임을 검증하는 테스트를 추가했다(개선안 3, MockMvc 수준 — 실제 JDBC session row 카운트 검증까지는 하지 않는다). 로그인 사용자 CSRF 쓰기 테스트는 기존대로 통과한다(개선안 4).

기존 문제: `posts-detail.html`은 모든 요청에서 `pageHead(..., true)`를 호출한다. Thymeleaf가 `_csrf`를 평가하면 지연 CSRF token이 구체화되고 익명 공개 조회도 JDBC 세션 row를 만들 수 있다. `LoginUserArgumentResolver`는 익명 요청에서 새 세션을 만들지 않도록 구현되어 있지만 템플릿이 그 이점을 상쇄할 수 있다.

개선안:

1. 상세 페이지의 CSRF meta는 로그인 사용자에게만 렌더링한다.
2. 익명 사용자는 댓글 쓰기 UI 자체가 없으므로 token이 없어도 정상이다.
3. 익명 `GET /`와 `GET /posts/{id}` 이후 `request.getSession(false)`가 `null`이고 JDBC session row가 증가하지 않는 통합 테스트를 추가한다.
4. 로그인 화면·글 작성·수정·댓글 쓰기는 기존 CSRF 방어를 그대로 유지한다.

완료 기준:

- 익명 상세 HTML에 `_csrf` meta가 없고 공개 GET이 세션을 생성하지 않는다.
- 로그인 사용자의 실제 렌더링 token을 사용한 게시글·댓글 쓰기 테스트는 계속 성공한다.

### 3.5 댓글 생성·삭제 경쟁 조건 보강 — 부분 완료 (2026-07-21)

`CommentsRepository`에 `@Lock(PESSIMISTIC_READ)` 기반 `findByIdAndPostIdForUpdate`를 추가하고 답글 생성 시 부모 조회에 사용해 동시 tombstone과의 경쟁을 막았다(개선안 2, 기존 `InvalidParentCommentException`을 재사용). 게시글 동시 삭제로 인한 `DataIntegrityViolationException`은 `CommentsService.create()`에서 잡아 기존 `PostNotFoundException`(404)으로 변환한다(개선안 3). `CommentsServiceTest`(Mockito, 이 저장소 첫 순수 단위 테스트)로 FK 위반 변환 경로를 검증했다. 다만 이 저장소에는 아직 MariaDB Testcontainers가 없어(§3.2 미착수), 실제 다중 스레드 잠금 경합 테스트(개선안 1)와 transaction/lock 순서 문서화(개선안 4)는 하지 않았다 — §3.2 완료 후 이어서 진행한다.

기존 문제: 답글 생성은 부모를 읽어 활성 상위 댓글인지 확인한 뒤 insert한다. 검증 이후 다른 transaction이 부모를 tombstone으로 바꾸면 삭제된 부모 아래 새 답글이 들어갈 수 있다. 게시글 존재 확인과 댓글 insert 사이에도 게시글 삭제 경쟁이 있으며, 이 경우 FK 예외가 구조화되지 않은 500으로 끝날 가능성이 있다.

개선안:

1. MariaDB 동시성 테스트로 답글 생성 대 부모 삭제, 댓글 생성 대 게시글 삭제를 재현한다.
2. 부모 검증 query에 필요한 row lock을 적용하거나, 활성 상위 댓글 조건을 쓰기 시점까지 보장하는 조건부 접근을 사용한다.
3. post FK 경쟁에서 발생한 `DataIntegrityViolationException`을 원인별 `post_not_found` 또는 conflict로 변환한다.
4. transaction 경계와 lock 순서를 문서화해 게시글 삭제와 댓글 mutation 사이 deadlock 가능성을 제한한다.

완료 기준:

- 삭제된 부모에 새 답글이 남지 않는다.
- 동시 게시글 삭제 중 댓글 쓰기는 데이터 유실 없이 결정적인 404/409를 반환한다.
- 동시성 테스트가 H2가 아닌 MariaDB에서 반복 실행된다.

## 4. P1: 성능과 확장성 미세 조정

### 4.1 댓글 query 비용

일반 댓글 페이지는 전형적으로 게시글 존재 확인, 상위 댓글 content/count, 답글 일괄 조회, 활성 개수 조회를 수행한다. 상세 화면에서는 게시글을 이미 읽었는데 `CommentsService`가 다시 `existsById`를 호출한다. mutation 후 `pageIndexOf()`는 해당 게시글의 모든 상위 댓글 ID를 메모리로 읽고 `indexOf`로 위치를 찾는다.

개선안:

- 상세 화면용 facade에서 게시글 존재 확인을 한 번만 수행하거나, comments 조회가 빈 결과와 post 없음만 구분할 수 있는 경량 전략을 사용한다.
- 대상 댓글의 `(createdDate, id)`보다 앞선 상위 댓글 수만 세는 rank/count query로 `targetPage`를 계산한다.
- `activeCount`가 매 요청마다 필요한지 측정한다. 필요하면 `(post_id, deleted)` 인덱스를 V12 이후에 추가하되 먼저 MariaDB `EXPLAIN`과 실제 cardinality를 확인한다.
- 한 부모의 답글 수에 상한을 두거나 별도 cursor pagination을 제공한다. 현재는 한 thread의 모든 답글을 한 응답에 싣는다.
- `deleteByPostId`의 실제 SQL을 확인하고, entity를 하나씩 삭제한다면 명시적 bulk delete 또는 DB cascade 한 가지를 주 경로로 선택한다.
- 게시글 목록은 현재 PK 내림차순과 projection 덕분에 효율적이다. 데이터가 커지고 깊은 offset이 관측될 때만 keyset pagination을 도입한다.

측정 항목:

- 게시글당 상위 댓글 10, 1천, 10만 개에서 `findPage`, create, update, delete의 query 수와 p95
- 한 thread 답글 10, 1천, 1만 개의 응답 크기와 heap 사용량
- `activeCount` index 전후 실행 계획과 write overhead

### 4.2 virtual thread와 DB pool 조율

`spring.threads.virtual.enabled=true`는 대기 중 thread 비용을 줄이지만 MariaDB connection 수를 늘려 주지는 않는다. Hikari pool이 실제 동시성 상한이며, 무제한 요청이 DB 대기로 몰리면 virtual thread의 장점이 사라진다.

개선안:

- 예상 동시 사용자, DB `max_connections`, query p95를 기준으로 Hikari maximum pool size와 connection timeout을 부하 테스트로 결정한다.
- Tomcat request limit, DB pool, upstream timeout을 함께 조정하고 기본값을 추측으로 키우지 않는다.
- slow query와 pool pending/active metric을 관찰한 후에만 index·cache·pool 변경을 채택한다.
- 게시글/댓글 공개 GET에 조건부 cache를 적용할 경우 사용자별 `owner` 값이 섞이지 않도록 응답을 분리하거나 `Vary` 전략을 명시한다.

### 4.3 정적 자원과 화면

- `index.js`를 API client, post forms, comments, history/focus 모듈로 분리하고 minify·fingerprint 여부를 Spring resource chain과 함께 검토한다.
- 날짜는 raw `LocalDateTime` 문자열 대신 `<time datetime>`은 유지하면서 사용자 locale용 표시 문자열을 제공한다.
- 동적 댓글 렌더링도 서버 렌더링과 동일한 markup·권한 규칙을 공유하도록 template 함수 또는 명시적 view model을 둔다.
- 모바일 reply 들여쓰기와 긴 URL·연속 문자열, 200% zoom, forced-colors, 고대비 모드, 키보드-only 흐름을 점검한다.
- CSS는 현재 local asset이며 외부 font/CDN이 없어 CSP와 성능에 유리하다. 측정 전 프레임워크를 추가하지 않는다.

## 5. P1: API·도메인·보안 개선

### 5.1 API 계약 정리

- 게시글 생성 응답의 숫자 본문을 `PostsResponseDto` 또는 `{id, version}`처럼 명시적 객체로 통일한다.
- ETag를 반환만 하지 말고 공개 GET의 `If-None-Match`와 mutation의 `If-Match` 도입을 검토한다. 도입하면 body/query version과 중복된 규칙을 하나로 정리한다.
- `CommentSaveRequestDto.parentId`에도 `@Positive`를 적용한다.
- `CommentPageResponse`와 `CommentThreadDto`의 list를 방어적으로 복사해 record의 불변 기대를 지킨다.
- 존재하지만 범위를 벗어난 댓글 page의 정책을 정의한다. 현재 빈 page가 “첫 댓글” 상태로 보일 수 있다.
- `ObjectOptimisticLockingFailureException`의 포괄 mapping은 댓글 context에서 post conflict code가 되지 않도록 resource별로 분리한다.
- `/api/v1/posts/list`, `/posts/update/{id}`의 실제 사용자를 확인하고 deprecation 기간 후 제거한다.
- `/hello`, `/hello/dto`, `/profile`은 샘플·레거시 운영 endpoint다. 필요성이 없으면 제거하고 profile 정보는 보호된 Actuator info로 통합한다.

### 5.2 도메인 일관성

- 제목·본문·댓글의 trim 정책과 길이 계산 시점을 공통 정책으로 정의한다. 현재 게시글 제목은 저장 시 strip, 게시글 본문은 원문 유지, 댓글은 strip 후 저장한다.
- validation 최대 길이 상수를 DTO와 entity에서 중복하지 않도록 domain policy 또는 상수로 모은다.
- `Comments.delete()`는 non-null clock time과 활성 상태를 자체적으로 방어하거나, 상태 전이를 명시한 domain method로 강화한다.
- `LocalDateTime.now()`를 직접 호출하지 말고 `Clock`을 주입해 테스트 가능성과 timezone 정책을 확보한다. 영속 시각은 UTC `Instant` 또는 명시한 DB timezone 중 하나로 통일한다.
- `Posts`라는 복수형 entity 이름은 장기적으로 `Post`로 정리하되 table/JSON 호환성을 깨지 않는 별도 refactor로 수행한다.
- `Role.GUEST`가 실제로 쓰이지 않으면 제거하거나 guest→user 승격 흐름을 구현한다. DB `ENUM` 변경 비용도 함께 고려한다.

### 5.3 개인정보와 계정 수명주기

`posts.authorEmail`, `users.email`, `users.picture`, 댓글의 `authorUserId`가 보존된다. 게시글 권한이 PK로 전환된 뒤 `authorEmail`은 API에 노출되지는 않지만 중복 PII로 남아 있다. 사용자 FK는 `ON DELETE RESTRICT`라 계정 삭제 정책도 아직 없다.

개선안:

- `authorEmail`의 실제 용도를 확인하고 불필요하면 새 migration으로 제거한다.
- 계정 삭제 시 게시글·댓글을 유지할지, 익명화할지, 함께 삭제할지 정책과 보존 기간을 정한다.
- OAuth email을 식별자로 사용하지 않는 현재 원칙은 유지한다. email verification 상태가 필요한 기능을 추가할 때 Google/Naver의 검증 의미를 별도로 저장한다.
- 사용하지 않는 profile picture 수집을 중단하거나 용도·보존 정책을 명시한다.
- 내부 user PK, email, OAuth 응답 원문이 HTML·API·로그에 포함되지 않는 테스트를 추가한다.

### 5.4 보안과 abuse 방어

- 현재 CSP, CSRF, SameSite, HttpOnly, 작성자 PK 검사는 유지한다.
- 로그인·댓글·게시글 생성에 사용자/IP 기반 rate limit과 최대 request body 크기를 적용해 spam과 큰 요청을 제한한다.
- reverse proxy 배포 시 forwarded header 신뢰 범위와 OAuth callback의 외부 base URL을 명시한다.
- 운영 설정에서 secure cookie를 강제하고 `.env.example`과 README에 `SESSION_COOKIE_SECURE`를 문서화한다.
- `Referrer-Policy`와 `Permissions-Policy`를 명시하고 기존 CSP/security header 회귀 테스트를 추가한다.
- OAuth 로그인 실패를 사용자 친화적인 고정 메시지로 처리하되 provider 응답·token·client secret은 로그에 남기지 않는다.

## 6. P1/P2: 테스트, 운영, 유지보수

### 테스트 포트폴리오

1. 빠른 suite: entity policy, DTO validation, resolver, controller slice.
2. 애플리케이션 suite: SecurityFilterChain, CSRF, session, Thymeleaf 렌더링.
3. MariaDB suite: Flyway, FK, collation, 실제 낙관적 잠금과 동시성.
4. 브라우저 JS suite: history, 익명 action, request race, focus, API error rendering.
5. 최소 E2E smoke: OAuth는 stubbed login으로 글·댓글 CRUD와 logout을 실제 브라우저에서 확인.

추가 품질 gate:

- JaCoCo coverage는 숫자 자체보다 service branch, security error, migration 경계를 보호하는 용도로 사용한다.
- formatter, Checkstyle 또는 SpotBugs 중 필요한 최소 도구를 선택하고 경고 0을 CI 기준으로 한다.
- PowerShell OAuth import script에는 Pester 테스트를 추가해 중복 key, 잘못된 JSON, 줄바꿈, 단일 provider 누락, atomic replace를 검증한다.
- test data builder를 도입해 여러 통합 테스트의 반복 setup을 줄이고 Security principal과 `SessionUser`가 불일치하지 않게 한다.

### 운영 준비

- 기본 profile을 무조건 `local`로 두는 정책을 재검토한다. 배포 환경은 명시적 profile과 필수 datasource/OAuth 변수 없이는 fail-fast 해야 한다.
- MariaDB image는 `mariadb:11` floating tag 대신 검증한 patch 또는 digest로 고정하고 정기 갱신 절차를 둔다.
- 애플리케이션 container/Dockerfile, readiness/liveness, graceful shutdown, migration 실행 주체를 정의한다.
- health 외에 pool, HTTP latency, JVM, Flyway version을 수집하고 business metric은 저비용·저카디널리티로 설계한다.
- 구조화 로그에 request correlation ID를 추가하고 개인정보 masking test를 둔다.
- DB backup/restore drill, migration rollback이 아닌 forward-fix 절차, V10처럼 세션을 비우는 migration의 release note를 운영 runbook에 포함한다.
- 공개 저장소 또는 배포물이라면 LICENSE, 보안 신고 경로, 지원 JDK/DB 범위를 추가한다.

## 7. 파일별 상세 분석과 권장 조치

아래 표는 Git 추적 파일 91개를 빠짐없이 다룬다. 적용된 Flyway 파일과 Gradle 생성 wrapper는 직접 편집하지 않는 것을 전제로 한다.

### 7.1 루트, 빌드, 문서, 도구

| 파일 | 현재 역할·상태 | 권장 개선 |
| --- | --- | --- |
| `.env.example` | MariaDB와 OAuth 로컬 변수 template. 비밀값은 포함하지 않는다. | `SESSION_COOKIE_SECURE`, 필요 시 timezone/pool 설정을 문서화하고 약한 기본값이 운영에 쓰이지 않도록 분리한다. |
| `.gitattributes` | 텍스트 LF, batch CRLF, JAR binary를 고정한다. | 유지. 향후 generated archive와 PowerShell encoding 정책도 필요할 때만 추가한다. |
| `.gitignore` | build, IDE, log, secret, `.env`를 제외하고 wrapper JAR만 예외 처리한다. | 유지. coverage·Testcontainers 임시 파일을 도입할 때 누락 여부를 점검한다. |
| `README.md` | 실행, OAuth, 게시글 API, Flyway 운영을 설명한다. 댓글 API/UI가 빠져 있고 `SESSION_COOKIE_SECURE`도 환경변수 표에 없다. | 현재 기능·오류 계약·댓글 정책·clean build·stale artifact 주의를 반영한다. |
| `build.gradle.kts` | Java 25, Boot 4.1, MVC/JPA/Security/Session/Flyway/Actuator와 H2 test를 구성한다. compile/test UTF-8과 안정적 build-info를 설정한다. | MariaDB integration source set, coverage·quality gate, dependency locking/verification, archive 재현성을 추가한다. |
| `docker-compose.yml` | localhost에만 노출된 MariaDB, healthcheck, volume, log rotation을 제공한다. | MariaDB patch/digest와 charset/timezone을 고정하고 필요 시 resource limit을 둔다. |
| `docs/improvement.md` | 저장소 전체 개선 기준 문서다. | 분기 또는 큰 기능 완료 시 상태·측정치·완료 항목을 갱신한다. 구현 전 계획으로 다시 방치하지 않는다. |
| `scripts/import-oauth-secrets.ps1` | JSON size/shape/개행을 검사하고 값을 출력하지 않은 채 `.env`를 임시 파일로 교체한다. | Pester test, 파일 권한, 한 provider만 갱신하는 옵션, properties 특수문자 처리 정책을 보강한다. |
| `settings.gradle.kts` | root project 이름을 고정한다. | 유지. plugin/dependency repository 정책을 중앙화할 때 확장한다. |

### 7.2 Gradle Wrapper

| 파일 | 현재 역할·상태 | 권장 개선 |
| --- | --- | --- |
| `gradle/wrapper/gradle-wrapper.jar` | 표준 wrapper 실행 바이너리. 감사 시 SHA-256은 `497C8C...A194A9C7`이었다. | 수동 편집 금지. wrapper upgrade task로만 교체하고 checksum 검증을 CI에 둔다. |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 9.6.1 URL과 distribution SHA-256, URL 검증을 고정한다. | 유지. upgrade 시 release note와 plugin 호환성을 함께 검증한다. |
| `gradlew` | POSIX wrapper 생성 스크립트다. | 수동 편집 금지. 실행 권한이 Git에서 유지되는지 CI에서 확인한다. |
| `gradlew.bat` | Windows wrapper 생성 스크립트다. | 수동 편집 금지. Windows CI smoke로 PowerShell 안내와 실제 실행을 확인한다. |

### 7.3 애플리케이션과 인증 설정

| 파일 | 현재 역할·상태 | 권장 개선 |
| --- | --- | --- |
| `src/main/java/com/blitz/Application.java` | Spring Boot 진입점만 가진다. | 유지. startup side effect를 추가하지 않는다. |
| `src/main/java/com/blitz/config/JpaConfig.java` | JPA auditing을 전역 활성화한다. | test clock/timezone 정책과 함께 auditing 시간의 일관성을 검증한다. |
| `src/main/java/com/blitz/config/WebConfig.java` | `LoginUserArgumentResolver`를 MVC에 등록한다. | 유지. 인증 identity를 custom principal 하나로 통합할 때 resolver 책임을 단순화한다. |
| `src/main/java/com/blitz/config/auth/CustomOAuth2UserService.java` | provider ID로 사용자를 upsert하고 `SessionUser`를 세션에 저장한다. concurrent insert를 재조회한다. | 동시 update test, login failure logging 정책, session/principal identity 일치 검증을 추가한다. |
| `src/main/java/com/blitz/config/auth/LoginUser.java` | controller parameter annotation이다. | 유지하거나 custom authentication principal로 전환 시 deprecate한다. |
| `src/main/java/com/blitz/config/auth/LoginUserArgumentResolver.java` | `getSession(false)`로 익명 세션 생성을 피한다. | 이 보장을 익명 상세 MVC/JDBC session 통합 테스트로 보호한다. |
| `src/main/java/com/blitz/config/auth/SecurityConfig.java` | CSP, 공개 GET, USER 쓰기, API 401/403, OAuth login/logout을 구성한다. | reply UI와 별개로 서버 규칙은 유지하고 security headers, legacy route, proxy, rate limit 정책을 보강한다. |
| `src/main/java/com/blitz/config/auth/dto/OAuthAttributes.java` | Google/Naver 응답을 정규화·길이 검증하고 immutable map을 만든다. | email verification/optional picture 정책과 provider별 contract fixture를 추가한다. |
| `src/main/java/com/blitz/config/auth/dto/SessionUser.java` | 사용자 PK·이름·email을 직렬화해 ownership source로 쓴다. | 세션에 email이 실제로 필요한지 줄이고 serialization 변경 시 migration/runbook을 요구한다. |

### 7.4 도메인과 저장소

| 파일 | 현재 역할·상태 | 권장 개선 |
| --- | --- | --- |
| `src/main/java/com/blitz/domain/BaseTimeEntity.java` | 생성·수정 `LocalDateTime`을 auditing으로 채운다. | UTC/`Instant` 또는 명시 timezone과 injectable `Clock` 정책을 정한다. |
| `src/main/java/com/blitz/domain/user/Role.java` | `GUEST`, `USER`와 `ROLE_` key를 제공한다. | 미사용 `GUEST`의 정책을 정하고 DB ENUM 확장 비용을 고려한다. |
| `src/main/java/com/blitz/domain/user/User.java` | OAuth profile, provider identity, role을 검증·보관한다. | profile PII 최소화, account deletion, 필요 시 `@Version`으로 동시 profile update를 보호한다. |
| `src/main/java/com/blitz/domain/user/UserRepository.java` | provider/provider ID 유일 identity를 조회한다. | login query 실행 계획을 MariaDB unique index로 확인하고 필요한 method만 유지한다. |
| `src/main/java/com/blitz/domain/posts/Posts.java` | 게시글 validation, PK ownership, snapshot author, `@Version`을 담당한다. | `Post` 명명, 공통 validation, 불필요한 `authorEmail` 제거를 별도 호환 migration으로 진행한다. |
| `src/main/java/com/blitz/domain/posts/PostsRepository.java` | CRUD와 projection page 조회를 제공한다. | 현재 목록 query는 적절하다. 데이터 증가 후에만 keyset query를 추가한다. |
| `src/main/java/com/blitz/domain/posts/PostsSummary.java` | 큰 본문을 제외한 폐쇄형 목록 projection이다. | 유지하고 projection query가 content를 선택하지 않는 SQL test를 추가한다. |
| `src/main/java/com/blitz/domain/comments/Comments.java` | 1단계 reply, 본문 정규화, ownership, tombstone, `@Version`을 구현한다. | 상태 전이 guard, non-null deletion time, shared limits, clock 정책을 강화한다. |
| `src/main/java/com/blitz/domain/comments/CommentsRepository.java` | root page, replies batch, active count, post-scoped lookup, post delete, root ID 전체 조회를 제공한다. | 전체 ID 조회를 rank count로 교체하고 active-count index와 bulk delete는 실행 계획 측정 후 적용한다. |

### 7.5 서비스와 예외

| 파일 | 현재 역할·상태 | 권장 개선 |
| --- | --- | --- |
| `src/main/java/com/blitz/service/PostsService.java` | CRUD, owner/version 검사, sort·size clamp, 댓글 선삭제를 transaction으로 묶는다. | 댓글 삭제 SQL을 최적화하고 post/comment facade로 중복 존재 조회를 제거한다. 실제 동시 delete conflict test도 추가한다. |
| `src/main/java/com/blitz/service/CommentsService.java` | post/parent 검증, thread 조립, active count, target page, owner/version/tombstone을 처리한다. | P0 경쟁 조건, O(n) `pageIndexOf`, page 범위, unbounded replies, `Clock`을 개선한다. |
| `src/main/java/com/blitz/service/exception/CommentNotFoundException.java` | post-scoped 댓글 404를 표현한다. | API 밖 사용 시 `@ResponseStatus`와 advice 중 단일 mapping 원칙을 정한다. 내부 ID가 운영 log에 과도하게 남지 않게 한다. |
| `src/main/java/com/blitz/service/exception/CommentVersionConflictException.java` | 댓글 version conflict를 구분한다. | resource/version 정보를 구조화하되 공개 응답에는 안전한 고정 메시지를 유지한다. |
| `src/main/java/com/blitz/service/exception/InvalidParentCommentException.java` | reply 또는 tombstone을 parent로 쓸 때 400을 표현한다. | 동시성 보강 후에도 동일한 안정적 error code를 유지한다. |
| `src/main/java/com/blitz/service/exception/PostNotFoundException.java` | 게시글 404를 표현한다. | FK 경쟁에서 발생한 DB 예외도 이 계약으로 일관되게 변환한다. |
| `src/main/java/com/blitz/service/exception/PostVersionConflictException.java` | 게시글 version conflict를 표현한다. | conditional request를 도입하면 precondition error 정책과 통합한다. |

### 7.6 웹 controller와 DTO

| 파일 | 현재 역할·상태 | 권장 개선 |
| --- | --- | --- |
| `src/main/java/com/blitz/web/ApiExceptionHandler.java` | validation, malformed JSON, auth, access, 404, 409를 안정적 JSON으로 변환한다. | optimistic exception을 resource별로 구분하고 payload size/rate-limit/DB integrity 오류 계약을 추가한다. |
| `src/main/java/com/blitz/web/CommentsApiController.java` | 공개 page와 인증 CRUD, Location, path/query validation을 제공한다. | page 범위 정책, conditional version, consistent response type을 반영한다. |
| `src/main/java/com/blitz/web/HelloController.java` | 인증 뒤에 남아 있는 sample endpoint다. | 외부 사용이 없으면 controller와 관련 test/DTO를 제거한다. |
| `src/main/java/com/blitz/web/IndexController.java` | 목록·상세·작성·편집 SSR과 첫 댓글 page를 조립한다. | 상세의 중복 post 조회, 익명 CSRF session, 내부 current user ID model을 제거한다. |
| `src/main/java/com/blitz/web/PostsApiController.java` | 게시글 CRUD, public page/detail, ETag, legacy list를 제공한다. | create body 통일, conditional GET/write, legacy list deprecation을 진행한다. |
| `src/main/java/com/blitz/web/ProfileController.java` | active/default deployment profile 문자열을 인증 사용자에게 반환한다. | 운영 의존성을 확인하고 불필요하면 제거하거나 Actuator info로 이동한다. |
| `src/main/java/com/blitz/web/dto/CommentPageResponse.java` | thread page meta와 active count를 반환한다. | content를 방어 복사하고 out-of-range 의미를 명확히 한다. |
| `src/main/java/com/blitz/web/dto/CommentResponseDto.java` | tombstone masking과 요청 사용자별 owner를 계산한다. | 날짜 표현 정책을 명시하고 masking regression test를 확장한다. |
| `src/main/java/com/blitz/web/dto/CommentSaveRequestDto.java` | content blank/1000자 제한과 optional parent를 받는다. | `parentId @Positive`, 공통 길이 상수, normalized length 규칙을 적용한다. |
| `src/main/java/com/blitz/web/dto/CommentThreadDto.java` | 상위 댓글 하나와 전체 답글 list를 묶는다. | list 방어 복사와 답글 pagination/summary metadata를 준비한다. |
| `src/main/java/com/blitz/web/dto/CommentUpdateRequestDto.java` | content와 non-negative version을 검증한다. | 공통 validation policy와 conditional request 전환 시 version 중복을 정리한다. |
| `src/main/java/com/blitz/web/dto/HelloResponseDto.java` | sample record다. | Hello endpoint를 제거할 때 함께 제거한다. |
| `src/main/java/com/blitz/web/dto/PageResponse.java` | 일반 page를 immutable content와 명시적 meta로 변환한다. | 유지. cursor 도입 시 별도 타입으로 분리한다. |
| `src/main/java/com/blitz/web/dto/PostsListResponseDto.java` | 목록 projection을 공개 DTO로 변환한다. | 유지. comment count를 무조건 추가해 목록 query를 무겁게 하지 않는다. |
| `src/main/java/com/blitz/web/dto/PostsResponseDto.java` | 공개 게시글과 version·감사 시각을 반환한다. | ETag/conditional contract와 날짜 표시 정책을 정리한다. |
| `src/main/java/com/blitz/web/dto/PostsSaveRequestDto.java` | 제목 500자, 본문 10000자를 검증한다. | entity와 정규화·길이 상수를 공유한다. |
| `src/main/java/com/blitz/web/dto/PostsUpdateRequestDto.java` | 게시글 값과 non-negative version을 검증한다. | conditional request 도입 여부에 맞춰 version source를 하나로 정리한다. |

### 7.7 애플리케이션 설정

| 파일 | 현재 역할·상태 | 권장 개선 |
| --- | --- | --- |
| `src/main/resources/application.yml` | 기본 local profile, open-in-view off, virtual thread, secure session 기본값, health/info 노출을 설정한다. | 배포 fail-fast profile, proxy, request size, graceful shutdown, metrics 정책을 명시한다. |
| `src/main/resources/application-local.yml` | MariaDB/Flyway/JDBC session과 Google/Naver placeholder를 제공하고 local cookie secure를 끈다. | placeholder login UX, pool/timezone, 운영 설정과의 분리를 강화한다. |
| `src/main/resources/logback-spring.xml` | local/test console과 운영 ECS rolling/error file을 구성한다. | correlation ID, PII masking, 비동기 logging 필요성을 부하 측정 후 결정한다. error 이중 저장 용량도 점검한다. |

### 7.8 Flyway 마이그레이션

| 파일 | 현재 역할·상태 | 권장 개선 |
| --- | --- | --- |
| `V1__create_users_table.sql` | 사용자와 provider identity unique, role ENUM을 생성한다. | 수정 금지. role/account 정책 변화는 새 migration으로 처리한다. |
| `V2__create_posts_table.sql` | 게시글, snapshot author/email, version을 생성한다. | 수정 금지. PII 제거·명명 보강은 새 migration으로 처리한다. |
| `V3__create_spring_session_tables.sql` | JDBC session과 expiry/principal index를 생성한다. | 수정 금지. 현재 Spring Session 버전의 공식 schema와 integration test로 호환성을 검증한다. |
| `V4__normalize_audit_timestamps.sql` | legacy null 감사 시각을 보정한다. | 수정 금지. upgrade fixture로 결과를 검증한다. |
| `V5__harden_users_constraints.sql` | 감사 시각 not-null, picture 2048, provider ID binary collation을 적용한다. | 수정 금지. MariaDB collation test를 추가한다. |
| `V6__add_posts_ownership.sql` | nullable owner PK와 version 기본값을 추가한다. | 수정 금지. 신규 row는 app에서 non-null이고 legacy null row 정책을 문서화한다. |
| `V7__backfill_post_owners.sql` | email이 유일한 legacy 게시글만 owner를 역채운다. | 수정 금지. unresolved owner row의 조회·수정 정책과 운영 count를 확인한다. |
| `V8__index_post_owners.sql` | post owner index를 추가한다. | 수정 금지. 실제 owner 관리 query가 없으면 장기적으로 index 효용을 측정한다. |
| `V9__constrain_post_owners.sql` | user FK restrict를 추가한다. | 수정 금지. account deletion/anonymization 정책을 새 migration으로 구현한다. |
| `V10__invalidate_legacy_sessions.sql` | user ID 없는 직렬화 세션을 일괄 만료시킨다. | 수정 금지. 배포 release note와 재로그인 영향 template로 남긴다. |
| `V11__create_comments_table.sql` | 댓글·reply FK, tombstone 열, version, thread/author index를 생성한다. | 수정 금지. V12 이후에 tombstone check, `(post_id, deleted)` index, parent 무결성을 MariaDB 측정 후 보강한다. |

### 7.9 Thymeleaf와 정적 자원

| 파일 | 현재 역할·상태 | 권장 개선 |
| --- | --- | --- |
| `src/main/resources/templates/layout/header.html` | CSP 친화적 head, optional CSRF meta, 로그인·logout header를 제공한다. | 상세에서 로그인 여부에 따라 CSRF를 전달하고 OAuth placeholder 상태 UX와 header 접근성을 점검한다. |
| `src/main/resources/templates/layout/footer.html` | footer와 공통 JS를 defer 로드한다. | asset fingerprint/module 분리 시 entrypoint만 유지한다. |
| `src/main/resources/templates/index.html` | 접근 가능한 table, empty state, post pagination을 SSR한다. | 날짜 locale 표시와 매우 깊은 page 정책을 보강한다. 현재 projection 구조는 유지한다. |
| `src/main/resources/templates/posts-detail.html` | 게시글·첫 댓글 thread·owner action·pagination을 SSR한다. | 익명 reply action, current user PK, 익명 CSRF를 제거하고 focus target/실제 page fallback을 추가한다. |
| `src/main/resources/templates/posts-save.html` | CSRF meta와 게시글 작성 form을 제공한다. | server error field 연결, dirty-form 이탈 경고는 UX 요구가 있을 때 추가한다. |
| `src/main/resources/templates/posts-update.html` | version을 포함한 수정·삭제 form이다. | conditional request 정책, conflict 후 최신 내용 비교 UX를 보강한다. |
| `src/main/resources/static/css/app.css` | token 기반 반응형 layout, focus, notice, comment thread, reduced-motion을 제공한다. | forced-colors, 200% zoom, 긴 thread/mobile indentation, busy region을 접근성 test로 보강한다. |
| `src/main/resources/static/js/app/index.js` | CSRF API client, flash, post CRUD, comment CRUD/render/history를 모두 담당한다. | P0 history/auth/race/focus를 고치고 모듈 분리·JS test·실제 pagination href를 도입한다. |

### 7.10 테스트와 테스트 설정

| 파일 | 현재 역할·상태 | 권장 개선 |
| --- | --- | --- |
| `src/test/java/com/blitz/ApplicationTests.java` | H2 session table과 Thymeleaf context 기동을 확인한다. | MariaDB/Flyway context test를 별도 suite로 추가하고 익명 session 비생성을 검사한다. |
| `src/test/java/com/blitz/config/auth/LoginUserArgumentResolverTest.java` | 익명 세션 비생성과 기존 `SessionUser` 반환을 검증한다. | wrong-type/stale session과 principal 통합 정책을 추가한다. |
| `src/test/java/com/blitz/config/auth/dto/OAuthAttributesTest.java` | Google/Naver mapping과 주요 오류를 검증한다. | 길이 경계, null response, email verification/picture 정책 fixture를 추가한다. |
| `src/test/java/com/blitz/domain/comments/CommentsRepositoryTest.java` | 정렬, validation, owner, lock, tombstone, page, active count를 H2에서 검증한다. | FK·cascade·rank query·동시성은 MariaDB로 옮기고 H2 test가 운영 무결성을 대표하지 않게 명명한다. |
| `src/test/java/com/blitz/domain/posts/PostsRepositoryTest.java` | 저장, auditing, optimistic lock을 검증한다. | projection SQL과 MariaDB FK/version 동작을 추가하고 고정된 2019 시각 대신 injected clock을 사용한다. |
| `src/test/java/com/blitz/web/CommentsApiControllerTest.java` | 공개 읽기, auth/CSRF, ownership, parent, tombstone, target page, stale update를 통합 검증한다. | stale delete, malformed JSON, invalid parent ID, page 범위, DB 경쟁, 내부 PK 비노출을 추가한다. |
| `src/test/java/com/blitz/web/HelloControllerTest.java` | sample endpoint를 검증한다. | endpoint 제거 시 함께 삭제해 suite 비용과 공격 표면을 줄인다. 유지한다면 실제 SecurityConfig를 제외하지 않는다. |
| `src/test/java/com/blitz/web/IndexControllerTest.java` | 목록·상세·owner edit·댓글 SSR·tombstone·page link를 검증한다. | 익명 reply/CSRF/세션 비생성, PK 비노출, out-of-range page, 날짜·접근성 selector를 추가한다. |
| `src/test/java/com/blitz/web/PostsApiControllerTest.java` | create/validation/CSRF/public/owner/page/version/delete cleanup을 폭넓게 검증한다. | 실제 concurrent request, conditional header, malformed JSON, projection query, MariaDB cascade를 추가한다. |
| `src/test/java/com/blitz/web/ProfileControllerTest.java` | profile endpoint의 인증 정책을 전체 SecurityConfig와 검증한다. | endpoint 유지 여부 결정 후 Actuator 정책 test로 대체한다. |
| `src/test/java/com/blitz/web/ProfileControllerUnitTest.java` | deployment profile 우선순위를 검증한다. | profile endpoint 제거 시 삭제한다. 유지 시 빈 default 방어를 추가한다. |
| `src/test/java/com/blitz/web/dto/HelloResponseDtoTest.java` | sample record accessor만 검증한다. | sample 제거 시 삭제한다. 이런 trivial test보다 domain/API 경계에 투자한다. |
| `src/test/resources/application-test.yml` | unique H2, create-drop, Flyway off, embedded session, test OAuth를 구성한다. | 빠른 suite 전용임을 명시하고 별도 MariaDB/Flyway profile을 추가한다. |
| `src/test/resources/logback-test.xml` | test console과 SQL log 억제를 설정한다. | 동시성/migration 실패 진단 시 opt-in SQL profile을 쓰고 기본 출력은 조용하게 유지한다. |

## 8. 구현 순서와 완료 기준

### 1단계: 즉시 안전화

1. [ ] stale artifact 배포를 막는 CI와 clean build smoke를 추가한다. (§3.1, 미착수)
2. [x] 익명 CSRF/session, reply action, current user PK, History API를 수정한다. (§3.3, §3.4, 2026-07-21 완료)
3. [x] 관련 MVC·JS 회귀 테스트를 먼저 추가한다. (서버 측 MockMvc 테스트만 — JS 자동 테스트는 도입하지 않고 수동 브라우저 확인으로 대체)

### 2단계: 데이터 무결성

1. [ ] MariaDB Testcontainers와 Flyway suite를 추가한다. (§3.2, 미착수)
2. [x]/[ ] 부모 삭제/답글 생성 경쟁은 pessimistic lock으로 보강 완료(§3.5). 게시글 삭제/댓글 생성 경쟁은 FK 예외 변환으로 대응했으나, 실제 MariaDB 동시성 재현·검증은 Testcontainers 도입 후로 남아 있다.
3. [ ] V12가 필요하면 check/index를 새 migration으로만 추가한다. (해당 없음 — 이번 변경은 기존 스키마로 충분)

### 3단계: 측정 기반 최적화

1. 댓글 mutation의 O(n) target page 계산을 rank/count query로 교체한다.
2. active count index, bulk delete, reply pagination을 실제 실행 계획과 부하 결과로 결정한다.
3. virtual thread, Hikari, HTTP timeout을 함께 튜닝한다.

### 4단계: 유지보수와 운영

1. JS 모듈화, sample/legacy endpoint 정리, validation/time policy 통합을 수행한다.
2. dependency verification, container pinning, prod profile, metrics, runbook을 완성한다.
3. README와 API 문서를 현재 기능에 맞춰 갱신한다.

최종 완료 조건은 다음과 같다.

- 깨끗한 checkout과 MariaDB에서 migration, test, boot JAR 생성이 자동으로 재현된다.
- 공개 GET은 불필요한 세션을 만들지 않고, 모든 mutation은 인증·CSRF·소유권·동시성 규칙을 지킨다.
- 브라우저 history, focus, 인증별 action, 느린 요청 경쟁이 자동 테스트로 보호된다.
- 댓글 수가 커져도 mutation이 전체 root ID를 읽지 않으며 답글 한 thread가 무제한 응답을 만들지 않는다.
- 배포 artifact, DB migration, 로그·metric, backup/forward-fix 절차가 같은 release 기준으로 추적된다.
