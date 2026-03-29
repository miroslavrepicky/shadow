package sk.stuba.fiit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import sk.stuba.fiit.util.Vector2D;
import sk.stuba.fiit.inventory.Inventory;
import sk.stuba.fiit.items.HealingPotion;
import sk.stuba.fiit.projectiles.EggProjectile;

/**
 * ShadowQuestTest – 4 JUnit 5 tests covering:
 *   1. Vector2D        – mathematical operations and edge cases
 *   2. Character       – takeDamage() with armor reduction, healing, HP floor
 *   3. Inventory       – slot management, overflow, addCharacter / removeItem
 *   4. EggProjectile   – TICKING → BLASTING state machine
 */
public class ShadowQuestTest {

    // =========================================================
    // TEST 1 – Vector2D: add, scale, distanceTo
    // =========================================================

    /**
     * Verifies basic vector operations and edge cases:
     *  - adding a zero vector (identity)
     *  - scaling by zero → zero vector
     *  - scaling by a negative factor
     *  - distance from a point to itself = 0
     *  - Pythagorean triple (3-4-5) for distanceTo
     *  - symmetry: d(a,b) == d(b,a)
     */
    @Test
    void testVector2D_operations() {
        Vector2D a    = new Vector2D(3f, 4f);
        Vector2D zero = new Vector2D(0f, 0f);

        // adding zero vector is identity
        Vector2D sum = a.add(zero);
        assertEquals(3f, sum.getX(), 1e-6f, "add with zero: X");
        assertEquals(4f, sum.getY(), 1e-6f, "add with zero: Y");

        // scaling by zero produces zero vector
        Vector2D scaledZero = a.scale(0f);
        assertEquals(0f, scaledZero.getX(), 1e-6f, "scale by 0: X");
        assertEquals(0f, scaledZero.getY(), 1e-6f, "scale by 0: Y");

        // scaling by a negative factor flips and magnifies
        Vector2D negScaled = a.scale(-2f);
        assertEquals(-6f, negScaled.getX(), 1e-6f, "scale by -2: X");
        assertEquals(-8f, negScaled.getY(), 1e-6f, "scale by -2: Y");

        // distance from a point to itself must be 0
        assertEquals(0.0, a.distanceTo(a), 1e-9, "distanceTo self == 0");

        // Pythagorean triple: distance from (3,4) to (0,0) == 5
        Vector2D origin = new Vector2D(0f, 0f);
        assertEquals(5.0, a.distanceTo(origin), 1e-6, "distanceTo (3,4) from (0,0) == 5");

        // symmetry: d(a,b) == d(b,a)
        Vector2D b = new Vector2D(6f, 8f);
        assertEquals(a.distanceTo(b), b.distanceTo(a), 1e-9, "distanceTo is symmetric");
    }

    // =========================================================
    // TEST 2 – Character.takeDamage(): armor, heal, HP floor
    // =========================================================

    /**
     * Minimal Character stub – no AnimationManager or LibGDX dependency.
     */
    static class TestCharacter extends sk.stuba.fiit.characters.Character {
        TestCharacter(int hp, int armor, int maxArmor) {
            super("TestHero", hp, 10, 1f, new Vector2D(0f, 0f), armor, maxArmor);
        }
        @Override public void performAttack() {}
        @Override public sk.stuba.fiit.core.AnimationManager getAnimationManager() { return null; }
        @Override public void move(Vector2D d) { position = position.add(d); updateHitbox(); }
        @Override public void onCollision(Object other) {}
        @Override public void update(float dt) {}
    }

    /**
     * Verifies:
     *  - armor reduces incoming damage (dmg > armor)
     *  - armor absorbs all damage when dmg <= armor
     *  - HP never drops below 0
     *  - negative dmg value heals the character (armor is not applied)
     *  - healing cannot exceed maxHp
     */
    @Test
    void testCharacter_takeDamage() {
        // armor reduces damage: 30 - 10 armor = 20 effective damage
        TestCharacter c1 = new TestCharacter(100, 10, 20);
        c1.takeDamage(30);
        assertEquals(80, c1.getHp(), "HP after damage with armor");

        // dmg <= armor → no HP loss
        TestCharacter c2 = new TestCharacter(100, 15, 20);
        c2.takeDamage(10); // 10 - 15 = Math.max(0, -5) = 0
        assertEquals(100, c2.getHp(), "damage smaller than armor leaves HP unchanged");

        // HP floor is 0, never negative
        TestCharacter c3 = new TestCharacter(10, 0, 0);
        c3.takeDamage(9999);
        assertEquals(0, c3.getHp(), "HP must not drop below 0");
        assertFalse(c3.isAlive(), "isAlive() returns false when HP == 0");

        // negative dmg = heal
        TestCharacter c4 = new TestCharacter(60, 0, 0);
        c4.takeDamage(40);           // hp = 20
        assertEquals(20, c4.getHp());
        c4.takeDamage(-15);          // heal 15 → hp = 35
        assertEquals(35, c4.getHp(), "healing increases HP");

        // healing cannot exceed maxHp
        c4.takeDamage(-9999);
        assertEquals(c4.getMaxHp(), c4.getHp(), "HP is capped at maxHp after overheal");
    }

    // =========================================================
    // TEST 3 – Inventory: slots, characters, items, overflow
    // =========================================================

    /**
     * Minimal PlayerCharacter stub for inventory tests – no LibGDX dependency.
     */
    static class StubPlayer extends sk.stuba.fiit.characters.PlayerCharacter {
        StubPlayer(String name) {
            super(name, 100, 10, 2f, new Vector2D(0f, 0f));
        }
        @Override public void handleInput() {}
        @Override public void update(float dt) {}
        @Override public void performAttack() {}
        @Override public sk.stuba.fiit.core.AnimationManager getAnimationManager() { return null; }
        @Override public void move(Vector2D d) { position = position.add(d); updateHitbox(); }
    }

    /**
     * Verifies:
     *  - adding a character costs 3 slots
     *  - overflow (not enough slots) returns false and does not add the character
     *  - addItem / removeItem correctly track usedSlots
     *  - getFreeSlots() is consistent with usedSlots
     *  - isPartyDefeated() returns false when all characters are alive
     */
    @Test
    void testInventory_slotManagement() {
        Inventory inv = new Inventory(10);

        StubPlayer p1 = new StubPlayer("Knight");
        assertTrue(inv.addCharacter(p1), "first character is added successfully");
        assertEquals(3, inv.getUsedSlots(), "character costs 3 slots");
        assertEquals(p1, inv.getActive(), "first added character is set as active");

        StubPlayer p2 = new StubPlayer("Wizzard");
        assertTrue(inv.addCharacter(p2), "second character is added (6/10 slots)");
        assertEquals(6, inv.getUsedSlots());

        // HealingPotion costs 2 slots
        HealingPotion potion = new HealingPotion(50, new Vector2D(0f, 0f));
        assertTrue(inv.addItem(potion), "potion is added (8/10 slots)");
        assertEquals(8, inv.getUsedSlots());
        assertEquals(2, inv.getFreeSlots());

        // third character needs 3 slots but only 2 remain → overflow
        StubPlayer p3 = new StubPlayer("Archer");
        assertFalse(inv.addCharacter(p3), "character is rejected when not enough slots");
        assertEquals(8, inv.getUsedSlots(), "usedSlots unchanged after failed add");

        // removeItem correctly decrements usedSlots
        inv.removeItem(potion);
        assertEquals(6, inv.getUsedSlots(), "usedSlots decremented after removeItem");
        assertEquals(4, inv.getFreeSlots());

        // party is not defeated while all characters are alive
        assertFalse(inv.isPartyDefeated(), "isPartyDefeated() is false when party is alive");
    }

    // =========================================================
    // TEST 4 – EggProjectile: TICKING → BLASTING state machine
    // =========================================================

    /**
     * Stub subclass that overrides initAnimations() with an empty body so
     * no atlas file is loaded from disk during tests.
     *
     * IMPORTANT: this requires one small change in EggProjectile.java –
     * change  "private void initAnimations()"
     * to      "protected void initAnimations()"
     * so that this subclass can override it.
     */
    static class TestEggProjectile extends EggProjectile {
        TestEggProjectile(Vector2D position) {
            super(position);
        }

        /** Skip atlas loading – animationManager stays null in tests. */
        @Override
        protected void initAnimations() {
            // intentionally empty – no atlas files available in test environment
        }
    }

    /**
     * Verifies the EggProjectile lifecycle without any atlas/LibGDX assets:
     *  - starts in TICKING state and is active
     *  - stays TICKING before BOMB_DURATION (2.5 s) elapses
     *  - transitions to BLASTING after BOMB_DURATION is exceeded
     *  - becomes inactive after BLAST_DURATION (0.8 s) elapses
     */
    @Test
    void testEggProjectile_stateMachine() {
        TestEggProjectile egg = new TestEggProjectile(new Vector2D(100f, 50f));

        // initial state
        assertEquals(EggProjectile.EggState.TICKING, egg.getEggState(),
            "egg starts in TICKING state");
        assertTrue(egg.isActive(), "egg is active at creation");

        // before BOMB_DURATION elapses → still TICKING
        egg.update(1.0f);
        assertEquals(EggProjectile.EggState.TICKING, egg.getEggState(),
            "still TICKING after 1 s");
        assertTrue(egg.isActive());

        // exceed BOMB_DURATION (2.5 s total) → transition to BLASTING
        egg.update(1.6f); // total: 2.6 s > 2.5 s
        assertEquals(EggProjectile.EggState.BLASTING, egg.getEggState(),
            "transitions to BLASTING after 2.6 s");
        assertTrue(egg.isActive(), "egg is still active during BLASTING");

        // exceed BLAST_DURATION (0.8 s) → becomes inactive
        egg.update(0.9f);
        assertFalse(egg.isActive(), "egg becomes inactive after blast ends");
    }
}
