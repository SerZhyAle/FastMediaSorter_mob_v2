@{
    Pins = @(
        @{
            name = 'hilt-android'
            gradleKey = 'hilt-android'
            docs = @{
                'docs/TECH_STACK.md' = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true; matcher = 'Hilt\s+(?<v>\d+(?:\.\d+)+)' }
                'CLAUDE.md' = @{ required = $false; matcher = $null }
            }
            policy = 'allMustMatch'
            exclude = @('## Version History[\s\S]*$')
        }
    )
}
