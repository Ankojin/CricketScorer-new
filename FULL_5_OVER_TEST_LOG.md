# Cric Score — Full 5-Over Test Log

**App version (footer):** _______________  
**version.properties:** _______________  
**Build / date:** _______________  
**Device / emulator:** _______________  
**Tester:** _______________  
**Git commit (optional):** _______________  

**Architecture note (product rule):**  
Global Player list is the **Master List**. Any player added during a match (local / Manage Squad) must also be added to the Global Master List and linked into the tournament series roster.

---

## Pre-test setup

| Step | Action | Result (PASS/FAIL) | Notes |
|------|--------|--------------------|-------|
| P1 | Uninstall previous app from emulator/device | | |
| P2 | Clean Project + Run | | |
| P3 | Footer version matches version.properties | | |
| P4 | Open tournament with 2 teams + enough players | | |
| P5 | Confirm Global Playlist has existing players | | |

---

## A. Setup (toss + settings)

| Step | Action | Result | Notes |
|------|--------|--------|-------|
| A1 | Schedule match, **5 overs**, max 2 overs/bowler | | |
| A2 | Open match → Match Settings / Toss appears | | |
| A3 | Flip coin (optional) or set winner + Bat/Bowl | | |
| A4 | Change overs if needed → **Save & Start** | | |
| A5 | Settings dialog **closes** | | |
| A6 | Prompted to select striker / non-striker / bowler | | |
| A7 | Select players and enter live scoring | | |

**Section A:** ☐ PASS ☐ FAIL  

---

## B. Scoring (1st innings)

| Step | Action | Result | Notes |
|------|--------|--------|-------|
| B1 | Score 0, 1, 2, 3 | | |
| B2 | Score **4** — appears blue in Overs tab | | |
| B3 | Score **6** — appears purple in Overs tab | | |
| B4 | Wide + No-ball — orange; do not count as legal ball incorrectly | | |
| B5 | Take a wicket (Bowled/Caught) — **W** red in Overs | | |
| B6 | End of Over popup shows runs, wickets, **team score** | | |
| B7 | Undo 1–2 balls — score/history correct | | |
| B8 | Complete at least 2 full overs | | |

**Section B:** ☐ PASS ☐ FAIL  

---

## C. Manage Squad mid-match (Global = Master List)

| Step | Action | Result | Notes |
|------|--------|--------|-------|
| C1 | Open Manage Squad | | |
| C2 | **+ on Team A** → add brand-new local name | | |
| C3 | Player appears under **Team A only** | | |
| C4 | Same player appears in **Global Master List** | | |
| C5 | **+ on Team B** → add different new name | | |
| C6 | Player appears under **Team B only** | | |
| C7 | New Team B player also in **Global Master List** | | |
| C8 | Add from Global Playlist to a team — works | | |
| C9 | Try remove **current striker** — blocked + Toast | | |
| C10 | Try remove **current bowler** — blocked + Toast | | |
| C11 | Remove a **bench** player — succeeds | | |
| C12 | Leave match → reopen match — added players still present | | |

**Section C:** ☐ PASS ☐ FAIL  

---

## D. Retired Hurt

| Step | Action | Result | Notes |
|------|--------|--------|-------|
| D1 | Retire striker hurt | | |
| D2 | Does **not** count as legal ball | | |
| D3 | Does **not** force End of Over popup | | |
| D4 | Striker cleared; select replacement | | |
| D5 | Continue scoring normally | | |

**Section D:** ☐ PASS ☐ FAIL  

---

## E. 1st innings end → 2nd innings

| Step | Action | Result | Notes |
|------|--------|--------|-------|
| E1 | Complete 1st innings (5 overs **or** all out) | | |
| E2 | **Innings Completed** dialog appears | | |
| E3 | End of Over popup does **not** block Start 2nd Innings | | |
| E4 | Shows 1st innings score + target | | |
| E5 | Tap **START 2ND INNINGS** | | |
| E6 | Select batsmen + bowler | | |
| E7 | Score at least 1 full over in 2nd innings | | |
| E8 | Overs / Scorecard show both innings correctly | | |

**Section E:** ☐ PASS ☐ FAIL  

---

## F. Close match

| Step | Action | Result | Notes |
|------|--------|--------|-------|
| F1 | Finish 2nd innings (target or overs) | | |
| F2 | Match result / celebration shown | | |
| F3 | Winner correct | | |
| F4 | Scorecard final totals correct | | |
| F5 | Footer version still correct after match | | |

**Section F:** ☐ PASS ☐ FAIL  

---

## Residual risks — explicit checks

| Risk | Expected | Result | Notes |
|------|----------|--------|-------|
| R1 | New local player also lands in **Global Master List** | | |
| R2 | New local player linked to **tournament series** roster | | |
| R3 | After kill/reopen app, mid-match adds still present | | |
| R4 | Player IDs consistent (no duplicate ghosts after reload) | | |
| R5 | Debug build does **not** auto-bump version each Run | | |

---

## Overall result

| Section | Status |
|---------|--------|
| A Setup | |
| B Scoring | |
| C Manage Squad | |
| D Retired Hurt | |
| E 2nd innings | |
| F Close match | |
| Residual risks | |

**FINAL:** ☐ PASS ☐ FAIL  

**Blockers / bugs found:**

1.  
2.  
3.  

**Screenshots / logcat notes:**

-
-
-
