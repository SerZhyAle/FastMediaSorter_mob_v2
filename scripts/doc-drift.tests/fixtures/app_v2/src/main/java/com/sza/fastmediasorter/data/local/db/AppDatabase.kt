// Fixture for the room-schema-version pin (S1496). Get-GradlePins reads this exact path through
// Read-RequiredText, which throws when the file is absent - the missing fixture was the second
// reason the GradleParser suite threw at load instead of running.
//
// The version below is fixture-local and deliberately unlike production, so a test result can
// never be mistaken for a reading of the real schema, and a production bump does not touch tests.

package com.sza.fastmediasorter.data.local.db

@Database(
    entities = [],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase
