
<img width="2824" height="1364" alt="image" src="https://github.com/user-attachments/assets/a3d84e70-d989-468b-b441-73004807b38d" />

## О проекте

**Tree Description** — плагин для IDE JetBrains: заметки к папкам и файлам
прямо в дереве проекта, чтобы быстрее разбираться в структуре кода.

Это форк [JaysonX-Tech/tree-description-plugin](https://github.com/JaysonX-Tech/tree-description-plugin):
китайский интерфейс заменён русским, рабочие файлы перенесены в `.idea`.

## Возможности

| Что | Сочетание клавиш (Windows / Mac) |
| --- | --- |
| Добавить заметку к пакету или файлу | `Alt + Shift + A` / `Option + Shift + A` |
| Заметка из комментария класса | `Alt + Shift + E` / `Option + Shift + E` |
| Поиск по заметкам | `Alt + Shift + F` / `Option + Shift + F` |

- Заметки видны в дереве проекта сразу после правки.
- Встроенные библиотеки сопоставлений для популярных фреймворков.
- Шаблоны по имени файла и пакета.
- Цвет заметки.
- Интерфейс на русском и английском.

## Сборка

Нужен JDK 17 или 21. Gradle 8.14 не запускается на Java 25 и новее,
JBR из WebStorm 2025.2+ (Java 25) для сборки не подходит.

1. Указать JDK для текущего окна терминала (путь — свой):

   ```bash
   export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21"
   ```

2. Собрать плагин из корня репозитория:

   ```bash
   ./gradlew buildPlugin
   ```

3. Готовый архив: `build/distributions/tree-description-plugin-<версия>.zip`.
   Версия задаётся в `gradle.properties` (`pluginVersion`).

Первая сборка скачивает IntelliJ IDEA Community 2023.1.5 (около 1 ГБ) — это
SDK, против которого компилируется плагин; дальше берётся из кэша Gradle.

## Установка в IDE

1. `Settings` → `Plugins` → шестерёнка → `Install Plugin from Disk...`.
2. Выбрать zip из `build/distributions/` (не распаковывать).
3. Перезапустить IDE.

Старая версия заменится автоматически: ID плагина прежний
(`com.github.chinese-annotations`).

## Запуск в отдельной IDE для отладки

```bash
./gradlew runIde
```

Открывается отдельная IntelliJ IDEA Community с установленным плагином;
ваши настройки и проекты не затрагиваются.

## Проверка перевода

```bash
python scripts/find_non_english.py
```

Печатает строки с текстом не на английском и не на русском; код выхода 1,
если такие строки есть.

## Где хранятся заметки

| Что | Где |
| --- | --- |
| Заметки пользователя | `.idea/tree-description/local-description.json` |
| Скачанные библиотеки сопоставлений | `.idea/tree-description/*.json` |
| Путь в коде | `MappingsPaths.MAPPINGS_DIR` |

- Раньше папка называлась `.td-maps` и лежала в корне проекта.
  При открытии проекта плагин сам переносит её содержимое в `.idea/tree-description/`.
- Если файл с тем же именем уже есть на новом месте, старый остаётся в `.td-maps`
  (в журнале IDE будет предупреждение) — сравните и перенесите вручную.
- Если `.idea` исключена в `.gitignore`, а заметки нужны команде, замените строку
  `.idea/` на две: `.idea/*` и `!.idea/tree-description/`.
  Исключение внутри полностью игнорируемой папки (`.idea/`) git не применяет.

## Частые проблемы

| Симптом | Причина | Что сделать |
| --- | --- | --- |
| `What went wrong: 25.0.2` при сборке | Gradle запущен на Java 25 | указать `JAVA_HOME` на JDK 17/21 |
| IDE пишет, что плагин несовместим | `untilBuild` в `build.gradle.kts` ниже сборки IDE | поднять `untilBuild` |
| Заметки пропали после обновления | файлы остались в старой `.td-maps` из-за совпадения имён | перенести вручную в `.idea/tree-description/` |

---

## Project Introduction

**Tree Description** adds notes to the IntelliJ IDEA project tree, helping
developers quickly understand project structure and code meaning.

### Core Features
- **Smart Annotations**: Add custom path annotations to package names and files
  (`Alt + Shift + A` / `Option + Shift + A`)
- **Real-time Display**: Display annotation descriptions in real-time in project tree
- **Extract Comments**: Support extracting class comments into file comments
  (`Alt + Shift + E` / `Option + Shift + E`)
- **Mapping Library**: Pre-built common framework mapping libraries
- **Auto Matching**: Support filename and package name pattern matching
- **Internationalization**: Russian and English interface switching
- **Color Marking**: Support custom annotation colors

### Use Cases
- **Learning Large Open Source Projects**: Quickly understand project structure and module responsibilities
- **Team Collaboration**: Unify project naming conventions and code understanding
- **Programming Education**: Provide clear project structure explanations for students
- **Code Review**: Quickly identify file functions and package purposes

### Build and install
`./gradlew buildPlugin` with JDK 17 or 21, then
`Settings` → `Plugins` → `Install Plugin from Disk...` → `build/distributions/*.zip`.

### Contributing
Welcome to submit Issues and Pull Requests!
