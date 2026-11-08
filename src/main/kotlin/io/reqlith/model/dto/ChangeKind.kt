package io.reqlith.model.dto

enum class ChangeKind(val label: String) {
    NEW("New"),
    CHANGED("Changed"),
    UNCHANGED("Unchanged"),
    REMOVED("Removed"),
}
