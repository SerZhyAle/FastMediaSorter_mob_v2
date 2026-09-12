package com.sza.fastmediasorter.data.identity.transfer.di

import com.sza.fastmediasorter.data.cloud.helpers.DropboxTransferredSecretRestorer
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveTransferredSecretRestorer
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInProviderKeys
import com.sza.fastmediasorter.domain.identity.transfer.TransferredSecretRestorer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.Multibinds
import dagger.multibindings.StringKey

/**
 * Flavor-neutral bindings for the transferable sign-in record (S2101).
 *
 * The port itself is bound per flavor (`IdentityModule` in `cloudEnabled`, `NoOpIdentityModule` in
 * `cloudDisabled`); only the restorer map lives here, because it is contributed by credential
 * managers in `src/main` that every flavor compiles.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TransferableSignInModule {

    /**
     * Declares the restorer map so it injects as empty when no provider contributes one.
     *
     * Without this, a flavor whose providers contribute nothing fails to compile with a missing
     * binding rather than injecting the empty map that correctly describes it.
     */
    @Multibinds
    abstract fun transferredSecretRestorers(): Map<String, TransferredSecretRestorer>

    /**
     * The two routes that keep a refresh token in app-owned storage, and so the only two whose
     * secret can travel. OneDrive is absent deliberately: MSAL publishes no refresh token.
     */
    @Binds
    @IntoMap
    @StringKey(TransferableSignInProviderKeys.DROPBOX)
    abstract fun bindDropboxSecretRestorer(
        restorer: DropboxTransferredSecretRestorer
    ): TransferredSecretRestorer

    @Binds
    @IntoMap
    @StringKey(TransferableSignInProviderKeys.GOOGLE_DRIVE_BROWSER)
    abstract fun bindGoogleDriveSecretRestorer(
        restorer: GoogleDriveTransferredSecretRestorer
    ): TransferredSecretRestorer
}
