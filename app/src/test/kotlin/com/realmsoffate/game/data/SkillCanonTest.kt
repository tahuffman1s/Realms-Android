package com.realmsoffate.game.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SkillCanonTest {

    @Test
    fun `canonical skill names pass through unchanged`() {
        assertEquals("Investigation", SkillCanon.canonicalize("Investigation"))
        assertEquals("Persuasion", SkillCanon.canonicalize("Persuasion"))
        assertEquals("Sleight of Hand", SkillCanon.canonicalize("Sleight of Hand"))
        assertEquals("Animal Handling", SkillCanon.canonicalize("Animal Handling"))
        assertEquals("Attack", SkillCanon.canonicalize("Attack"))
    }

    @Test
    fun `case-insensitive canonical match`() {
        assertEquals("Investigation", SkillCanon.canonicalize("investigation"))
        assertEquals("Persuasion", SkillCanon.canonicalize("PERSUASION"))
        assertEquals("Sleight of Hand", SkillCanon.canonicalize("sleight of hand"))
    }

    @Test
    fun `verb aliases map to canonical skills`() {
        assertEquals("Investigation", SkillCanon.canonicalize("investigate"))
        assertEquals("Investigation", SkillCanon.canonicalize("search"))
        assertEquals("Intimidation", SkillCanon.canonicalize("threaten"))
        assertEquals("Intimidation", SkillCanon.canonicalize("threaten back"))
        assertEquals("Persuasion", SkillCanon.canonicalize("persuade"))
        assertEquals("Persuasion", SkillCanon.canonicalize("convince"))
        assertEquals("Stealth", SkillCanon.canonicalize("sneak"))
        assertEquals("Stealth", SkillCanon.canonicalize("hide"))
        assertEquals("Perception", SkillCanon.canonicalize("look"))
        assertEquals("Perception", SkillCanon.canonicalize("listen"))
        assertEquals("Deception", SkillCanon.canonicalize("lie"))
        assertEquals("Acrobatics", SkillCanon.canonicalize("dodge"))
        assertEquals("Athletics", SkillCanon.canonicalize("climb"))
        assertEquals("Attack", SkillCanon.canonicalize("attack"))
    }

    @Test
    fun `travel and dialogue transitions return empty`() {
        assertEquals("", SkillCanon.canonicalize("head to Hunter's Rest now"))
        assertEquals("", SkillCanon.canonicalize("speak to Jiro Gorethane first"))
        assertEquals("", SkillCanon.canonicalize("travel to the inn"))
        assertEquals("", SkillCanon.canonicalize("go north"))
        assertEquals("", SkillCanon.canonicalize("walk away"))
        assertEquals("", SkillCanon.canonicalize(""))
        assertEquals("", SkillCanon.canonicalize("   "))
    }

    @Test
    fun `unknown freeform verbs return empty rather than defaulting to STR`() {
        assertEquals("", SkillCanon.canonicalize("ponder"))
        assertEquals("", SkillCanon.canonicalize("wait"))
        assertEquals("", SkillCanon.canonicalize("rest"))
    }

    @Test
    fun `canonical list exposes all 5e skills plus Attack`() {
        val expected = setOf(
            "Athletics", "Acrobatics", "Sleight of Hand", "Stealth",
            "Arcana", "History", "Investigation", "Nature", "Religion",
            "Animal Handling", "Insight", "Medicine", "Perception",
            "Survival", "Deception", "Intimidation", "Performance", "Persuasion",
            "Attack"
        )
        assertEquals(expected, SkillCanon.CANONICAL_SKILLS)
    }
}
