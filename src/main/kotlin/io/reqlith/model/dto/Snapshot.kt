package io.reqlith.model.dto

/** A document serialized for storage, with the SHA-256 of its canonical form. */
class Snapshot(val json: String, val hash: String, val schemaVersion: Int)
