@{
    Pins = @(
        @{
            name = 'agp'
            gradleKey = 'agp'
            docs = @{
                'docs/TECH_STACK.md' = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true; matcher = 'AGP \(Android Gradle Plugin\)\s*\|\s*(?<v>[\d\.]+)' }
                'CLAUDE.md' = @{ required = $false; matcher = $null }
            }
            policy = 'allMustMatch'
            exclude = @()
        }
        @{
            name = 'kotlin'
            gradleKey = 'kotlin'
            docs = @{
                'docs/TECH_STACK.md' = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $false; matcher = $null }
                'CLAUDE.md' = @{ required = $true; matcher = 'Kotlin\s+(?<v>[\d\.]+\+?)' }
            }
            policy = 'allMustMatch'
            exclude = @()
        }
        @{
            name = 'hilt-android'
            gradleKey = 'hilt-android'
            docs = @{
                'docs/TECH_STACK.md' = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true; matcher = 'Hilt\s+(?<v>\d+(?:\.\d+)+)' }
                'CLAUDE.md' = @{ required = $false; matcher = $null }
            }
            policy = 'allMustMatch'
            exclude = @()
        }
        @{
            name = 'media3'
            gradleKey = 'media3'
            docs = @{
                'docs/TECH_STACK.md' = @{ required = $false; matcher = $null }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $true; matcher = 'Media3\s*\|\s*(?<v>[\d\.]+)' }
                'CLAUDE.md' = @{ required = $false; matcher = $null }
            }
            policy = 'allMustMatch'
            exclude = @()
        }
        @{
            name = 'nanohttpd'
            gradleKey = 'nanohttpd'
            docs = @{
                'docs/TECH_STACK.md' = @{ required = $true; matcher = 'nanohttpd:(?<v>[\d\.]+)' }
                'dev/TECH_REQUIREMENTS.md' = @{ required = $false; matcher = $null }
                'CLAUDE.md' = @{ required = $false; matcher = $null }
            }
            policy = 'allMustMatch'
            exclude = @()
        }
    )
}
