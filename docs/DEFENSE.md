# Шпаргалка для защиты первой лабораторной

Откройте [Swagger UI](http://localhost:8080/swagger-ui/index.html#/).
Для запроса раскройте нужную операцию, нажмите **Try it out**, заполните параметры и нажмите **Execute**.
Смотрите фактические **Server response → Code**, **Response body** и **Response headers**:
нижний перечень возможных Responses описывает документацию, а не результат выполнения.

## Что означает ADMIN сейчас

`ADMIN` хранится как значение `UserRole`, но не предоставляет отдельных прав.
Spring Security, входа и проверки личности вызывающего в первой лабораторной нет.
Любой клиент API может вызвать операции с пользователями, программами и курсами.

Роли `STUDENT` и `PROFESSOR` используются как бизнес-атрибуты: записать можно активного студента,
назначить преподавателем курса — активного преподавателя. Это проверки объектов операции,
а не авторизация отправителя запроса. Права ADMIN будут реализованы в лабораторной №3.

## Подготовка данных в Swagger

Создайте преподавателя, двух студентов, две программы и хотя бы два курса.
Для проверки запрета удаления опубликованного курса и удаления черновика удобно иметь третий курс.
Записывайте ID из ответов: они не обязаны начинаться с 1. В шаблонах числовые ID нужно заменить фактическими.
При повторном показе используйте новые email и коды программ либо уже созданные записи.

**Преподаватель — POST /api/v1/users:**

```json
{
  "fullName": "Иван Петров",
  "email": "professor-defense@example.org",
  "role": "PROFESSOR",
  "active": true
}
```

**Первый студент — POST /api/v1/users:**

```json
{
  "fullName": "Анна Смирнова",
  "email": "student-one-defense@example.org",
  "role": "STUDENT",
  "active": true
}
```

Создайте второго студента с другим email, например `student-two-defense@example.org`.

**Программа — POST /api/v1/programs:**

```json
{
  "code": "IVT-DEFENSE",
  "name": "Информатика и вычислительная техника",
  "description": "Программа бакалавриата",
  "archived": false
}
```

Создайте вторую программу с кодом `SE-DEFENSE` и названием «Программная инженерия».

**Курс — POST /api/v1/courses:**

```json
{
  "title": "Java Backend",
  "description": "Разработка серверов на Spring Boot",
  "professorId": 1,
  "capacity": 1,
  "startDate": "2026-10-01",
  "endDate": "2026-12-01"
}
```

`professorId` замените ID преподавателя. Даты — пример для сентября 2026 года:
на день защиты дата начала должна быть строго в будущем по UTC.
Второй курс назовите «Базы данных». Сначала оставьте курсы в DRAFT.

Свяжите первый курс с обеими программами, второй — с первой:
**PUT /api/v1/courses/{id}/programs/{programId}**. Ожидается 204 без тела ответа.

Откройте набор первого курса через **PATCH /api/v1/courses/{id}/status**:

```json
{"status": "ENROLLMENT_OPEN"}
```

Запишите первого студента через **POST /api/v1/enrollments**:

```json
{"studentId": 2, "courseId": 1}
```

Замените оба ID фактическими. Запомните ID созданной записи. Ожидается 201 и статус ENROLLED.
Отменять курс пока не нужно: сначала покажите проверки мест, отказ и восстановление записи.

## Требования и доказательства

### 1. Осмысленный REST CRUD

**Swagger:** для пользователя или программы выполните POST → GET по ID → PUT → GET → DELETE.
Для PUT передавайте весь Request DTO, изменив нужное поле. Для удаления используйте отдельный объект без связей.
У курса полный CRUD доступен в DRAFT. У опубликованного курса DELETE возвращает 409 — вместо удаления применяется отмена.

У Enrollment: POST создаёт запись, GET читает, PATCH `/{id}/grade` обновляет оценку,
DELETE переводит в DROPPED. Повторный POST до начала обучения восстанавливает ту же запись с кодом 200.

**Оговорка про оценку:** PATCH допустим только в IN_PROGRESS. Созданный сегодня курс обязан иметь будущую дату начала,
поэтому весь путь до оценки в одной сессии Swagger без ожидания не пройти.
Покажите `enrollmentCrudIncludesDropReenrollmentAndGrade`: тест управляет Clock, начинает курс и меняет оценку 0 → 100.
Вручную обходить правила прямым изменением дат в рабочей БД для этого не нужно.

**Сказать:** «Операции учитывают жизненный цикл объектов. Отказ сохраняет историю, а опубликованный курс отменяется».

### 2. Правильные HTTP-статусы

**Swagger, Server response:**

| Действие | Результат |
|---|---|
| Создать новый объект | 201 и заголовок Location |
| Прочитать или обновить объект | 200 |
| Удалить допустимый объект или установить связь курса с программой | 204 без тела |
| Передать неверный email или size=51 | 400 |
| Прочитать действительно несуществующий ID | 404 |
| Повторить существующий email или записаться на заполненный курс | 409 |
| Восстановить запись после отказа | 200 с прежним ID |

**Сказать:** «Код ответа соответствует результату операции; ошибка не маскируется ответом 200».

### 3. Spring Data JPA/JDBC

**Код:** откройте `UserRepository`, `CourseRepository` и `EnrollmentRepository`.
Они расширяют `JpaRepository<Entity, Long>`. Покажите `findById`, `save`,
`countByCourseIdAndStatus` и запросы `@Query`.

**Сказать:** «Стандартные операции реализует Spring Data. Hibernate преобразует работу с Entity и JPQL/HQL в SQL».
По HTTP-ответу определить библиотеку доступа к данным нельзя — здесь доказательством служит код.

### 4. Валидация на уровне контроллера и Entity

**Swagger:** отправьте POST пользователя с `email: "bad"` или пустым `fullName`: ожидается 400 и объект errors.
Укажите у курса `capacity: 0` или окончание раньше начала — тоже 400.

**Код:** `@Valid` в контроллере и ограничения в `UserRequest`/`CourseRequest`.
Затем покажите аннотации на `AppUser`/`Course` и `EntityValidationTest`, где Entity проверяются без контроллера.

**Сказать:** «DTO проверяет HTTP-ввод; ограничения Entity проверяются при работе с объектом и при сохранении через JPA».
Ограничения SQL — дополнительный уровень; они не заменяют Bean Validation на Entity.

### 5. Миграции Flyway

**Код:** `src/main/resources/db/migration/V1__create_course_management.sql`.
**Настройки:** `ddl-auto: validate` — Hibernate проверяет схему, не создаёт её.
**БД:** таблица `flyway_schema_history`, поля version, description, success.

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

**Сказать:** «Схема версионируется миграциями. Flyway применяет новые миграции при запуске и хранит историю».
Для показа истории не нужно удалять существующий том БД.

### 6. Интеграционные тесты с Testcontainers и JUnit Jupiter

**Код:** `CourseManagementIT`: `@SpringBootTest`, `@Testcontainers`, `@Container`, `PostgreSQLContainer`,
`@DynamicPropertySource` и методы с `org.junit.jupiter.api.Test`.

**Запуск полного набора:**

```powershell
.\mvnw.cmd clean verify
```

**Показать:** итог Maven, тест гонки `onlyOneConcurrentRequestCanTakeLastSeat`
и отката `failedCancellationRollsBackAlreadyUpdatedEnrollments`.
Тестовая БД находится в отдельном временном контейнере, а MockMvc вызывает HTTP-слой без сетевого порта.

**Сказать:** «Проверяется вся цепочка приложения и поведение настоящего PostgreSQL».

### 7. Переменные окружения

**Код:** `${DB_URL:...}`, `${DB_USERNAME:...}`, `${DB_PASSWORD:...}` в `application.yml`,
а также `environment:` в `docker-compose.yml` и `.env.example`.
Правильное имя секции Compose — **environment**, в формулировке ТЗ опечатка.

**Сказать:** «Тот же образ можно подключить к другой БД без перекомпиляции — параметры задаются окружением».
В локальном запуске JVM `.env` автоматически не читается: используются переменные окружения процесса.

### 8. Сборка Docker и запуск Compose

**Код:** Dockerfile: Maven-этап собирает JAR и запускает модульные тесты,
финальный этап запускает JAR под непривилегированным пользователем.

```powershell
docker compose up -d --build --wait
docker compose ps
```

**Показать:** app и db в состоянии healthy, затем открыть Swagger.
Интеграционные тесты запускаются отдельным `verify`, поскольку им нужен доступ к Docker API.

### 9. База данных в Docker

**Показать:** сервис db в Compose, образ `postgres:17.9-alpine`, именованный том `postgres_data`.
При доступе к psql выполните `SELECT version();`.

**Сказать:** «PostgreSQL работает отдельным контейнером, данные сохраняются в томе при пересоздании контейнера».

### 10. Все списки с пагинацией, максимум 50

**Swagger:** `GET /api/v1/users?page=0&size=1` вернёт не более одной записи,
`page=1&size=1` — следующую. `size=51` и `size=0` возвращают 400.
Аналогичные параметры есть у программ, курсов, записей и программ конкретного курса.

**Код:** `Pagination.MAX_SIZE = 50` и `Pagination.page()`.
**Тест:** `allListsRejectOversizedPages`; для курсора есть отдельная проверка на 53 строках.

**Сказать:** «Ограничение проверяет сервер; пользователь не может обойти его параметром size».

### 11. Бесконечная прокрутка без общего количества

**Swagger:** при наличии двух курсов вызовите `GET /api/v1/courses/scroll?afterId=0&size=1`.
Не задавайте фильтр, который оставляет только один курс.
Ответ содержит content, hasNext=true, nextCursor. Передайте полученный nextCursor в afterId следующего запроса.
В последней порции hasNext=false, nextCursor=null.

Покажите отсутствие totalElements и заголовка X-Total-Count.
**Код:** `CourseRepository.scroll()` возвращает Slice, использует `id > afterId`.
Читается до size+1 строк, клиент получает максимум size.

**Сказать:** «Чтобы узнать, есть ли следующая порция, достаточно дополнительной строки. Общее количество не вычисляется».

### 12. Общее количество в HTTP-заголовке

**Swagger:** `GET /api/v1/users?page=0&size=1` → **Response headers → x-total-count**.
При трёх пользователях тело содержит одну запись, заголовок — 3.
Фактическое значение зависит от текущих данных. Регистр имени HTTP-заголовка несущественен.

**Код:** `PageResponses.of()` берёт `page.getTotalElements()` и добавляет `X-Total-Count`.

### 13. Минимум две сложные транзакции

**Первая — запись студента:** курс вместимостью 1, первый студент получает 201,
второй — 409. Откройте `EnrollmentService.enroll()`: блокировка курса,
проверка состояния и даты, проверка студента, подсчёт мест, вставка/восстановление.

**Обоснование:** два одновременных запроса не должны занять одно место. Транзакция удерживает блокировку
до фиксации результата. `@Transactional` без согласованной блокировки при READ COMMITTED недостаточно.
Последовательные запросы Swagger показывают лимит; гонку доказывает конкурентный тест.

**Вторая — отмена курса:** в конце демонстрации вызовите
PATCH `/api/v1/courses/{id}/status` с `{"status":"CANCELLED"}`, затем GET курса и его активной записи:
оба состояния должны быть CANCELLED.

**Обоснование:** изменение курса и записей должно фиксироваться целиком.
Откройте `CourseService.changeStatus()` и `cancelEnrollments()`.
Тест `failedCancellationRollsBackAlreadyUpdatedEnrollments` вызывает ошибку в БД при обновлении курса
и проверяет откат уже изменённых записей. Обычный успешный запрос сам по себе откат не доказывает.

### 14. Разделение Entity и DTO

**Код:** рядом откройте `Course`, `CourseRequest`, `CourseResponse` и метод `toResponse()` в сервисе.
Entity содержит JPA-связи и объект professor; DTO ответа — professorId.
Статус не принимается через обычный CourseRequest: переходы выполняет отдельная операция.

**Сказать:** «Структура хранения отделена от контракта API; клиент получает только предусмотренные поля».

### 15. Разделение ответственности

**Код:** дерево пакетов user/course/program/enrollment/common.
Пройдите по `EnrollmentController.enroll()` → `EnrollmentService.enroll()` → `EnrollmentRepository`.

**Сказать:** «Контроллер отвечает за HTTP, сервис — за правила и транзакции, репозиторий — за данные».
Это слоистый монолит с группировкой по предметной области, не отдельные микросервисы.

### 16. Enum в БД как строки

**Код:** `@Enumerated(EnumType.STRING)` в AppUser, Course и Enrollment.
**БД:**

```sql
SELECT id, role FROM app_user ORDER BY id LIMIT 50;
SELECT id, status FROM course ORDER BY id LIMIT 50;
SELECT id, status FROM enrollment ORDER BY id LIMIT 50;
```

**Показать:** STUDENT/PROFESSOR, DRAFT/ENROLLMENT_OPEN, ENROLLED и т. п.
Строка в JSON не доказывает формат в БД — покажите запрос к БД или mapping вместе с миграцией.

**Сказать:** «Сохраняются названия enum, поэтому перестановка констант не меняет смысл существующих записей».
Переименование константы всё равно потребует согласования с данными и миграцией.

### 17. Обработка ошибок

**Swagger:** прочитайте отсутствующего пользователя — 404 с detail;
отправьте неверный email — 400 с errors; запишите второго студента на заполненный курс — 409 с объяснением.
Ответ имеет Content-Type `application/problem+json`.

**Код:** `ApiExceptionHandler`, `@RestControllerAdvice`, `@ExceptionHandler`.
**Сказать:** «Все контроллеры используют единый формат ошибок; непредвиденная ошибка логируется,
а клиент не получает SQL и стек исключения».

### 18. Согласованная архитектура БД

**Показать:** ER-диаграмму в README и миграцию, пять таблиц:
app_user, course, study_program, course_program, enrollment.

**Сказать:** «Модель согласована: курс ведёт один преподаватель; курс входит в несколько программ;
запись хранит участие и оценку конкретного студента. Вместимость общая для всех программ».
Свяжите это с ранее полученным согласованием преподавателя: Swagger не подтверждает факт согласования.

### 19. Все три типа связей

| Тип | Показ в коде | Показ через API |
|---|---|---|
| One-to-Many / Many-to-One | `Course.enrollments` с `@OneToMany`, `Enrollment.course` с `@ManyToOne` | GET записей с фильтром courseId |
| Many-to-Many | `Course.programs`, `@ManyToMany`, `@JoinTable` | Первый курс в двух программах; первая программа содержит два курса |
| Many-to-Many с полями | Отдельная Entity Enrollment с двумя `@ManyToOne` | GET записи показывает studentId, courseId, status, grade, enrolledAt |

Для обычной M:N используйте GET `/api/v1/courses/{id}/programs` и GET `/api/v1/courses?programId=...`.
Для M:N с дополнительными полями grade может быть null до оценивания: связь уже содержит status и enrolledAt.

### 20. Общий OpenAPI 3 / Swagger

**Swagger:** покажите четыре группы User, Course, Program, Enrollment в одной странице.
**Спецификация:** [GET /v3/api-docs](http://localhost:8080/v3/api-docs) содержит openapi=3.0.1 и все маршруты.
**Код:** springdoc в pom.xml, общий OpenAPI в ApplicationConfiguration,
аннотации Operation/ApiResponse в контроллерах.

**Сказать:** «Документация формируется из контроллеров и DTO, все операции находятся в одной спецификации».
Она проверяется тестом `exposesOneOpenApiDocumentAndHealthEndpoint`.

## Как открыть PostgreSQL без дополнительных программ

Из папки проекта, с настройками по умолчанию:

```powershell
docker compose exec db psql -U course_app -d course_management
```

Если POSTGRES_USER/POSTGRES_DB изменены, подставьте соответствующие значения.
В psql:

```sql
SELECT version();
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT id, role FROM app_user ORDER BY id LIMIT 50;
SELECT id, title, status FROM course ORDER BY id LIMIT 50;
SELECT course_id, program_id FROM course_program ORDER BY course_id, program_id LIMIT 50;
SELECT id, student_id, course_id, status, grade FROM enrollment ORDER BY id LIMIT 50;
```

`\dt` покажет таблицы, `\d enrollment` — структуру и ограничения, `\q` завершит psql.
Это команды просмотра; они не изменяют данные.

## Удобный порядок показа на 10–15 минут

1. Compose: два healthy-контейнера; затем общий Swagger.
2. CRUD на отдельной программе или черновике курса.
3. Ошибки 400, 404, 409 и нормальные 200, 201, 204.
4. Связать курсы с программами, показать обе стороны M:N.
5. Показать обычную пагинацию и курсор с size=1.
6. Записать студента на последнее место, показать отказ второму.
7. Отказаться через DELETE, восстановить ту же запись через POST.
8. Отменить курс и показать CANCELLED у его записи.
9. Открыть два транзакционных метода и тесты гонки/отката.
10. Показать Entity/DTO, ограничения, миграцию, таблицы и строковые enum.
11. Завершить отчётом тестов и покрытия `target/site/jacoco/index.html`.

Отчёт JaCoCo после `clean verify` — доказательство покрытия текущей сборки.
Сохранённое в README число относится к указанной там дате проверки.
