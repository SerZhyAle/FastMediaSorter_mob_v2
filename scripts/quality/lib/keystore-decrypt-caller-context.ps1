#requires -Version 7.0

# Dot-sourced by source-matchers.ps1, so these predicates share its script scope. Split out to keep
# that file under the 2000-line script ceiling.

# S4456: CryptoHelper.decrypt is an Android Keystore binder call plus AES-GCM and runs in whoever's
# context calls it. A DataStore read decrypted in the store - `dataStore.data.first()[k]?.let(decrypt)` or
# `dataStore.data.map { decode(it[k]) }` - therefore runs on the main thread for a UI collector or a
# lifecycleScope caller, and a `data.map` re-runs it on every write of ANY key of the shared DataStore.
# Two audit slices met the shape in separate stores (S3926, S4453). Lexical and file-level by necessity:
# a decrypt reached through an injected seam from a function other than the one that hops is not
# traceable here, so the rule flags a file that decrypts a DataStore value and carries neither a
# withContext( nor a flowOn( anywhere. A file with one wrapped and one unwrapped read passes; the rule
# stops a new store that never considered the dispatcher.
$script:KeystoreDecryptDataStoreRx = [regex]'\bdataStore\.data\b'
$script:KeystoreDecryptCallRx = [regex]'\bCryptoHelper(?:::|\.)decrypt\b'
$script:KeystoreDecryptHopRx = [regex]'\b(?:withContext|flowOn)\s*\('
$script:KeystoreDecryptCommentRx = [regex]'(?s)/\*.*?\*/|//[^\r\n]*'

function Find-KeystoreDecryptCallerContextLines([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return @() }
    # A mention in a comment is neither a decrypt nor a hop.
    $code = $script:KeystoreDecryptCommentRx.Replace($Text, '')
    if (-not $script:KeystoreDecryptDataStoreRx.IsMatch($code)) { return @() }
    if (-not $script:KeystoreDecryptCallRx.IsMatch($code)) { return @() }
    if ($script:KeystoreDecryptHopRx.IsMatch($code)) { return @() }
    $call = $script:KeystoreDecryptCallRx.Match($Text)
    return @($Text.Substring(0, $call.Index).Split("`n").Count)
}

function Measure-KeystoreDecryptCallerContextText([string]$Text) {
    return @(Find-KeystoreDecryptCallerContextLines $Text).Count
}

function New-KeystoreDecryptCallerContextRule {
    [pscustomobject]@{
        Name         = 'keystore-decrypt-caller-context'
        Extensions   = @('.kt')
        Roots        = @('app_v2/src', 'wear/src')
        PathFilter   = '^(?:app_v2|wear)/src/(?!androidTest/|test|benchmark/)[^/]+/'
        Baseline     = 'keystore-decrypt-caller-context-baseline.txt'
        ExcludeNames = @()
        CountInText  = { param($t) Measure-KeystoreDecryptCallerContextText $t }
        LocateInText = { param($t) Find-KeystoreDecryptCallerContextLines $t }
        FailMessage  = 'a file that decrypts a DataStore value with no withContext( or flowOn( anywhere in it (S4456). CryptoHelper.decrypt is a Keystore binder call plus AES-GCM that runs in the caller''s context - the main thread for a UI collector or a lifecycleScope caller. Inject @IoDispatcher and decrypt under withContext(ioDispatcher), or narrow the flow to the store''s own key, distinctUntilChanged() and flowOn(ioDispatcher) before the decrypt; SftpServerSettingsStore is the model.'
    }
}
