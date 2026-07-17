# /app

Spring Boot 3.x + Java 17 애플리케이션 (Gradle).

Phase 0.2에서 스캐폴딩 완료:

- Spring Boot 3.5.16, Java 17 (Gradle toolchain)
- 의존성: Spring Web, Thymeleaf(+ Spring Security 6 extras), Spring Security, Spring Batch,
  Spring Data JPA, Flyway(core + mysql), HAPI FHIR R4 구조체 라이브러리
  (`ca.uhn.hapi.fhir:hapi-fhir-structures-r4`), JUnit 5(spring-boot-starter-test)
- 런타임 DB 드라이버: MySQL Connector/J (테스트는 H2 인메모리로 대체)
- 빌드: `./gradlew build` (Windows: `.\gradlew.bat build` 또는 `.\gradlew build`)

> 참고: Spring Initializr(start.spring.io)가 현재 Spring Boot 4.x만 생성 지원해서,
> Gradle Wrapper/디렉터리 골격만 Initializr로 받고 `build.gradle`은 Maven Central에 실제
> 게시된 최신 안정 3.x 버전(3.5.16)으로 직접 고정했다.
