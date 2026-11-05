"""One-time migration of Java text literals into the Minecraft language JSON.

Uses a lexical scanner so comments, escapes and character literals are preserved.
Existing keys are never renumbered. Re-run only when intentionally migrating old code.
"""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
LANG = ROOT / 'src/main/resources/assets/elementalcircuits/lang'
HAN = re.compile(r'[\u3400-\u9fff]')
TOKEN = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')


def java_string(token):
    # The existing literals use JSON-compatible escapes, except Java's apostrophe escape.
    return json.loads(token.replace("\\'", "'"))


def main():
    catalog = json.loads((LANG / 'zh_cn.json').read_text(encoding='utf-8'))
    english_path = LANG / 'en_us.json'
    english = json.loads(english_path.read_text(encoding='utf-8'))
    extracted = 0
    for path in sorted((ROOT / 'src/main/java').rglob('*.java')):
        source = path.read_text(encoding='utf-8')
        identity = str(path.relative_to(ROOT / 'src/main/java/dev/elementalcircuits')).replace('\\', '/')
        stem = re.sub(r'(?<!^)(?=[A-Z])', '_', identity.removesuffix('.java').replace('/', '.')).lower()
        prefix = 'text.elementalcircuits.' + stem + '.'
        index = 1

        def replace(match):
            nonlocal index, extracted
            token = match.group()
            if not token.startswith('"') or token.startswith('"""') or not HAN.search(token):
                return token
            text = java_string(token)
            existing = next((key for key, value in catalog.items() if key.startswith(prefix) and value == text), None)
            if existing is None:
                while prefix + f'{index:03}' in catalog:
                    index += 1
                existing = prefix + f'{index:03}'
                catalog[existing] = text
                index += 1
            extracted += 1
            lookup = 'dev.elementalcircuits.text.Texts.get'
            if '/client/' in '/' + identity:
                lookup = 'dev.elementalcircuits.client.text.UiText.string'
            return lookup + '(' + json.dumps(existing) + ')'

        updated = TOKEN.sub(replace, source)
        if updated != source:
            path.write_text(updated, encoding='utf-8', newline='\n')
    (LANG / 'zh_cn.json').write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    for key, value in catalog.items():
        english.setdefault(key, value)
    english_path.write_text(json.dumps(english, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Externalized {extracted} literals; {len(catalog)} text keys')


if __name__ == '__main__':
    main()
