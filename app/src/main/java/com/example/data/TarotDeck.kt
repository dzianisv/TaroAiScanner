package com.example.data

data class TarotCard(
    val id: Int,
    val name: String,
    val symbol: String, // Astronomical icon, symbol, or emoji
    val keywords: List<String>,
    val uprightMeaning: String,
    val reversedMeaning: String,
    val astrologicalSign: String,
    val element: String
)

object TarotDeck {
    val majorArcana = listOf(
        TarotCard(
            id = 0,
            name = "The Fool",
            symbol = "🎒",
            keywords = listOf("New Beginnings", "Spontaneity", "Faith", "Pure Potential"),
            uprightMeaning = "The beginning of a brand new adventure. Step into the unknown with faith, innocence, and infinite potential.",
            reversedMeaning = "Recklessness, risk-taking, holding back, or fear of taking a leap of faith into a new phase.",
            astrologicalSign = "Uranus",
            element = "Air"
        ),
        TarotCard(
            id = 1,
            name = "The Magician",
            symbol = "🪄",
            keywords = listOf("Manifestation", "Willpower", "Resourcefulness", "Creation"),
            uprightMeaning = "You have all the tools, skills, and resources needed to manifest your desires. Direct your focus.",
            reversedMeaning = "Manipulation, illusions, unused talent, or wasted willpower.",
            astrologicalSign = "Mercury",
            element = "Air"
        ),
        TarotCard(
            id = 2,
            name = "The High Priestess",
            symbol = "🌙",
            keywords = listOf("Intuition", "Sacred Knowledge", "Divine Feminine", "Subconscious"),
            uprightMeaning = "Listen to your inner voice. The answers lie deep within your subconscious and intuitive awareness.",
            reversedMeaning = "Secret motives, ignoring your gut feeling, surface-level focus, or hidden enemies.",
            astrologicalSign = "Moon",
            element = "Water"
        ),
        TarotCard(
            id = 3,
            name = "The Empress",
            symbol = "👑",
            keywords = listOf("Abundance", "Nature", "Nurturing", "Creativity"),
            uprightMeaning = "A period of incredible growth, connection with Mother Nature, and abundant physical and artistic creation.",
            reversedMeaning = "Creative block, dependence on others, smothering behavior, or feelings of scarcity.",
            astrologicalSign = "Venus",
            element = "Earth"
        ),
        TarotCard(
            id = 4,
            name = "The Emperor",
            symbol = "🏛️",
            keywords = listOf("Authority", "Structure", "Solid Foundation", "Protection"),
            uprightMeaning = "Establish order, structure, and boundaries. Take charge of your life with clear discipline and logic.",
            reversedMeaning = "Tyranny, lack of discipline, powerlessness, or overly rigid boundaries.",
            astrologicalSign = "Aries",
            element = "Earth"
        ),
        TarotCard(
            id = 5,
            name = "The Hierophant",
            symbol = "🗝️",
            keywords = listOf("Tradition", "Spiritual Wisdom", "Conformity", "Institutions"),
            uprightMeaning = "Seeking spiritual knowledge or guidance from established traditions, mentors, or structures.",
            reversedMeaning = "Rebellion, unorthodox paths, restriction, or challenging outdated dogmas.",
            astrologicalSign = "Taurus",
            element = "Earth"
        ),
        TarotCard(
            id = 6,
            name = "The Lovers",
            symbol = "💞",
            keywords = listOf("Harmony", "Deep Connection", "Choices", "Value Alignment"),
            uprightMeaning = "A major choice lies ahead, requiring alignment with your highest values. Deep harmony in relationships.",
            reversedMeaning = "Disharmony, misalignment of values, poor decisions, or imbalance.",
            astrologicalSign = "Gemini",
            element = "Air"
        ),
        TarotCard(
            id = 7,
            name = "The Chariot",
            symbol = "🛡️",
            keywords = listOf("Victory", "Willpower", "Focused Direction", "Control"),
            uprightMeaning = "Success through sheer willpower, focus, and overcoming opposing forces. Move forward with conviction.",
            reversedMeaning = "Lack of direction, loss of control, aggression, or hitting obstacles.",
            astrologicalSign = "Cancer",
            element = "Water"
        ),
        TarotCard(
            id = 8,
            name = "Strength",
            symbol = "🦁",
            keywords = listOf("Courage", "Inner Fortitude", "Compassion", "Patience"),
            uprightMeaning = "Conquering challenges through gentle persuasion, inner patience, and quiet, compassionate resolve.",
            reversedMeaning = "Self-doubt, weakness, raw raw emotion, or raw impulse overriding wisdom.",
            astrologicalSign = "Leo",
            element = "Fire"
        ),
        TarotCard(
            id = 9,
            name = "The Hermit",
            symbol = "🏮",
            keywords = listOf("Soul Searching", "Inner Guidance", "Solitude", "Spiritual Journey"),
            uprightMeaning = "Time to retreat from external noise. Seek wisdom in solitude, reflection, and quiet introspection.",
            reversedMeaning = "Loneliness, paranoia, withdrawal, or returning to society prematurely.",
            astrologicalSign = "Virgo",
            element = "Earth"
        ),
        TarotCard(
            id = 10,
            name = "Wheel of Fortune",
            symbol = "🎡",
            keywords = listOf("Good Luck", "Karma", "Destiny", "Turning Points"),
            uprightMeaning = "A sudden change of fortune. The universe is spinning events in your favor. Trust the cycle.",
            reversedMeaning = "Bad luck, resistance to inevitable change, breaking negative patterns.",
            astrologicalSign = "Jupiter",
            element = "Fire"
        ),
        TarotCard(
            id = 11,
            name = "Justice",
            symbol = "⚖️",
            keywords = listOf("Truth", "Fairness", "Law of Karma", "Cause & Effect"),
            uprightMeaning = "Truth will prevail. Your actions will have direct consequences. Decisions made with clarity and fairness.",
            reversedMeaning = "Dishonesty, unfair treatment, denial of responsibility, or karma delayed.",
            astrologicalSign = "Libra",
            element = "Air"
        ),
        TarotCard(
            id = 12,
            name = "The Hanged Man",
            symbol = "⚓",
            keywords = listOf("Surrender", "New Perspective", "Letting Go", "Sacrifice"),
            uprightMeaning = "Pause and let go. A temporary state of suspension that allows you to see the world from a completely new angle.",
            reversedMeaning = "Egotistical delay, stalling, resistance to letting go, or useless sacrifice.",
            astrologicalSign = "Neptune",
            element = "Water"
        ),
        TarotCard(
            id = 13,
            name = "Death",
            symbol = "💀",
            keywords = listOf("Endings", "Transformation", "Deep Transition", "New Dawn"),
            uprightMeaning = "The closure of a major cycle. Let go of the old to make way for a profound rebirth and new light.",
            reversedMeaning = "Resistance to change, lingering attachments, stagnation, or decay.",
            astrologicalSign = "Scorpio",
            element = "Water"
        ),
        TarotCard(
            id = 14,
            name = "Temperance",
            symbol = "🏺",
            keywords = listOf("Balance", "Moderation", "Harmony", "Divine Timing"),
            uprightMeaning = "Blending opposing forces to create peace, moderation, and alignment. Divine timing is at work.",
            reversedMeaning = "Imbalance, excess, lack of long-term vision, or discordant combinations.",
            astrologicalSign = "Sagittarius",
            element = "Fire"
        ),
        TarotCard(
            id = 15,
            name = "The Devil",
            symbol = "⛓️",
            keywords = listOf("Shadow Self", "Material Bonds", "Illusion of Capture", "Addiction"),
            uprightMeaning = "Recognizing areas where you feel chained by material desires, unhealthy habits, or shadow impulses.",
            reversedMeaning = "Breaking free, release of chains, shadow work, or reclaiming your personal power.",
            astrologicalSign = "Capricorn",
            element = "Earth"
        ),
        TarotCard(
            id = 16,
            name = "The Tower",
            symbol = "⚡",
            keywords = listOf("Sudden Change", "Revelation", "Demolition", "Breakthrough"),
            uprightMeaning = "Outdated structures or false beliefs are shattered by a lightning bolt of truth. A painful but necessary breakthrough.",
            reversedMeaning = "Avoiding disaster, delaying the inevitable collapse, or fear of change.",
            astrologicalSign = "Mars",
            element = "Fire"
        ),
        TarotCard(
            id = 17,
            name = "The Star",
            symbol = "⭐",
            keywords = listOf("Hope", "Serenity", "Inspiration", "Divine Protection"),
            uprightMeaning = "Renewed hope, spiritual healing, and a feeling of alignment and protection under the cosmic sky.",
            reversedMeaning = "Lack of faith, despair, discouragement, or uninspired state.",
            astrologicalSign = "Aquarius",
            element = "Air"
        ),
        TarotCard(
            id = 18,
            name = "The Moon",
            symbol = "🌙",
            keywords = listOf("Illusion", "Anxiety", "Unconscious Dreams", "Intuition"),
            uprightMeaning = "Navigating illusions, deep dreams, or hidden fears. Trust your instinct, not external appearances.",
            reversedMeaning = "Release of fear, uncovering secrets, resolving anxiety, or truth revealed.",
            astrologicalSign = "Pisces",
            element = "Water"
        ),
        TarotCard(
            id = 19,
            name = "The Sun",
            symbol = "☀️",
            keywords = listOf("Radiant Joy", "Success", "Vitality", "Self-Expression"),
            uprightMeaning = "Abundant warmth, victory, clarity, and pure child-like joy. A period of magnificent energy and vitality.",
            reversedMeaning = "Temporary cloudiness, minor delay, unrealistic optimism, or low energy.",
            astrologicalSign = "Sun",
            element = "Fire"
        ),
        TarotCard(
            id = 20,
            name = "Judgement",
            symbol = "🔔",
            keywords = listOf("Rebirth", "Absolution", "Calling", "Deep Decision"),
            uprightMeaning = "A call from deep within. Absolution of the past and stepping forward into a higher version of yourself.",
            reversedMeaning = "Self-doubt, ignoring your inner call, regret, or refusing to learn karmic lessons.",
            astrologicalSign = "Pluto",
            element = "Fire"
        ),
        TarotCard(
            id = 21,
            name = "The World",
            symbol = "🪐",
            keywords = listOf("Completion", "Wholeness", "Integration", "Ultimate Triumph"),
            uprightMeaning = "Successful completion of a major lifecycle. Ultimate triumph, absolute integration, and global belonging.",
            reversedMeaning = "Lack of closure, incomplete goals, taking shortcuts, or delayed completion.",
            astrologicalSign = "Saturn",
            element = "Earth"
        )
    )
}
