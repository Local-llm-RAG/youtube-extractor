-- V28: Drop the obsolete document_section.level column.
--
-- The `level` column was introduced by V11 to record heading depth (1 for
-- top-level sections, 2+ for nested). In practice every writer either hard-codes
-- 1 (GROBID extractor) or computes a depth that no downstream consumer reads —
-- the only query that used it (`SectionFilter.maxLevel`) has been removed from
-- the codebase alongside this migration. Dropping the column removes dead
-- state and keeps the Hibernate `validate` contract in lockstep with the entity.
--
-- This migration is idempotent and safe to re-run.

ALTER TABLE document_section DROP COLUMN IF EXISTS level;
