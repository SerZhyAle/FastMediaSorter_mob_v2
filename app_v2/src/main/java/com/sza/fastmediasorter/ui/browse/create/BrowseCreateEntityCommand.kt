package com.sza.fastmediasorter.ui.browse.create

import com.sza.fastmediasorter.domain.model.MediaResource

/**
 * S0189 (Phase 09): shared pattern for "create-a-new-entity" actions in the Browse screen.
 *
 * Each concrete entity type (text note for S0189, drawing for S0191, …) implements this
 * interface so the toolbar / overflow registrar can host them generically.
 *
 * The interface intentionally stays narrow: it captures the two questions every command
 * must answer — "am I applicable for this resource?" and "create me with this name in
 * this parent" — without prescribing the dialog UI, the validation or the post-create
 * navigation (those concerns belong to the concrete implementation and its host).
 */
interface BrowseCreateEntityCommand {

    /**
     * `true` when this command should be offered for the given [resource] (e.g. local
     * writable directory for text notes; same plus images-enabled flavor for drawings).
     */
    fun isAvailableFor(resource: MediaResource): Boolean

    /**
     * Execute the create operation: produce a new entity called [name] under [parentPath].
     *
     * The implementation owns IO dispatch, error reporting and any post-create side-effects
     * (toast, list reload, editor launch). Returns once the action is dispatched — actual
     * IO may complete asynchronously.
     */
    fun requestCreate(parentPath: String, name: String)
}
