package com.chandu.gatesystem.data

data class AchievementStats(
    val totalXp: Int,
    val level: Int,
    val streak: Int,
    val questsCleared: Int,
    val monstersDefeated: Int
)

data class Achievement(val id: String, val icon: String, val title: String, val condition: (AchievementStats) -> Boolean)

object Achievements {
    val all: List<Achievement> = listOf(
        Achievement("first_quest", "\u2694\uFE0F", "First Quest Cleared") { it.questsCleared >= 1 },
        Achievement("quests10", "\uD83D\uDCDA", "10 Quests Cleared") { it.questsCleared >= 10 },
        Achievement("quests30", "\uD83D\uDCDA", "30 Quests Cleared") { it.questsCleared >= 30 },
        Achievement("quests100", "\uD83D\uDCDA", "100 Quests Cleared") { it.questsCleared >= 100 },
        Achievement("streak7", "\uD83D\uDD25", "7-Day Streak") { it.streak >= 7 },
        Achievement("streak30", "\uD83D\uDD25", "30-Day Streak") { it.streak >= 30 },
        Achievement("level10", "\u2B06", "Reached Level 10") { it.level >= 10 },
        Achievement("level21", "\u2B06", "Reached Level 21 (S-Rank)") { it.level >= 21 },
        Achievement("monsters10", "\u2620", "10 Error Monsters Defeated") { it.monstersDefeated >= 10 },
        Achievement("monsters50", "\u2620", "50 Error Monsters Defeated") { it.monstersDefeated >= 50 },
        Achievement("xp5000", "\uD83D\uDCB0", "5,000 Total XP") { it.totalXp >= 5000 },
        Achievement("xp10000", "\uD83D\uDCB0", "10,000 Total XP") { it.totalXp >= 10000 }
    )

    fun earned(stats: AchievementStats): List<Achievement> = all.filter { it.condition(stats) }
}
