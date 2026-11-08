# ADR-005. Безопасность: Keycloak, JWT и проверка прав в каждом сервисе

* Статус: принято
* Дата: 2026-11-08

## Решение

* **Keycloak** (realm `moby`, импорт `infra/keycloak/moby-realm.json`). Роли realm: `PARENT`, `CHILD_JUNIOR`,
  `CHILD_TEEN`. Демо-пользователи: `anna` (родитель), `misha` (9 лет), `ivan` (17 лет), `olga` (родитель другой
  семьи — для проверки 403). Пароль у всех `demo`.
* **Claims токена.** Протокол-мапперы клиента `moby-frontend` кладут в access token `roles` (роли realm),
  `member_id` и `family_id` (атрибуты пользователя, объявлены в declarative user profile). Сервисы не ходят в
  Keycloak за профилем и не держат таблицу пользователей: кто я и в какой семье — в токене.
* **Gateway** проверяет JWT (подпись по JWKS, срок, `iss`) и закрывает всё, кроме `/api/**` и health.
  Токен передаётся сервисам без изменений.
* **Каждый сервис — тоже resource server** (defense in depth: сервисы доступны во внутренней сети и вызывают
  друг друга с JWT пользователя). Ключи — по `jwk-set-uri` внутреннего адреса Keycloak, `iss` сверяется
  с публичным адресом (`issuer-uri`), по которому токен выпущен для браузера.
* **Права на ресурсы — в сервисах** (`AccessPolicy`): родитель — только дети своей семьи (`familyId` записи ==
  `family_id` токена), ребёнок — только своё (`childId == member_id`), операции родителя — только `PARENT`.
  Ответ — ProblemDetail 403 `FORBIDDEN` (401 — без токена). Тесты: `AccessControlTest`, `FamilyAccessControlTest`,
  `RewardsScenariosTest.access`, `NotificationFlowTest.access`, `GatewayRoutingTest`.
* **Вход на стенде.** Переключатель ролей в панели жюри получает токен демо-пользователя через Direct Access
  Grant (`grant_type=password`) публичного клиента — без ввода пароля, мгновенное переключение.

## Альтернативы и компромиссы

* Password grant не рекомендован OAuth 2.1 и не годится для продакшена. В продакшене — Authorization Code + PKCE
  (у клиента уже включены standard flow и PKCE S256) или BFF-сессия в gateway. Для стенда, где жюри за секунду
  переключается между тремя людьми, это осознанное упрощение, ограниченное профилем demo.
* Mutual TLS / сервисные токены между сервисами не нужны: внутренние вызовы делаются от имени пользователя.
