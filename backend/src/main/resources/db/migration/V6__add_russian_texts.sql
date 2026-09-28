-- Knowledge texts in two languages. The existing columns hold English (the canonical text used for matching);
-- the *_ru columns hold the Russian version and may be NULL (then the UI falls back to English).
ALTER TABLE skill ADD COLUMN name_ru TEXT;
ALTER TABLE skill ADD COLUMN description_ru TEXT;
ALTER TABLE evidence ADD COLUMN text_ru TEXT;
ALTER TABLE open_question ADD COLUMN text_ru TEXT;
