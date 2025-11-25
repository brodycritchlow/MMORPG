# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

```bash
# Build plugin and copy to server
./build.sh

# Maven commands (used by build.sh)
mvn clean package          # Compile and package JAR
mvn clean                  # Clean build artifacts
mvn compile               # Compile without packaging
```

Build output: `target/SkillsPlugin-1.0-SNAPSHOT.jar`
Server deployment: `../../plugins/` (relative to project root)

## High-Level Architecture

### Layered Design

```
Entry Point (SkillsPlugin.java)
    ├─> Commands (7 total)
    │   └─> /skills, /sword, /wand, /levwand, /skillset, /skillreset, /query
    ├─> Event Listeners (4 total)
    │   ├─> SkillGainListener    - Combat XP, damage tracking, health bars
    │   ├─> WandListener          - Fire wand spell mechanics
    │   ├─> LevitationWandListener - Block levitation mechanics
    │   └─> SkillsGUIListener     - GUI interaction prevention
    └─> Business Logic
        ├─> SkillManager          - Skill operations (mostly stubbed)
        └─> DatabaseManager       - SQLite connection and queries
```

### Critical Data Flow: Combat XP System

1. **Damage Event** (SkillGainListener.java:46)
   - Player hits entity with custom weapon
   - Check item NBT tags for `damage_custom` and `damage_skill`
   - Calculate final damage: `baseDamage × (1.0 + attackLevel × 0.05)`
   - Track damage per player in `damageTracker: Map<UUID, Map<UUID, Double>>`
   - Track skill type per player in `skillTracker: Map<UUID, Map<UUID, String>>`

2. **Entity Death** (SkillGainListener.java:182)
   - Calculate base XP from config: `combat_xp.{ENTITY_TYPE}` or DEFAULT (5.0)
   - Distribute XP proportionally: `xpAmount = baseXP × (damageDealt / totalDamage)`
   - Update database: level and experience for each contributing player
   - Handle level-ups with progression formula: `requiredXP = level² × 45`

3. **Database Update**
   - Query current level and XP
   - Add earned XP and check for level-ups
   - UPDATE player_skills with new values

### Database Schema

```sql
CREATE TABLE IF NOT EXISTS player_skills (
    uuid TEXT NOT NULL,           -- Player UUID
    skill_name TEXT NOT NULL,     -- Skill enum name (ATTACK, MAGIC, etc.)
    level INTEGER DEFAULT 1,      -- Current level (1-99)
    experience REAL DEFAULT 0.0,  -- Current XP toward next level
    PRIMARY KEY (uuid, skill_name)
);
```

12 Skills: MINING, WOODCUTTING, FARMING, FISHING, ATTACK, DEFENSE, MAGIC, ARCHERY, HUNTING, EXPLORATION, COOKING, ALCHEMY

### Custom Item System via NBT Tags

Items use PersistentDataContainer with these keys:
- `damage_custom` (DOUBLE) - Custom damage value
- `damage_skill` (STRING) - Skill type for XP ("combat" or "magic")
- `no_melee` (BYTE) - Flag to prevent melee damage (projectiles only)

Created by: `/sword <damage>`, `/wand`, `/levwand`

### Hologram Systems

Two ArmorStand-based systems:
1. **Health Bars** - Persistent above living entities
   - Format: `§e{name} §c❤ {health}/{maxHealth}`
   - Updates every tick via BukkitRunnable
   - Stored in `healthDisplays: Map<UUID, ArmorStand>`
   - Cleanup on entity death

2. **Damage Indicators** - Temporary floating numbers
   - Format: `§c{damage}` (1 decimal place)
   - Spawns at random offset near entity
   - Floats upward 0.05 blocks/tick
   - Removed after 20 ticks (1 second)

## Important Technical Considerations

### Key Formulas
- **XP to Level**: `level² × 45` (e.g., level 2 requires 180 XP)
- **Damage Scaling**: `1.0 + (skillLevel × 0.05)` (5% per level)
- **XP Distribution**: `baseXP × (damageDealt / totalDamage)` (proportional to contribution)

### Wand Mechanics
**Fire Wand** (WandListener.java):
- Spiral beam particle effect (FLAME)
- Radius: 0.4, rotation: π/2 per step
- 20-block range, hits first entity in path
- Applies custom damage with MAGIC skill type

**Levitation Wand** (LevitationWandListener.java):
- Levitates 9 nearby blocks (3×3 area)
- Circular orbit: radius 3.0, speed 0.1 rad/tick
- Launch on right-click: velocity (0, 2, 0)
- Explosion on impact: 3-block damage radius

### Architectural Concerns

**CRITICAL - SQL Injection Vulnerability**:
All database queries use string concatenation:
```java
"SELECT ... WHERE uuid = '" + uuid + "' AND skill_name = '" + skillName + "'"
```
This should use PreparedStatement for production use.

**Performance Considerations**:
- All database operations are synchronous on main thread (may cause lag)
- No connection pooling or async queries
- Unbounded hologram creation (no entity limits)
- SkillManager cache methods are stubbed (in-memory cache not implemented)

**Implementation Status**:
- Combat XP system: COMPLETE
- Health bars and damage indicators: COMPLETE
- Skills GUI: COMPLETE
- Fire wand and levitation wand: COMPLETE
- Admin commands (/skillset, /skillreset): STUBBED
- SkillManager business logic: MOSTLY STUBBED
- Model classes (SkillData, PlayerSkills): STUBBED

### Essential File Locations

**Core Systems**:
- Main plugin: `src/main/java/com/thornily/skills/SkillsPlugin.java`
- Combat/XP: `src/main/java/com/thornily/skills/listeners/SkillGainListener.java`
- Database: `src/main/java/com/thornily/skills/database/DatabaseManager.java`

**Commands** (all in `src/main/java/com/thornily/skills/commands/`):
- SkillsCommand.java (GUI), SwordCommand.java, WandCommand.java, LevitationWandCommand.java
- SkillSetCommand.java, SkillResetCommand.java, QueryCommand.java (admin tools)

**Listeners** (all in `src/main/java/com/thornily/skills/listeners/`):
- WandListener.java (fire wand)
- LevitationWandListener.java (block levitation)
- SkillsGUIListener.java (prevent inventory interaction)

**Configuration**:
- Entity XP values: `src/main/resources/config.yml`
- Build config: `pom.xml`
- Plugin metadata: `src/main/resources/plugin.yml`

## Technology Stack

- **Minecraft**: Paper API 1.21.3
- **Java**: 21 (source and target)
- **Build**: Maven 3.x with Shade plugin
- **Database**: SQLite (sqlite-jdbc 3.47.1.0)
- **Bukkit**: Event-driven architecture, BukkitRunnable for async tasks
