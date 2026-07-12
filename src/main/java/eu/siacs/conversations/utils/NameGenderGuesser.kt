package eu.siacs.conversations.utils

/**
 * Best-effort, fully offline guess of a first name's grammatical gender — used only to pick
 * between "был"/"была" (or similar gendered forms) in translated strings, never surfaced to the
 * user as a claim about anyone's actual gender. Deliberately conservative: returns UNKNOWN
 * (callers fall back to the masculine/default string) whenever the name isn't recognized, rather
 * than guessing from an unreliable heuristic.
 *
 * No network calls — sending contact names to a third-party gender-lookup API would leak contact
 * data for a cosmetic grammar choice, which has no place in a privacy-focused XMPP client.
 */
object NameGenderGuesser {

    enum class Gender {
        MASCULINE,
        FEMININE,
        UNKNOWN,
    }

    // Common Russian diminutives/full names ending in -а/-я that are masculine despite the
    // typically-feminine ending (Никита, Илья, Кузьма, ... and their casual diminutive forms).
    private val RUSSIAN_MASCULINE_EXCEPTIONS =
        setOf(
            "никита", "илья", "фома", "кузьма", "лука", "дима", "митя", "юра", "гоша", "жора",
            "лёва", "лева", "лёша", "леша", "ваня", "петя", "костя", "серёжа", "сережа", "паша",
            "миша", "боря", "толя", "коля", "гриша", "витя", "вова", "гена", "тёма", "тема",
            "кирюша", "андрюша", "лёня", "леня",
            // The "-ка" diminutive suffix (Димка, Вовка, ...) attaches to already-masculine names
            // and stays masculine — distinct from names that are simply -а/-я by root (Танька,
            // from Таня, is feminine because Таня already was, not because of the suffix).
            "димка", "вовка", "мишка", "витька", "колька", "толька", "гришка", "борька",
            "петька", "ванька", "юрка", "лёвка", "левка", "лёшка", "лешка", "гошка", "жорка",
        )

    // Russian endearment suffixes are productive (Дима → Димуля → Димуленька → Димусенька →
    // Димончик, indefinitely) — no exact-match list can ever be complete. For a handful of stems
    // confident enough not to collide with any unrelated name (e.g. nothing feminine starts with
    // "дим"), match by prefix instead so the whole family is covered at once. Deliberately not
    // done for shorter/riskier stems (e.g. "вит" would also catch "Виталина", "миш" would catch
    // "Мишель") — those stay exact-match-only above, falling back to UNKNOWN rather than risk a
    // wrong guess.
    private val RUSSIAN_MASCULINE_STEMS =
        setOf("дим", "гош", "жор", "никит", "кузьм", "серёж", "сереж", "кирюш", "андрюш", "мит", "вов")

    // Names commonly used by either gender in Russian — deliberately treated as UNKNOWN rather
    // than guessed, since a wrong guess here is common enough to be worse than falling back.
    private val RUSSIAN_AMBIGUOUS =
        setOf("саша", "шура", "женя", "валя", "слава")

    // Feminine Russian names that don't end in -а/-я (the rare exceptions to that rule).
    private val RUSSIAN_FEMININE_EXCEPTIONS = setOf("любовь")

    // Small curated set of common English/Western first names. Not exhaustive — anything not in
    // here (or not recognized as Cyrillic) falls back to UNKNOWN, which callers treat as the
    // default/masculine string. Good enough to cover the common case; a per-contact override is
    // the right answer for names this list doesn't know, not a bigger list or an ML model.
    private val WESTERN_FEMININE =
        setOf(
            "mary", "patricia", "jennifer", "linda", "elizabeth", "barbara", "susan", "jessica",
            "sarah", "karen", "nancy", "lisa", "margaret", "betty", "sandra", "ashley", "kimberly",
            "emily", "donna", "michelle", "carol", "amanda", "melissa", "deborah", "stephanie",
            "rebecca", "laura", "sharon", "cynthia", "kathleen", "amy", "angela", "shirley",
            "anna", "brenda", "pamela", "emma", "nicole", "helen", "samantha", "katherine",
            "christine", "debra", "rachel", "catherine", "carolyn", "janet", "ruth", "maria",
            "heather", "diane", "julie", "joyce", "victoria", "olivia", "kelly", "christina",
            "lauren", "joan", "evelyn", "judith", "megan", "andrea", "cheryl", "hannah", "jacqueline",
            "martha", "gloria", "teresa", "sara", "janice", "julia", "marie", "madison", "grace",
            "judy", "theresa", "beverly", "denise", "marilyn", "amber", "danielle", "abigail",
            "brittany", "diana", "natalie", "sophia", "isabella", "charlotte", "mia", "ava",
            "ella", "scarlett", "chloe", "zoe", "lily", "alexandra", "alice", "eva", "irene",
        )
    private val WESTERN_MASCULINE =
        setOf(
            "james", "robert", "john", "michael", "david", "william", "richard", "joseph",
            "thomas", "charles", "christopher", "daniel", "matthew", "anthony", "mark", "donald",
            "steven", "andrew", "paul", "joshua", "kenneth", "kevin", "brian", "george", "edward",
            "ronald", "timothy", "jason", "jeffrey", "ryan", "jacob", "gary", "nicholas", "eric",
            "jonathan", "stephen", "larry", "justin", "scott", "brandon", "benjamin", "samuel",
            "gregory", "alexander", "frank", "patrick", "raymond", "jack", "dennis", "jerry",
            "tyler", "aaron", "jose", "adam", "henry", "nathan", "douglas", "zachary", "peter",
            "kyle", "walter", "harold", "carl", "jeremy", "gerald", "keith", "roger", "terry",
            "austin", "sean", "christian", "ethan", "arthur", "noah", "lawrence", "jesse",
            "willie", "elijah", "juan", "billy", "bruce", "albert", "gabriel", "logan", "wayne",
            "alan", "dylan", "harry", "vladimir", "ivan", "dmitri", "dmitry", "alex", "max",
            "leo", "oscar", "victor", "felix", "simon", "louis", "julian",
        )

    fun guess(rawName: String?): Gender {
        val name = rawName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase()
        if (name.isNullOrEmpty()) return Gender.UNKNOWN
        return if (isCyrillic(name)) guessRussian(name) else guessWestern(name)
    }

    private fun isCyrillic(name: String): Boolean = name.any { it.code in 0x0400..0x04FF }

    private fun guessRussian(name: String): Gender {
        if (name in RUSSIAN_AMBIGUOUS) return Gender.UNKNOWN
        if (name in RUSSIAN_MASCULINE_EXCEPTIONS) return Gender.MASCULINE
        if (name in RUSSIAN_FEMININE_EXCEPTIONS) return Gender.FEMININE
        if (RUSSIAN_MASCULINE_STEMS.any { name.startsWith(it) }) return Gender.MASCULINE
        val last = name.last()
        return if (last == 'а' || last == 'я') Gender.FEMININE else Gender.MASCULINE
    }

    private fun guessWestern(name: String): Gender {
        if (name in WESTERN_FEMININE) return Gender.FEMININE
        if (name in WESTERN_MASCULINE) return Gender.MASCULINE
        return Gender.UNKNOWN
    }
}
