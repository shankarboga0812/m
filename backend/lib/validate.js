'use strict';
const TYPES = ['MCQ', 'MSQ', 'NAT'];
const DIFFS = ['EASY', 'MEDIUM', 'HARD'];
const str = (v, max) => typeof v === 'string' && v.trim().length > 0 && v.length <= max;

/** Validates the app's request body. Returns {ok, value} or {ok:false, error}. */
function validateGenerateRequest(b, maxQuestions) {
  if (!b || typeof b !== 'object' || Array.isArray(b)) return { ok: false, error: 'Body must be a JSON object' };
  if (!str(b.subject, 80)) return { ok: false, error: 'subject is required (max 80 chars)' };
  if (!str(b.topic, 300)) return { ok: false, error: 'topic is required (max 300 chars)' };
  const count = Number(b.count);
  if (!Number.isInteger(count) || count < 1 || count > maxQuestions) return { ok: false, error: `count must be an integer 1..${maxQuestions}` };
  const difficulty = String(b.difficulty || 'MEDIUM').toUpperCase();
  if (!DIFFS.includes(difficulty)) return { ok: false, error: 'difficulty must be EASY, MEDIUM or HARD' };
  const questionType = String(b.questionType || 'MCQ').toUpperCase();
  if (!TYPES.includes(questionType)) return { ok: false, error: 'questionType must be MCQ, MSQ or NAT' };
  const marks = b.marks == null ? 1 : Number(b.marks);
  if (![1, 2].includes(marks)) return { ok: false, error: 'marks must be 1 or 2' };
  const list = (v, n, m) => (Array.isArray(v) ? v.filter((x) => typeof x === 'string').map((x) => x.slice(0, m)).slice(0, n) : []);
  return { ok: true, value: {
    subject: b.subject.trim(), topic: b.topic.trim(), subtopic: typeof b.subtopic === 'string' ? b.subtopic.slice(0, 600) : '',
    mode: ['exam', 'mock', 'revision'].includes(String(b.mode || '').toLowerCase()) ? String(b.mode).toLowerCase() : null,
    learningObjective: typeof b.learningObjective === 'string' ? b.learningObjective.slice(0, 600) : '',
    difficulty, questionType, count, marks,
    previousMistakes: list(b.previousMistakes, 10, 200), coveredConcepts: list(b.coveredConcepts, 20, 120),
    explanationFormat: typeof b.explanationFormat === 'string' ? b.explanationFormat.slice(0, 200) : 'step-by-step',
  } };
}

/** Server-side structural check of one AI question; mirrors the app's validator so bad data is dropped early. */
function checkQuestion(q) {
  const errs = [];
  if (!q || typeof q !== 'object') return ['not an object'];
  if (!TYPES.includes(q.type)) errs.push('bad type');
  if (!str(q.statement, 4000)) errs.push('missing statement');
  if (!str(q.solution, 6000)) errs.push('missing solution');
  if (!str(q.concept, 200)) errs.push('missing concept');
  if (q.type === 'MCQ' || q.type === 'MSQ') {
    if (!Array.isArray(q.options) || q.options.length < 2 || q.options.some((o) => typeof o !== 'string' || !o.trim())) errs.push('bad options');
    else {
      const norm = q.options.map((o) => o.toLowerCase().replace(/\s+/g, ' ').trim());
      if (new Set(norm).size !== norm.length) errs.push('duplicate options');
      if (!Array.isArray(q.correct) || q.correct.length === 0 || q.correct.some((i) => !Number.isInteger(i) || i < 0 || i >= q.options.length)) errs.push('bad answer key');
      else if (q.type === 'MCQ' && (q.correct.length !== 1 || q.options.length !== 4)) errs.push('MCQ needs 4 options and exactly one key');
    }
  } else if (q.type === 'NAT') {
    const a = q.answer;
    const ok = typeof a === 'number' || (a && typeof a === 'object' && typeof a.low === 'number' && typeof a.high === 'number' && a.low <= a.high);
    if (!ok) errs.push('bad numeric answer');
  }
  return errs;
}

/** Pull the first JSON object out of a model reply (tolerates ``` fences and chatter). */
function extractJson(text) {
  if (typeof text !== 'string') return null;
  const t = text.replace(/```json|```/gi, '');
  const start = t.indexOf('{');
  if (start < 0) return null;
  let depth = 0, inStr = false, esc = false;
  for (let i = start; i < t.length; i++) {
    const c = t[i];
    if (inStr) { if (esc) esc = false; else if (c === '\\') esc = true; else if (c === '"') inStr = false; continue; }
    if (c === '"') inStr = true; else if (c === '{') depth++; else if (c === '}') { depth--; if (depth === 0) { try { return JSON.parse(t.slice(start, i + 1)); } catch { return null; } } }
  }
  return null;
}
module.exports = { validateGenerateRequest, checkQuestion, extractJson };
