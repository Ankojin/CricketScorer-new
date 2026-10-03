# CricLeague — Android Audit Report

This report provides a detailed technical audit of the **CricLeague** Android application. It serves as the primary functional and architectural reference for building the new **React Web application**.

---

## 1. Application Architecture

* **Pattern**: Modern Jetpack Compose Architecture (MVVM + Repository Pattern + Reactive Data Streams via `StateFlow`).
* **UI Layer**: Declarative Jetpack Compose UI built with Material Design 3 (`androidx.compose.material3`).
* **State Management**:
  * `ScoringViewModel`: Manages active live match scoring, undo history, active wicket context, bowler notifications, over summaries, and Nearby P2P synchronization state.
  * `TournamentViewModel`: Manages tournament series, teams, global player repository, and import/export operations.
* **Domain Layer**: Pure Kotlin domain logic encapsulated in singletons:
  * `ScoringEngine`: High-performance incremental scoring engine (`overSnapshots` caching), match recalculation from ball history, strike rotation, extras, wickets, Gully rules, and target calculations.
* **Data & Persistence Layer**:
  * Local Room Database (`CricketDatabase` v3).
  * Repositories (`TournamentRepository`, `GlobalPlayerRepository`, `GullyRulesRepository`).
  * P2P Local Synchronization (`NearbyManager` using Google Play Services Nearby Connections).

---

## 2. Screen Inventory

| Screen Name | Composable Function | Location / File | Purpose & Features |
| :--- | :--- | :--- | :--- |
| **Home Screen** | `HomeScreen` | [`HomeScreen.kt`](file:///C:/CricscorPro/app/app/src/main/kotlin/in/nrkmart/cricscore/HomeScreen.kt#L38) | App dashboard. Displays live match banner, quick start actions, series shortcuts, settings modal, and Gully Rules configuration. |
| **Series / Dashboard Screen** | `DashboardScreen` | [`DashboardScreen.kt`](file:///C:/CricscorPro/app/app/src/main/kotlin/in/nrkmart/cricscore/DashboardScreen.kt#L27) | Displays list of tournaments/series, "New Series" setup dialog, and .CSD import/export options. |
| **Tournament Details Screen** | `TournamentDetailsScreen` | [`TournamentDetailsScreen.kt`](file:///C:/CricscorPro/app/app/src/main/kotlin/in/nrkmart/cricscore/TournamentDetailsScreen.kt#L48) | Multi-tab view for a tournament: `Matches`, `Standings` (Points table with NRR), `Teams`, `Stats` (Orange & Purple caps), and `Settings`. |
| **Live Scoring Screen** | `LiveScoringScreen` | [`LiveScoringScreen.kt`](file:///C:/CricscorPro/app/app/src/main/kotlin/in/nrkmart/cricscore/LiveScoringScreen.kt#L19) | Main scoring workspace. Tabbed header (`Live`, `Scorecard`, `Overs`, `More`), Keypad (0-6 runs, Wides, No-Balls, Byes, Wickets), Striker/Bowler cards, Over summary overlays. |
| **All Matches Screen** | `AllMatchesScreen` | [`ExploreScreens.kt`](file:///C:/CricscorPro/app/app/src/main/kotlin/in/nrkmart/cricscore/ExploreScreens.kt#L236) | Searchable/filterable list of all scheduled, live, and completed matches across all series. |
| **All Teams Screen** | `AllTeamsScreen` | [`ExploreScreens.kt`](file:///C:/CricscorPro/app/app/src/main/kotlin/in/nrkmart/cricscore/ExploreScreens.kt#L45) | List of all registered teams with color badges and squad counts. |
| **Team Detail Screen** | `TeamDetailScreen` | [`ExploreScreens.kt`](file:///C:/CricscorPro/app/app/src/main/kotlin/in/nrkmart/cricscore/ExploreScreens.kt#L286) | Squad roster, captain/vice-captain assignments, team color picker, and global player picking dialogs. |
| **All Players Screen (Playlist)** | `AllPlayersScreen` | [`ExploreScreens.kt`](file:///C:/CricscorPro/app/app/src/main/kotlin/in/nrkmart/cricscore/ExploreScreens.kt#L106) | Global master player directory. Allows adding/editing reusable players and batting styles (RHB/LHB). |

---

## 3. Navigation Inventory

Using `androidx.navigation.compose.NavHost` in [`MainActivity.kt`](file:///C:/CricscorPro/app/app/src/main/kotlin/in/nrkmart/cricscore/MainActivity.kt#L79):

* `home`: Root home screen with quick start and active live card.
* `dashboard`: Tournament list and series management.
* `all_matches`: Complete match directory.
* `all_teams`: Global team list.
* `team_detail/{teamId}?tournamentId={tournamentId}`: Team detail view.
* `all_players`: Global player directory.
* `live_scoring`: Active match scoring interface.
* `tournament_details/{tournamentId}`: Single tournament details, standings, and fixtures.

---

## 4. Domain & Model Inventory

All core data models reside in [`Models.kt`](file:///C:/CricscorPro/app/app/src/main/kotlin/in/nrkmart/cricscore/Models.kt):

1. **`Player`**:
   * `id`, `name`, `battingStats`, `bowlingStats`, `fieldingStats`, `isJoker`, `isCaptain`, `isViceCaptain`, `battingStyle` (RHB / LHB).
2. **`BattingStats`**:
   * `runs`, `balls`, `fours`, `sixes`, `isOut`, `isRetiredHurt`, `wicketType`, `dismissalBowlerId`, `dismissalFielderId`, `strikeRate`.
3. **`BowlingStats`**:
   * `overs`, `balls`, `maidens`, `runsConceded`, `wickets`, `dotBalls`, `wides`, `noBalls`, `economy`, `formattedOvers`.
4. **`FieldingStats`**:
   * `catches`, `runOuts`, `stumpings`, `droppedCatches`.
5. **`Team`**:
   * `id`, `name`, `players`, `matchesPlayed`, `wins`, `losses`, `points`, `nrr`, `colorHex`.
6. **`Ball`**:
   * `runs`, `extrasType` (`NONE`, `WIDE`, `NO_BALL`, `BYE`, `LEG_BYE`, `GRANTED`), `extraRuns`, `wicketType` (`BOWLED`, `CAUGHT`, `LBW`, `RUN_OUT`, `STUMPED`, `HIT_WICKET`, `HANDLED_BALL`, `OBSTRUCTING_FIELD`, `RETIRED_HURT`), `strikerId`, `nonStrikerId`, `bowlerId`, `fielderId`, `isLegalBall`, `outPlayerId`, `rotateStrike`, `hadCrossed`, `isDroppedCatch`, `dismissalReason`, `isAdjustment`, `adjustmentSlot`, `adjustmentPlayerId`, `isReplacement`.
7. **`Match`**:
   * `id`, `tournamentId`, `tournamentName`, `teamA`, `teamB`, `tossWinnerId`, `tossDecision` (`BAT`/`BOWL`), `initialBattingTeamId`, `initialBowlingTeamId`, `target`, `status` (`UPCOMING`, `LIVE`, `COMPLETED`, `ABANDONED`), `currentInnings` (1 or 2), `battingTeamId`, `bowlingTeamId`, `totalRuns`, `totalWickets`, `totalBalls`, `wideCount`, `noBallCount`, `byeCount`, `legByeCount`, `ballHistory`, `wicketHistory`, `strikerId`, `nonStrikerId`, `currentBowlerId`, `lastBowlerId`, `winnerId`, `oversPerInnings`, `maxOversPerBowler`, `quotaBowlersCount`, `quotaMaxOvers`, `gullyRules`, `pendingAction`, `innings1Data`, `isSecondInningsStarted`, `dateMillis`.
8. **`GullyRules`**:
   * `commonPlayer`, `unequalTeams`, `playersJoinMidMatch`, `playersSwitchMidMatch`, `lastManStanding`, `singleSideBatting`, `noExtraRunsForWidesNoBalls`.
9. **`PendingAction`**:
   * `NONE`, `SELECT_STRIKER`, `SELECT_NON_STRIKER`, `SELECT_BOWLER`, `TOSS_REQUIRED`, `SELECT_WK_A`, `SELECT_WK_B`, `SELECT_FIELDER`, `START_SECOND_INNINGS`, `SELECT_FIELDER_DROPPED_CATCH`, `SELECT_RUNS_DROPPED_CATCH`, `REPLACE_STRIKER`, `REPLACE_NON_STRIKER`, `REPLACE_BOWLER`, `SELECT_RUNS_WICKET`, `SELECT_MATCH_SETTINGS`.

---

## 5. Scoring Functionality Inventory

The cricket domain logic in [`ScoringEngine.kt`](file:///C:/CricscorPro/app/app/src/main/kotlin/in/nrkmart/cricscore/ScoringEngine.kt) governs all match processing:

1. **Incremental Snapshot Engine**:
   * Caches match states in `overSnapshots` at the end of each over (6 physical balls) to perform $O(1)$ incremental updates instead of recalculating full ball history every delivery.
2. **Runs & Extras**:
   * **Off Bat**: Credited to striker, legal ball.
   * **Wide**: +1 penalty run (unless `noExtraRunsForWidesNoBalls` is enabled), re-bowled (not a physical ball).
   * **No-Ball**: +1 penalty run, re-bowled (not a physical ball), bat runs credited to striker.
   * **Byes & Leg Byes**: Legal balls, team runs, zero runs to batter, no bowler concession for runs.
3. **Strike Rotation**:
   * Rotates strike on odd physical runs off bat/extra runs.
   * Handles batter crossing (`hadCrossed`).
   * Automatic strike rotation at the end of an over (6 physical balls).
4. **Wickets**:
   * Bowler-credited wickets: `BOWLED`, `CAUGHT`, `LBW`, `STUMPED`, `HIT_WICKET`.
   * Non-bowler wickets: `RUN_OUT`, `RETIRED_HURT`, `HANDLED_BALL`, `OBSTRUCTING_FIELD`.
   * Unsets out batter and prompts for new striker/non-striker via `PendingAction`.
5. **Gully & Street Cricket Rules**:
   * **Last Man Standing (LMS)**: Final batter continues alone without non-striker.
   * **Single Side Batting**: One batter at a time without strike rotation.
   * **Unequal Teams**: Sets max wickets based on team squad size.
6. **Innings & Target Calculation**:
   * Innings 1 ends when max wickets are fallen or max overs are completed.
   * Target set to `Innings 1 Runs + 1`.
   * Innings 2 completes when runs $\ge$ target (Batting team wins) or max overs/wickets reached with runs $<$ target $- 1$ (Bowling team wins).
7. **Undo & Adjustments**:
   * Complete undo support by popping last ball from `ballHistory` and re-running `recalculateMatchFromHistory`.
   * Adjustment balls record manual player replacements, strike swaps, and slot filling without altering legal delivery counts.

---

## 6. Persistence Inventory

1. **Database**: Android Room Database [`CricketDatabase`](file:///C:/CricscorPro/app/app/src/main/kotlin/in/nrkmart/cricscore/db/CricketDatabase.kt) version 3.
2. **Entities**:
   * `TournamentEntity` (`tournaments`)
   * `TeamEntity` (`teams`)
   * `PlayerEntity` (`players`)
   * `MatchEntity` (`matches`)
   * `BallEntity` (`balls`)
3. **Migrations**:
   * `MIGRATION_1_2`: Added `colorHex` column to `teams`.
   * `MIGRATION_2_3`: Flattened player statistics into `players` table.
4. **Schema Export**:
   * Schemas exported to `app/schemas/in.nrkmart.cricscore.db.CricketDatabase/3.json`.
5. **JSON Export / Import**:
   * `.CSD` file format used for tournament/series backup and sharing via Android `FileProvider`.

---

## 7. Authentication Inventory

* **Mode**: 100% Offline-First / Guest Mode.
* **No Cloud Auth Required**: Local data persistence via Room DB. All functionality (scoring, tournaments, stats, Gully rules) operates locally without mandatory login or cloud tokens.

---

## 8. API Inventory

* **Peer-to-Peer Local Sync**: Uses Google Play Services Nearby Connections API (`NearbyManager.kt`).
  * `SERVICE_ID`: `in.nrkmart.cricscore.SYNC.v1.20`.
  * Allows live match scoring broadcast to nearby spectator/scoring devices over Bluetooth & Wi-Fi Direct.
* **Sharing API**:
  * Android `FileProvider` (`${applicationId}.fileprovider`) for sharing scorecard image captures and `.csd` tournament export files.

---

## 9. Test Inventory

* **Unit Tests**:
  * [`ScoringViewModelTest.kt`](file:///C:/CricscorPro/app/app/src/test/kotlin/in/nrkmart/cricscore/ScoringViewModelTest.kt): Covers wide scoring, no-ball scoring, and extra run calculations.
* **Instrumentation / Migration Tests**:
  * [`MigrationTest.kt`](file:///C:/CricscorPro/app/app/src/androidTest/kotlin/in/nrkmart/cricscore/db/MigrationTest.kt): Validates Room database schema migration path `MIGRATION_1_2` and `MIGRATION_2_3`.

---

## 10. Dependencies

Defined in [`app/build.gradle.kts`](file:///C:/CricscorPro/app/app/build.gradle.kts):

* `androidx.core:core-ktx:1.15.0`
* `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7`
* `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7`
* `androidx.navigation:navigation-compose:2.8.5`
* `androidx.activity:activity-compose:1.13.0`
* `androidx.compose.ui:ui`, `material3`, `material-icons-extended` (BOM `2026.08.00`)
* `androidx.room:room-runtime:2.8.5`, `room-ktx:2.8.5`, `room-compiler:2.8.5` (KSP)
* `com.google.android.gms:play-services-nearby:19.0.0`
* `com.google.code.gson:gson:2.14.0`

---

## 11. Application ID Details

* **Current & Target Application ID**: `in.nrkmart.cricscore`
* **Namespace**: `in.nrkmart.cricscore`
* **Root Kotlin Package**: `in.nrkmart.cricscore`

---

## 12. Migration Risks

1. **State Engine Divergence**:
   * Any subtle divergence between Kotlin's `ScoringEngine` and TypeScript's `ScoringEngine` regarding strike rotation, Gully rules, or extra runs calculation could lead to score mismatch.
   * *Mitigation*: Both implementations share identical algorithms and models.
2. **Data Model Nullability**:
   * Android Kotlin models use nullable fields for initial states (`strikerId`, `nonStrikerId`, `currentBowlerId`).
   * *Mitigation*: TypeScript models explicitly define `string | null` for all optional slots.
3. **Local Storage Limits**:
   * Large ball-by-ball histories stored in `localStorage` could exceed standard limits if not pruned or optimized.
   * *Mitigation*: Use structured JSON serialization in `StorageAdapter`.

---

## 13. Recommended React Web Mapping

| Android Component | React Web Component | File Path in `web/` |
| :--- | :--- | :--- |
| `ScoringEngine.kt` | `ScoringEngine` (TypeScript class) | [`src/domain/scoringEngine.ts`](file:///C:/CricscorPro/app/web/src/domain/scoringEngine.ts) |
| `Models.kt` | TypeScript Interfaces & Enums | [`src/domain/models.ts`](file:///C:/CricscorPro/app/web/src/domain/models.ts) |
| `ScoringViewModel.kt` | `MatchContext` & `useMatch` hook | [`src/state/MatchContext.tsx`](file:///C:/CricscorPro/app/web/src/state/MatchContext.tsx) |
| `TournamentRepository.kt` | `TournamentContext` & `useTournament` | [`src/state/TournamentContext.tsx`](file:///C:/CricscorPro/app/web/src/state/TournamentContext.tsx) |
| Room DB (`CricketDatabase`) | `StorageAdapter` (`localStorage`) | [`src/storage/storageAdapter.ts`](file:///C:/CricscorPro/app/web/src/storage/storageAdapter.ts) |
| `MainNavigation` | `Navbar` Component | [`src/ui/navigation/Navbar.tsx`](file:///C:/CricscorPro/app/web/src/ui/navigation/Navbar.tsx) |
| `HomeScreen.kt` | `HomeScreen` View | [`src/ui/views/HomeScreen.tsx`](file:///C:/CricscorPro/app/web/src/ui/views/HomeScreen.tsx) |
| `LiveScoringScreen.kt` | `LiveScoringView` | [`src/ui/views/LiveScoringView.tsx`](file:///C:/CricscorPro/app/web/src/ui/views/LiveScoringView.tsx) |
| `ScorecardViews.kt` | `ScorecardView` | [`src/ui/views/ScorecardView.tsx`](file:///C:/CricscorPro/app/web/src/ui/views/ScorecardView.tsx) |
| `OversViews.kt` | `OversView` | [`src/ui/views/OversView.tsx`](file:///C:/CricscorPro/app/web/src/ui/views/OversView.tsx) |
| `ExploreScreens.kt` | `MatchesScreen`, `TeamsScreen`, `StatsScreen` | [`src/ui/views/`](file:///C:/CricscorPro/app/web/src/ui/views) |

