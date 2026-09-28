Translate knowledge texts from my learning app MiniBrain from English into Russian.

The MINIBRAIN_TRANSLATION_REQUEST JSON at the end lists, per skill, the English texts that have no Russian
version yet: the skill `name`, its `description`, `evidence` (what I demonstrated) and `openQuestions`.

## How to translate

- Natural, clear Russian for a software developer; keep the meaning exactly, do not add or drop anything.
- Established terms: use the usual Russian term if there is one and keep the English original in brackets the
  first time within a text, e.g. "Ограниченный контекст (Bounded Context)". Class names, code, `keys` and
  identifiers stay as they are.
- Evidence is written from my point of view ("Explained why ..." → "Объяснил, почему ...").

## Answer format

Reply with **one JSON block inside a ```json code block**, nothing else. Copy every English text **exactly**
into `en` (MiniBrain finds the text by it) and put the translation into `ru`. Include only what the request
lists; omit a field the request does not have.

```json
{
  "type": "MINIBRAIN_UPDATE",
  "schemaVersion": 2,
  "session": { "topic": "Translation" },
  "translations": [
    {
      "skill": "ddd.aggregate",
      "name": { "en": "Aggregate", "ru": "Агрегат" },
      "description": { "en": "<English exactly as given>", "ru": "<перевод>" },
      "evidence": [ { "en": "<English exactly as given>", "ru": "<перевод>" } ],
      "openQuestions": [ { "en": "<English exactly as given>", "ru": "<перевод>" } ]
    }
  ]
}
```

No comments inside the JSON, no trailing commas.

## MINIBRAIN_TRANSLATION_REQUEST
