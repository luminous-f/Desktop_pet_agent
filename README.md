# Desktop Pet Agent

Local Java desktop pet agent using JavaFX, LangChain4j, DeepSeek-compatible chat APIs, MyBatis/MySQL, Redis Stack RediSearch vector RAG, QWeather, and an optional search API.

## Run Split Backend and Windows Client

1. Install JDK 17 and Maven.
2. Copy `.env.example` values into your shell environment or into `config/application.properties`.
3. Create the MySQL database named by `MYSQL_DATABASE`.
4. In Ubuntu/WSL, start the local backend:

```bash
mvn exec:java -Dexec.mainClass=com.desktoppet.BackendApplication
```

The backend listens on `127.0.0.1:8080` by default. Override with `SERVER_HOST` and `SERVER_PORT`.

5. In Windows, start the JavaFX client and point it at the backend:

```bash
set BACKEND_BASE_URL=http://127.0.0.1:8080
mvn javafx:run
```

The client uses native JavaFX text controls, so Windows input methods such as Sogou IME handle Chinese input directly. The client does not connect to MySQL, Redis, model APIs, weather, news, or local file organization services directly.

## Local HTTP API

- `GET /api/startup`: startup notice, weather, profile, and today's schedule.
- `POST /api/chat`: `{ "message": "..." }` -> `{ "reply": "...", "expression": "speaking" }`.
- `GET /api/profile`: latest profile from MySQL.
- `PUT /api/profile`: `{ "profileText": "..." }`, saves a new MySQL profile row and returns the latest profile.
- `GET /api/schedule/today`: today's `schedule_items`.
- `POST /api/schedule/today`: `{ "title": "..." }`, writes to MySQL and returns the refreshed list. Database failures return an error response instead of a false success.
- `GET /api/news/daily`: daily news summary.
- `GET /api/files/allowed-roots`: current file organization whitelist.
- `PUT /api/files/allowed-roots`: `{ "roots": ["/mnt/d/papers"] }`. Replaces the global whitelist, persists it to `config/application.properties`, and takes effect immediately.
- `POST /api/files/organize/preview`: `{ "sourceRoot": "/mnt/d", "extensions": "pdf", "instruction": "请把与密钥生成有关的 PDF 文献分类" }`. The backend scans only paths under the current whitelist, extracts PDF text, asks the model whether each file is relevant literature, and returns a preview id plus candidate classifications.
- `POST /api/files/organize/confirm`: `{ "previewId": "..." }`. Copies preview candidates into `PET_EXPORT_DIR/job-时间戳/<分类>/` and writes `目录.xlsx`.
- `POST /api/files/organize`: compatibility endpoint for one-step organize; new UI and chat flow use preview/confirm.

## AI File Organization

- Chat supports natural language requests such as `请把D盘中所有与密钥生成有关的pdf文献分类`.
- Drive words and Windows paths are converted for the WSL backend, for example `D盘` or `D:\papers` becomes `/mnt/d` or `/mnt/d/papers`.
- Set the whitelist from chat with phrases like `把D盘加入白名单` or `设置文件白名单为D:\papers`. Each update replaces the previous whitelist and persists to `config/application.properties`.
- The `整理` dialog also shows the current whitelist and can replace it with the current source directory.
- The UI `整理` button opens a form for source directory, extensions, and natural-language classification requirements, then asks for confirmation before copying files.
- Original files are never moved or deleted.

## Agent Skills

- Chat requests are routed by a LangChain4j JSON router, not by hard-coded keyword branches.
- Runtime skills: weather, news, schedule, file organization, file whitelist, and user profile.
- Weather and news run directly when the user asks for them naturally.
- Schedule queries run directly; adding a schedule asks for confirmation before writing MySQL.
- File organization always creates a preview first; `确认整理` copies files and writes `目录.xlsx`.
- User profile can be queried or explicitly replaced through chat, and stable profile facts are extracted from normal chat messages.

## Required Local Services

- MySQL: raw conversations, session summaries, long-term memories, user profile, preferences, schedule items, resources, file organization jobs.
- Redis Stack with RediSearch: vector RAG store. Defaults use DashScope-compatible Qwen `qwen3-vl-embedding`; set `EMBEDDING_API_KEY` in the environment.
- DeepSeek: configured through OpenAI-compatible `baseUrl`, `apiKey`, and `modelName`.
- QWeather: fixed weather API provider. Prefer `WEATHER_API_KEY`; `QWEATHER_API_KEY` remains supported for compatibility.
- Search API: optional; default provider placeholder is Tavily-compatible HTTP.

## Memory and RAG

- Short-term memory stays in process for the active desktop session.
- Every message is saved to MySQL as raw conversation history.
- On shutdown, and when the prompt approaches the configured context budget, short-term memory is summarized into MySQL `session_summaries`.
- Important user facts are stored in `long_term_memories` and recalled by weighted score.
- RAG uses parent documents plus child chunks in RediSearch. Defaults: 900 characters per chunk, 120 characters overlap, top 5 retrieval.

## Resource Placeholders

Use `assets/pet/default/` for pet expressions and `assets/character/Castorice/` for character/world files. The first UI scaffold uses emoji if image assets are not present.
