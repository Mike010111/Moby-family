# ADR-009. Сборка на Maven

* Статус: принято (заменяет сборку на Gradle Kotlin DSL из этапа 1)
* Дата: 2026-11-08

## Контекст

Проект собирался Gradle 9 (Kotlin DSL, version catalog, convention-плагины в `build-logic`). Сборка работала,
но: команда и проверяющие банка привычно работают с Maven; Kotlin DSL и convention-плагины — отдельный язык и
отдельный проект, который нужно понимать, чтобы прочитать, какие зависимости у сервиса; первая загрузка Gradle
и Kotlin-компилятора заметно удлиняла сборку образа в Docker.

## Решение

* Корневой `pom.xml` (packaging `pom`) — родитель всех модулей; его родитель — `spring-boot-starter-parent`
  той же версии Spring Boot (4.1.2). Версии — в `<properties>`, BOM Spring Cloud и Spring Modulith — в
  `<dependencyManagement>`, версии Testcontainers, JUnit, AssertJ и т. д. — из BOM Spring Boot, как и раньше.
* У каждого модуля свой `pom.xml` с явным списком зависимостей: всё, что сервис использует, видно в одном файле.
  Состав зависимостей сверен с Gradle (`dependencies` ↔ `dependency:list` по runtime и test для каждого модуля).
* Плагины: `maven-compiler-plugin` (release 21, `-parameters`, процессор MapStruct в `annotationProcessorPaths`),
  `maven-surefire-plugin` (все тесты, `user.timezone=Europe/Minsk`), `jacoco-maven-plugin` (отчёт и порог 80 %
  строк на фазе `verify`), `spring-boot-maven-plugin` только в сервисах (`target/app.jar`).
* Maven Wrapper (`mvnw`, `.mvn/wrapper`) фиксирует версию Maven; одна команда проверки — `./mvnw clean verify`.

## Последствия

* Число тестов и покрытие не изменились (115 тестов; покрытие строк по сервисам 84–89 %).
* Maven разрешает конфликт версий «ближайшая / управляемая», а Gradle — «наибольшая»: BOM Modulith опускал ядро
  ArchUnit до 1.4.x, его версия зафиксирована в `dependencyManagement`.
* Нет инкрементальной сборки и build cache Gradle: полная сборка с тестами на ноутбуке ~1,5 мин — приемлемо.
* Toolchain Gradle гарантировал JDK 21 для компиляции и тестов; в Maven байткод и API фиксирует `release 21`,
  сборка идёт на JDK 21+ (в CI и в Docker — ровно 21).
