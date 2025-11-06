# Database Schemas

This directory contains exported Room database schemas for version tracking and migration validation.

## Files

- `com.sza.fastmediasorter.data.AppDatabase/3.json` - Current schema (version 3)
- Future schema files will be added here as the database evolves

## Usage

These schema files are automatically generated when `exportSchema = true` is set in `@Database` annotation.

They are used for:
- Migration validation during build
- Version comparison between releases  
- Automated migration testing
- Documentation of schema changes

## Git Tracking

Schema files should be committed to git to track database evolution over time.