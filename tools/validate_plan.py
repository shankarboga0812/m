#!/usr/bin/env python3
"""Validate the structured study plan against key invariants."""
import json, datetime
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'plan'/'114_day_plan.json'
data=json.loads(p.read_text())
days=data['days']
assert len(days)==114, f"Expected 114 days, got {len(days)}"
assert [d['day'] for d in days]==list(range(1,115)), 'Day numbers must be 1..114'
assert days[0]['date_label'].endswith('11 Oct 2026')
assert days[-1]['date_label'].endswith('01 Feb 2027')
assert data['mock_days']==[14,21,28,35,42,49,56,63,70,77,81,85,89,93,97,100,103,106,109,112]
assert data['revision_offsets_days']==[1,4,7]
assert all(set(d['sets'])=={'P','R','E'} for d in days), 'Every day must preserve P/R/E counts'
assert all(all(isinstance(v,int) and v>=0 for v in d['sets'].values()) for d in days)
assert all(d['learning'] and d['workout'] for d in days), 'Missing learning topic/workout'
print(f"PASS: {len(days)} days; {len(data['mock_days'])} mock days; 1-4-7 rule; P/R/E counts and daily fields valid.")
