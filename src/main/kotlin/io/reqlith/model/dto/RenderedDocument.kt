package io.reqlith.model.dto

/** Basic information, solution design and all elements rendered to HTML, keyed by element key. */
data class RenderedDocument(
    val basicInfo: String,
    val elements: Map<String, String>,
    val roles: Map<String, Pair<String, String>>,
    val solutionDesign: String,
) {
    companion object {
        fun of(document: ProjectDocument, render: (String?) -> String) = RenderedDocument(
            basicInfo = render(document.basicInfo),
            elements = document.elements.associate { it.key to render(it.body) },
            roles = document.roles.associate { it.key to (render(it.globalDescription) to render(it.contextInProject)) },
            solutionDesign = render(document.solutionDesign),
        )
    }
}
