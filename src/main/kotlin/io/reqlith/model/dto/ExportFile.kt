package io.reqlith.model.dto

/** A downloadable file. */
class ExportFile(val name: String, val contentType: String, val bytes: ByteArray)
