# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

听书 (audio-book) platform backend — a Spring Cloud Alibaba microservices system. Java 17, Spring Boot 3.0.5, Spring Cloud 2022.0.2, Maven multi-module (`groupId com.atguigu.tingshu`, version `1.0`).

Business flow: user uploads an album → content audit (Tencent Cloud) → publish/上架 to Elasticsearch → search/browse → order → WeChat pay → listen (with paid-track/VIP gating).

**Critical: there are no local `application.yml` files.** Every module only has `bootstrap.properties` that points at Nacos (`192.168.200.6:8848`) for both discovery and config, with a shared `common.yaml`. So DB/Redis/RabbitMQ/Kafka/ES/MinIO/WeChat/VOD connection settings live in Nacos, not in this repo. A service cannot start without Nacos and the surrounding middleware.

## Build & test

No Maven wrapper — use a system `mvn` with JDK 17.

```bash
mvn clean install -DskipTests              # build all modules
mvn -pl service/service-search -am clean package -DskipTests   # build one module + its deps
```

- Many service modules set `<skipTests>true</skipTests>` in their pom. To run tests, override it: `-DskipTests=false`.
- There is exactly one test file: `service/service-search/src/test/java/com/atguigu/tingshu/test.java` (a `@SpringBootTest` smoke test for Feign clients).
  ```bash
  mvn -pl service/service-search -am test -DskipTests=false -Dtest=test        # whole class
  mvn -pl service/service-search -am test -DskipTests=false -Dtest=test#test1  # one method
  ```
- `pinyin4j` is not on Maven Central — see the install-file note in the root `pom.xml`.

## Module layout

```
common/          shared infra (common-log, common-util, rabbit-util, service-util)
model/           shared entities + VO + Query objects for all services
service/         7 business services (see below)
server-gateway/  Spring Cloud Gateway (CorsConfig, AuthGlobalFilter)
service-client/  Feign clients, each with a matching *DegradeFeignClient fallback
```

Business services (`service/service-*`): `service-user` (users, WeChat login, VIP, listen history), `service-album` (albums/tracks/categories, upload, audit), `service-search` (Elasticsearch), `service-account` (wallet/recharge/deduct), `service-order`, `service-payment` (WeChat pay), `service-dispatch` (XXL-Job).

## Architecture notes

- **Auth.** Login is enforced per-endpoint, not at the gateway (the gateway `AuthGlobalFilter` is currently a passthrough stub). The custom `@Fgb(value = true)` annotation + `GuiGuLoginAspect` (in `common/service-util/.../login/`) does the work: read `token` header → look up Redis key `user:login:{token}` (`RedisConstant.USER_LOGIN_KEY_PREFIX`) → load `UserInfoVo` → store userId in `AuthContextHolder` (ThreadLocal). `FeignInterceptor` forwards the `token` header to downstream services so auth survives service-to-service calls.
- **Cross-service calls.** Feign clients live in `service-client/*`; each has a degrade (fallback) impl. `service` module depends on all `service-client` artifacts.
- **Async.** RabbitMQ via `RabbitService` (`rabbit-util`) plus Kafka. All exchange/queue/routing constants are in `MqConst` — album `upper`/`lower`/stat-update, track stat-update, order cancel (15-min delay), pay-success, account unlock/minus, user register/pay-record/vip-expire, and search keyword input/output. Stat/ranking updates are MQ-driven.
- **Search.** `AlbumInfoIndex` is the ES `@Document(indexName = "albuminfo")`; repository extends `ElasticsearchRepository`. `MqConst.ROUTING_ALBUM_UPPER`/`LOWER` are the publish/unpublish signals that drive index writes.

## Coding conventions (match these)

- **API shape is authoritative in `docs/api .md`** (note the space in the filename — `api .md`, not `api.md`). This exported doc defines every endpoint's `**Path**`/`**Method**`/params/response and overrides any assumption (e.g. album 上架 `upperAlbum` and 下架 `lowerAlbum` are both `GET`, not POST). Grep the endpoint index with `grep -nE "^\*\*Path：|^\*\*Method：" "docs/api .md"` before adding/changing an endpoint.
- **Controllers**: `@RestController` + `@RequestMapping("api/xxx")` (no leading slash), `@Tag`/`@Operation` for Knife4j, `@SuppressWarnings({"all"})`, `@Autowired` field injection. Methods return `Result` or `Result<T>` and carry a Javadoc with `@param`/`@return`. `@PathVariable Long id` for resource ids; match the HTTP verb from the API doc (GET/POST/PUT/DELETE).
- **Login-required endpoints** are marked `@Fgb(value = true)`; read the current user via `AuthContextHolder.getUserId()`.
- **Services**: interface + `@Slf4j @Service @SuppressWarnings({"all"})` impl, with numbered `//1. //2.` step comments in the body.
- **Errors**: throw `GuiguException(ResultCodeEnum.XXX)`; handled centrally by `GlobalExceptionHandler` → `Result` with code/message.
- **Persistence**: MyBatis-Plus. Entities extend `BaseEntity` (id/createTime/updateTime/isDeleted) and use `@TableName`/`@TableField`; mappers are thin interfaces with XML in `src/main/resources/mapper/`.
- Use `@PathVariable` (not `@RequestParam`) for ids in the path.
