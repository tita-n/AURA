package com.aura.ui.command

/**
 * Real command examples that AURA currently supports.
 *
 * This list powers the rotating Command Bar placeholder — a teaching surface, NOT a
 * second source of command definitions. Every entry maps to a genuinely implemented
 * capability (call, message, timer, percentage, open app, open settings, time query).
 * The list is immutable and contains no duplicates.
 *
 * Honesty invariant: never list a capability here that is not wired end-to-end.
 */
val SUPPORTED_COMMAND_EXAMPLES: List<String> = listOf(
    "Call Mum",
    "Message Sarah",
    "Set a timer for 10 min",
    "Calculate 15% of 4000",
    "Open Spotify",
    "Open Wi-Fi settings",
    "What time is it"
)

/**
 * Deterministic cyclic advance for the rotating placeholder.
 * Pure: advancing the index never touches command state, the resolver, or storage.
 */
fun nextExampleIndex(current: Int, size: Int): Int {
    if (size <= 0) return 0
    return (current + 1) % size
}

/**
 * Rotation should be paused whenever the user could be interacting with the bar
 * (focused, or has already typed something) or Home is not in the foreground.
 *
 * Pure decision used both by the rotation coroutine and by tests.
 */
fun shouldPauseRotation(homeInForeground: Boolean, query: String, focused: Boolean): Boolean {
    return !homeInForeground || focused || query.isNotBlank()
}
