# Tactical Plan: Phase 3 - AI Integration and "Magic"

**Parent Strategy:** [STRATEGIC_GROWTH_PLAN.md](STRATEGIC_GROWTH_PLAN.md)
**Focus:** The app thinks and works for the user.

---

## 1. On-Device Intelligence

### Objective

Provide smart features while maintaining user privacy (no data upload).

### Tactical Initiatives

- [ ] **Local ML Pipeline**:
  - integrate Google ML Kit for on-device vision API.
  - implement background job for progressive image analysis.
- [ ] **Auto-Tagging System**:
  - map ML Kit labels to user-friendly tags.
  - create a database schema for file-tag relationships.
- [ ] **Clustering**:
  - implement algorithms to group photos by location (GeoHash) and time.

## 2. Smart Sort

### Objective

Reduce manual file organization effort to near zero.

### Tactical Initiatives

- [ ] **Magic Sort Algorithm**:
  - develop a heuristic engine that suggests folders based on file extension, date, and source.
  - allow users to "teach" the engine by correcting suggestions.
- [ ] **Predictive Actions**:
  - track user copy/move patterns locally.
  - surface "Move to [Folder]" chips in the UI based on history.

## 3. Intelligent Search

### Objective

Find any file using natural language.

### Tactical Initiatives

- [ ] **Search Engine**:
  - implement Full-Text Search (FTS) in Room database.
  - parse natural language queries (e.g., extract dates and keywords).
- [ ] **OCR Integration**:
  - run text recognition on images and index the results.
  - highlight matched text areas in image viewer.

## 4. Duplicate Management

### Objective

Clean up storage by removing redundant copies intelligently.

### Tactical Initiatives

- [ ] **Perceptual Hashing**:
  - implement pHash algorithm to compare image visual similarity.
  - optimize comparison loop for N^2 complexity issues.
- [ ] **Best Take Selector**:
  - analyze blur, exposure, and face smiles to score photos.
  - suggest keeping the highest-scored photo in a duplicate group.
