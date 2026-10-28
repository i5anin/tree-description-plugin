"""Поиск текста не на английском и не на русском в исходниках плагина.

Зачем: плагин пришёл из китайского репозитория, интерфейс и комментарии
переведены на русский. Скрипт показывает, что осталось непереведённым,
и годится как проверка перед коммитом: код выхода 1, если что-то нашлось.

Что считается находкой:
- буква не из латиницы ASCII и не из кириллицы (иероглифы, кана, хангыль,
  латиница с диакритикой и т. п.);
- полноширинная и CJK-пунктуация (，。：（）【】), которая остаётся
  после китайского текста даже без иероглифов.

Эмодзи, стрелки, тире и прочие символы не считаются языком и пропускаются.

Запуск из корня репозитория:
    python scripts/find_non_english.py            # весь репозиторий
    python scripts/find_non_english.py src README.md
"""

import subprocess
import sys
import unicodedata
from pathlib import Path

# Расширения текстовых файлов, которые проверяем; бинарные (jar, png) пропускаем.
TEXT_EXTENSIONS = {
    ".java", ".kt", ".kts", ".xml", ".properties", ".md", ".json", ".txt",
}

# CJK-пунктуация и полноширинные формы: U+3000–U+303F и U+FF00–U+FFEF.
CJK_PUNCTUATION_RANGES = ((0x3000, 0x303F), (0xFF00, 0xFFEF))


def is_foreign_char(char: str) -> bool:
    """Возвращает True, если символ — признак текста не на английском/русском.

    Используется в find_foreign_fragments для каждого символа строки.
    """
    code = ord(char)
    if code < 128:
        return False
    if any(start <= code <= end for start, end in CJK_PUNCTUATION_RANGES):
        return True
    if not unicodedata.category(char).startswith("L"):
        return False
    is_cyrillic = "CYRILLIC" in unicodedata.name(char, "")
    return not is_cyrillic


def find_foreign_fragments(line: str) -> list[str]:
    """Собирает подряд идущие «чужие» символы строки в фрагменты.

    Фрагменты нужны только для читаемого отчёта: видно, что именно
    осталось, а не только номер строки.
    """
    fragments = []
    current = ""
    for char in line:
        if is_foreign_char(char):
            current += char
            continue
        if current:
            fragments.append(current)
            current = ""
    if current:
        fragments.append(current)
    return fragments


def list_files(targets: list[str]) -> list[Path]:
    """Возвращает проверяемые файлы: из аргументов или все файлы под git.

    Под git берём `git ls-files`, чтобы не лезть в build/ и .gradle/.
    """
    if targets:
        paths = []
        for target in targets:
            path = Path(target)
            paths.extend(path.rglob("*") if path.is_dir() else [path])
    else:
        output = subprocess.run(
            ["git", "ls-files"], capture_output=True, text=True, check=True
        ).stdout
        paths = [Path(name) for name in output.splitlines()]
    return [p for p in paths if p.is_file() and p.suffix in TEXT_EXTENSIONS]


def main() -> int:
    """Печатает находки в формате `файл:строка: фрагменты` и считает их."""
    sys.stdout.reconfigure(encoding="utf-8")
    found_lines = 0
    for path in list_files(sys.argv[1:]):
        try:
            text = path.read_text(encoding="utf-8-sig")
        except UnicodeDecodeError:
            print(f"{path.as_posix()}: не UTF-8, пропущен", file=sys.stderr)
            continue
        for number, line in enumerate(text.splitlines(), start=1):
            fragments = find_foreign_fragments(line)
            if not fragments:
                continue
            found_lines += 1
            print(f"{path.as_posix()}:{number}: {' | '.join(fragments)}")
    print(f"\nСтрок с текстом не на английском/русском: {found_lines}")
    return 1 if found_lines else 0


if __name__ == "__main__":
    sys.exit(main())
