#!/usr/bin/env python3
"""Parse 'GATE CS 2027 - 114 Day Transformation Plan.docx' into plan_114.json.

Usage: python3 -I parse_plan.py <plan.docx> <out.json>
The docx is the source of truth; nothing is invented. Every field is copied
from the document text and the script validates the structure (114 consecutive
dates, the 20 listed mock days, and the 1-4-7 due lists).
"""
import json, re, sys, datetime
import docx

MOCK_DAYS = [14,21,28,35,42,49,56,63,70,77,81,85,89,93,97,100,103,106,109,112]
HEAD = re.compile(r'^Day (\d{3}) — (\w{3}), (\d{2} \w{3} \d{4})( — FULL MOCK)?$')
DUE = re.compile(r'^Day (\d+): (.+?) — (.+)$')

def split_st(s):
    a, _, b = s.partition(' — ')
    return a.strip(), b.strip()

def main(src, out):
    paras = [p.text.strip() for p in docx.Document(src).paragraphs if p.text.strip()]
    days, cur = [], None
    for t in paras:
        m = HEAD.match(t)
        if m:
            cur = dict(day=int(m[1]), weekday=m[2],
                       date=datetime.datetime.strptime(m[3], '%d %b %Y').date().isoformat(),
                       fullMock=bool(m[4]), due=[], dueNote=None, weeklyReview=False,
                       weeklyReviewText=None, mockPhase=None)
            days.append(cur); continue
        if cur is None: continue
        if t.startswith('LEARN / CONSOLIDATE:'):
            cur['learnSubject'], cur['learnTopic'] = split_st(t.split(':', 1)[1].strip())
        elif t.startswith('REVISION SUBJECT:'):
            cur['revisionSubject'], cur['revisionTopic'] = split_st(t.split(':', 1)[1].strip())
        elif t.startswith('GA MINI-SET:'):
            body = t.split(':', 1)[1].strip()
            m2 = re.match(r'(.+?); (\d+) questions \((\d+)–(\d+) minutes\)\.?$', body)
            cur['gaTopic'], cur['gaQuestions'] = m2[1], int(m2[2])
            cur['gaMinMinutes'], cur['gaMaxMinutes'] = int(m2[3]), int(m2[4])
        elif t.startswith('147 DUE'):
            pass
        elif t.startswith('SETS:'):
            cur['setsText'] = t[5:].strip()
            P = re.search(r'P = (\d+)', t); R = re.search(r'R = (\d+)(?:–(\d+))?', t)
            E = re.search(r'E = (\d+) timed', t)
            cur['setP'] = int(P[1]) if P else None
            cur['setR'] = int(R[1]) if R else None
            cur['setRMax'] = int(R[2]) if R and R[2] else None
            cur['setE'] = int(E[1]) if E else None
            if 'full 65-question' in t:
                cur['setE'] = None; cur['mockQuestions'] = 65; cur['mockMarks'] = 100; cur['mockMinutes'] = 180
        elif t.startswith('WORKOUT:'):
            cur['workout'] = t.split(':', 1)[1].strip()
        elif t.startswith('LIFE / CAREER QUEST'):
            body = t.split('):', 1)[1].strip()
            cat, _, task = body.partition(': ')
            cur['lifeCategory'], cur['lifeTask'] = cat, task
        elif t.startswith('WEEKLY REVIEW:'):
            cur['weeklyReview'] = True; cur['weeklyReviewText'] = t.split(':', 1)[1].strip()
        elif t.startswith('MOCK PHASE:'):
            cur['mockPhase'] = t.split(':', 1)[1].strip()
        elif t.startswith('END-OF-DAY CHECK:'):
            cur['endOfDay'] = t.split(':', 1)[1].strip()
        elif t.startswith('No earlier') or t.startswith('No '):
            cur['dueNote'] = t
        elif DUE.match(t) and cur['day'] and 'learnTopic' in cur and 'setsText' not in cur:
            m3 = DUE.match(t)
            cur['due'].append(dict(fromDay=int(m3[1]), subject=m3[2], topic=m3[3]))
        elif t.startswith('Error Monster —'):
            break
    # ---- validation ----
    assert len(days) == 114, len(days)
    d0 = datetime.date(2026, 10, 11)
    for i, d in enumerate(days):
        assert d['day'] == i + 1
        assert d['date'] == (d0 + datetime.timedelta(days=i)).isoformat(), d['day']
        for k in ('learnSubject','learnTopic','revisionSubject','gaTopic','setsText','workout','lifeTask'):
            assert k in d, (d['day'], k)
    assert d['date'] == '2027-02-01'
    mocks = [d['day'] for d in days if d['fullMock']]
    assert mocks == MOCK_DAYS, mocks
    by = {d['day']: d for d in days}
    mismatches = []
    for d in days:
        expect = [d['day'] - k for k in (1, 4, 7) if d['day'] - k >= 1]
        got = [x['fromDay'] for x in d['due']]
        if sorted(expect) != sorted(got): mismatches.append((d['day'], expect, got))
        for x in d['due']:
            src = by[x['fromDay']]
            if not (src['learnSubject'] == x['subject'] and src['learnTopic'] == x['topic']):
                mismatches.append((d['day'], 'topic-mismatch', x['fromDay']))
    json.dump(dict(source='GATE CS 2027 — 114-Day Transformation Plan.docx', start='2026-10-11',
                   end='2027-02-01', mockDays=MOCK_DAYS, days=days), open(out, 'w'),
              ensure_ascii=False, indent=1)
    print('days', len(days), 'mocks', len(mocks), 'due-rule mismatches', len(mismatches))
    for m in mismatches[:20]: print(' ', m)
main(sys.argv[1], sys.argv[2])
