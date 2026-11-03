# Changes
- Exam Set, Full Mock (65Q/100 marks) and Subject Mock now come from the AI backend in real GATE CS style (hard, PYQ-like, MCQ/MSQ/NAT mix). Stored easy bank only fills gaps if AI fails.
- Revision / Error-Monster sets also use the AI backend (hard, same concept with new values).
- Daily Learning topic quest/lesson XP removed (Learn button removed from home).
- Daily quest = Subject Mock (25 Q). Full Mock quest every 3rd day.
- XP now = marks: subject mock 10 + 5/net mark (+40 at 60%, +100 at 75%); full mock 30 + 4/net mark (+bonus); exam set 10 + 3/net mark; workout 20 partial / 40 done. Levels follow XP.
- Backend: new `mode` (exam|mock|revision) prompt; redeploy backend/ and keep APP_TOKEN + a strong model (e.g. ANTHROPIC_API_KEY or GEMINI_MODEL=gemini-2.5-pro). Rate limit raised to 60/min.
