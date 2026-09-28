# MiniBrain

Local-first приложение, которое показывает историю того, **как я становлюсь лучше**:
карта навыков (skill tree) + доказательства (Evidence) реального понимания + обмен контекстом с AI.

- Бриф продукта и архитектуры: [docs/brief.md](docs/brief.md)
- План по шагам: [docs/roadmap.md](docs/roadmap.md)
- Журнал решений: [docs/decisions.md](docs/decisions.md)
- Как мы работаем (Claude): [CLAUDE.md](CLAUDE.md)

## Структура

```text
backend/     Spring Boot (Java 21, Maven, Spring JDBC, SQLite)
frontend/    React + TypeScript + Vite — появится на шаге 7
docs/        бриф, roadmap, решения
data/        minibrain.db (не в Git)
exports/     current.json и snapshots/
imports/     файлы MINIBRAIN_UPDATE
backups/     локальные бэкапы (не в Git)
```

## Запуск

Требуется JDK 21 и Maven.

```bash
cd backend
mvn spring-boot:run     # http://localhost:8080
mvn test
```

База создаётся в `data/minibrain.db` при первом обращении к ней
(путь задаётся относительно `backend/`, переопределяется через `MINIBRAIN_DB_URL`).
