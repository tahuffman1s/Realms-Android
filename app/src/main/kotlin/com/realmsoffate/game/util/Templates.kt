package com.realmsoffate.game.util

/**
 * Tiny placeholder interpolator. Replaces every `{key}` substring in [template]
 * with the matching value from [values], in a single pass.
 *
 * Used by Phase 6 JSON-content milestones (M5a+) where prompt templates ship
 * as strings in JSON and runtime code binds context-specific values.
 *
 * Unmatched placeholders pass through untouched — callers that want strict
 * substitution can audit the result.
 */
object Templates {
    fun interpolate(template: String, values: Map<String, String?>): String {
        var out = template
        for ((k, v) in values) {
            out = out.replace("{$k}", v ?: "")
        }
        return out
    }
}
