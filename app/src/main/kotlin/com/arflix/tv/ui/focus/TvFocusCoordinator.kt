package com.arflix.tv.ui.focus

/** Details has one vertical path: buttons, season picker, episode rail. */
enum class FocusSection { BUTTONS, SEASONS, EPISODES }

data class DetailsFocusIndices(
    val buttonIndex: Int = 0,
    val episodeIndex: Int = 0,
    val seasonIndex: Int = 0
)

data class DetailsSectionContent(
    val episodeCount: Int = 0,
    val totalSeasons: Int = 1,
    val maxButtonIndex: Int = 3,
    val isTv: Boolean = true
) {
    fun itemCount(section: FocusSection): Int = when (section) {
        FocusSection.BUTTONS -> maxButtonIndex + 1
        FocusSection.SEASONS -> totalSeasons
        FocusSection.EPISODES -> episodeCount
    }

    fun isAvailable(section: FocusSection): Boolean = when (section) {
        FocusSection.BUTTONS -> true
        FocusSection.SEASONS -> isTv && totalSeasons > 1
        FocusSection.EPISODES -> isTv && episodeCount > 0
    }
}

fun indexInSection(section: FocusSection, indices: DetailsFocusIndices): Int = when (section) {
    FocusSection.BUTTONS -> indices.buttonIndex
    FocusSection.SEASONS -> indices.seasonIndex
    FocusSection.EPISODES -> indices.episodeIndex
}

fun isAtLeftmost(section: FocusSection, indices: DetailsFocusIndices): Boolean =
    indexInSection(section, indices) == 0

fun handleLeft(
    section: FocusSection,
    buttonIdx: Int, episodeIdx: Int, seasonIdx: Int,
    setButton: (Int) -> Unit, setEpisode: (Int) -> Unit, setSeason: (Int) -> Unit
): Boolean {
    when (section) {
        FocusSection.BUTTONS -> if (buttonIdx > 0) setButton(buttonIdx - 1)
        FocusSection.SEASONS -> if (seasonIdx > 0) setSeason(seasonIdx - 1)
        FocusSection.EPISODES -> if (episodeIdx > 0) setEpisode(episodeIdx - 1)
    }
    return true
}

fun handleRight(
    section: FocusSection,
    buttonIdx: Int, episodeIdx: Int, seasonIdx: Int,
    maxButtonIndex: Int, episodeCount: Int, seasonCount: Int,
    setButton: (Int) -> Unit, setEpisode: (Int) -> Unit, setSeason: (Int) -> Unit
): Boolean {
    when (section) {
        FocusSection.BUTTONS -> if (buttonIdx < maxButtonIndex) setButton(buttonIdx + 1)
        FocusSection.SEASONS -> if (seasonIdx < seasonCount - 1) setSeason(seasonIdx + 1)
        FocusSection.EPISODES -> if (episodeIdx < episodeCount - 1) setEpisode(episodeIdx + 1)
    }
    return true
}

fun moveUpSection(section: FocusSection, content: DetailsSectionContent): FocusSection = when (section) {
    FocusSection.BUTTONS, FocusSection.SEASONS -> FocusSection.BUTTONS
    FocusSection.EPISODES -> if (content.isAvailable(FocusSection.SEASONS)) FocusSection.SEASONS else FocusSection.BUTTONS
}

fun moveDownSection(section: FocusSection, content: DetailsSectionContent): FocusSection = when (section) {
    FocusSection.BUTTONS -> when {
        content.isAvailable(FocusSection.SEASONS) -> FocusSection.SEASONS
        content.isAvailable(FocusSection.EPISODES) -> FocusSection.EPISODES
        else -> FocusSection.BUTTONS
    }
    FocusSection.SEASONS -> if (content.isAvailable(FocusSection.EPISODES)) FocusSection.EPISODES else FocusSection.SEASONS
    FocusSection.EPISODES -> FocusSection.EPISODES
}
