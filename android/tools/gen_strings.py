"""Regenerate values/strings.xml and values-am/strings.xml from strings_table.py.
Edit wording in strings_table.py (English, Amharic), then run:  python3 tools/gen_strings.py"""
import os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
from strings_table import S, PLURALS
RES = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res')

def esc(t):
    return t.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace("'", "\\'").replace('"', '\\"')

for lang, idx, path in (('en', 0, 'values/strings.xml'), ('am', 1, 'values-am/strings.xml')):
    note = 'Native-speaker review welcome: edit tools/strings_table.py, then rerun tools/gen_strings.py.' if lang == 'am' else ''
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           f'<!-- Generated from one table so English and Amharic always have the same keys. {note} -->', '<resources>']
    for k, v in S.items():
        t = v[idx]
        fmt = '' if re.search(r'%\d\$', t) or '%' not in t else ' formatted="false"'
        out.append(f'    <string name="{k}"{fmt}>{esc(t)}</string>')
    for k, (en, am) in PLURALS.items():
        one, other = (en, am)[idx]
        out.append(f'    <plurals name="{k}">\n        <item quantity="one">{esc(one)}</item>\n        <item quantity="other">{esc(other)}</item>\n    </plurals>')
    out.append('</resources>\n')
    open(os.path.join(RES, path), 'w').write('\n'.join(out))
print(f'{len(S)} strings written for en + am')
