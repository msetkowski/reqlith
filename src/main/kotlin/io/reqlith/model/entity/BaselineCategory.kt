package io.reqlith.model.entity

/** Quality attribute a baseline non-functional requirement belongs to. Declaration order is display order. */
enum class BaselineCategory(val label: String) {
    PERFORMANCE("Performance"),
    AVAILABILITY("Availability"),
    RELIABILITY("Reliability"),
    SCALABILITY("Scalability"),
    SECURITY("Security"),
    USABILITY("Usability"),
    ACCESSIBILITY("Accessibility"),
    MAINTAINABILITY("Maintainability"),
    COMPLIANCE("Compliance"),
    OTHER("Other"),
}
