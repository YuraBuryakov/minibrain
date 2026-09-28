package dev.minibrain.importing.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minibrain.importing.application.ImportPreview.Issue;
import dev.minibrain.importing.application.ImportPreview.Issue.Code;
import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.importing.application.ImportPreview.Section;
import dev.minibrain.importing.application.ImportPreview.Verdict;
import dev.minibrain.importing.format.ChatNotes;
import dev.minibrain.importing.format.UpdateDocument;
import dev.minibrain.learning.persistence.LearningSessionRepository;
import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.SkillStatus;
import dev.minibrain.skill.query.KnowledgeGraph;
import dev.minibrain.skill.query.KnowledgeGraphQuery;
import dev.minibrain.skill.query.SkillDetails;
import dev.minibrain.skill.query.SkillDetailsQuery;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Brief §19: parse, validate, compare with the current state, build a Preview. Writes nothing.
 * Problems are attached to single items, so one bad line does not block the rest of the file.
 */
@Component
public class ImportPreviewer {

    private static final Pattern KEY = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*(\\.[a-z0-9]+(-[a-z0-9]+)*)*");
    // Pasted chat text: the JSON sits in a ```json fence, possibly after study notes that contain other code blocks.
    // (?:(?!```).) never crosses a fence, so a ```json example inside the notes cannot swallow the real block.
    private static final Pattern FENCED_UPDATE = Pattern.compile(
            "```(?:json)?\\s*(\\{(?:(?!```).)*?\"MINIBRAIN_UPDATE\"(?:(?!```).)*?})\\s*```", Pattern.DOTALL);

    private final ObjectMapper json;
    private final KnowledgeGraphQuery graph;
    private final SkillDetailsQuery detailsQuery;
    private final LearningSessionRepository sessions;

    public ImportPreviewer(ObjectMapper json, KnowledgeGraphQuery graph, SkillDetailsQuery detailsQuery,
                           LearningSessionRepository sessions) {
        this.json = json;
        this.graph = graph;
        this.detailsQuery = detailsQuery;
        this.sessions = sessions;
    }

    public ImportPreview preview(String text) {
        String source = text == null ? "" : text;
        var fenced = FENCED_UPDATE.matcher(source);
        boolean pastedChat = fenced.find();
        String jsonText = pastedChat ? fenced.group(1) : source.strip();
        // Fallback source of study notes: the chat text above the JSON block.
        ChatNotes chatNotes = pastedChat ? ChatNotes.extract(source.substring(0, fenced.start())).orElse(null) : null;

        UpdateDocument document;
        try {
            document = json.readValue(jsonText, UpdateDocument.class);
        } catch (JsonProcessingException e) {
            return documentProblem(Code.PARSE_ERROR, "This is not valid JSON: " + e.getOriginalMessage());
        }
        if (document == null) {
            return documentProblem(Code.PARSE_ERROR, "The text is empty.");
        }
        if (!UpdateDocument.TYPE.equals(document.type()) || !Integer.valueOf(UpdateDocument.SCHEMA_VERSION).equals(document.schemaVersion())) {
            return documentProblem(Code.UNSUPPORTED_DOCUMENT, "Expected \"type\": \"MINIBRAIN_UPDATE\" with \"schemaVersion\": 1.");
        }
        ChatNotes notes = ChatNotes.fromDocument(document).orElse(chatNotes); // the JSON's own notes win
        return new Build(document, graph.get(), notes).run();
    }

    private static ImportPreview documentProblem(Code code, String message) {
        return new ImportPreview(null, List.of(Issue.error(code, message)), List.of());
    }

    /** State of one preview run. */
    private final class Build {

        private final UpdateDocument document;
        private final ChatNotes notes;
        private final Map<String, KnowledgeGraph.Node> existing = new HashMap<>();
        private final Set<String> existingRelations = new HashSet<>();
        private final Map<String, Set<RelationType>> pairTypes = new HashMap<>(); // unordered pair -> types
        private final Map<String, Change.CreateSkill> newSkills = new LinkedHashMap<>();
        private final Map<String, Optional<SkillDetails>> detailsCache = new HashMap<>();
        private final Set<String> linked = new HashSet<>(); // skills touched by a READY relation
        private final Set<String> seen = new HashSet<>(); // in-file duplicates
        private final List<Item> items = new ArrayList<>();

        Build(UpdateDocument document, KnowledgeGraph current, ChatNotes notes) {
            this.document = document;
            this.notes = notes;
            current.nodes().forEach(n -> existing.put(n.key(), n));
            current.edges().forEach(e -> {
                existingRelations.add(e.from() + "|" + e.type() + "|" + e.to());
                pairTypes.computeIfAbsent(pair(e.from(), e.to()), k -> new HashSet<>()).add(e.type());
            });
        }

        ImportPreview run() {
            var pendingNewSkills = validateNewSkills();
            relations();
            pendingNewSkills.forEach(this::newSkillItem);
            orEmpty(document.changes()).forEach(this::skillChange);
            suggestedSkills();
            String topic = document.session() == null ? null : document.session().topic();
            sessionNotes(topic);
            return new ImportPreview(topic, List.of(), List.copyOf(items));
        }

        // ---- new skills: validated first, so changes and relations may reference them ----

        private record PendingSkill(UpdateDocument.NewSkill raw, List<Issue> issues, Change.CreateSkill change) {
        }

        private List<PendingSkill> validateNewSkills() {
            var pending = new ArrayList<PendingSkill>();
            for (var raw : orEmpty(document.newSkills())) {
                var issues = new ArrayList<Issue>();
                boolean keyValid = raw.key() != null && KEY.matcher(raw.key()).matches();
                if (!keyValid) {
                    issues.add(Issue.error(Code.INVALID_VALUE, "Key \"" + raw.key() + "\" must be lowercase words joined by '-' and '.', e.g. ddd.aggregate."));
                }
                if (isBlank(raw.name())) {
                    issues.add(Issue.error(Code.INVALID_VALUE, "Name is missing."));
                }
                SkillStatus status = parse(SkillStatus.class, raw.status());
                if (status == null) {
                    issues.add(Issue.error(Code.INVALID_VALUE, "Unknown status \"" + raw.status() + "\"."));
                }
                if (keyValid && existing.containsKey(raw.key())) {
                    issues.add(Issue.error(Code.DUPLICATE_SKILL_KEY, "Skill " + raw.key() + " already exists; its updates belong in \"changes\"."));
                } else if (keyValid && newSkills.containsKey(raw.key())) {
                    issues.add(Issue.error(Code.DUPLICATE_SKILL_KEY, "Skill " + raw.key() + " appears twice in this file."));
                }
                Change.CreateSkill change = null;
                if (issues.isEmpty()) {
                    change = new Change.CreateSkill(raw.key(), raw.name().strip(), blankToNull(raw.description()), status);
                    newSkills.put(raw.key(), change);
                }
                pending.add(new PendingSkill(raw, issues, change));
            }
            return pending;
        }

        private void newSkillItem(PendingSkill skill) {
            var issues = new ArrayList<>(skill.issues());
            if (skill.change() != null && !linked.contains(skill.change().key())) {
                issues.add(Issue.warning(Code.ORPHAN_SKILL, "Not connected to any skill: it will float outside the tree."));
            }
            String label = skill.raw().name() + " (" + skill.raw().key() + "), " + skill.raw().status();
            add(Section.NEW_SKILLS, skill.raw().key(), label, issues, skill.change(), false, false);
        }

        // ---- relations: all validated first, then RELATED_TO checked against stronger links in the same pair ----

        private record PendingRelation(UpdateDocument.NewRelation raw, List<Issue> issues, Change.AddRelation change, boolean present) {
        }

        private void relations() {
            var pending = new ArrayList<PendingRelation>();
            for (var raw : orEmpty(document.newRelations())) {
                var issues = new ArrayList<Issue>();
                RelationType type = parse(RelationType.class, raw.type());
                if (type == null) {
                    issues.add(Issue.error(Code.INVALID_VALUE, "Unknown relation type \"" + raw.type() + "\"."));
                }
                requireKnown(raw.from(), issues);
                requireKnown(raw.to(), issues);
                if (raw.from() != null && raw.from().equals(raw.to())) {
                    issues.add(Issue.error(Code.INVALID_RELATION, "A skill cannot relate to itself."));
                }
                Change.AddRelation change = null;
                boolean present = false;
                if (issues.isEmpty()) {
                    String id = raw.from() + "|" + type + "|" + raw.to();
                    present = existingRelations.contains(id) || !seen.add("relation|" + id);
                    change = new Change.AddRelation(raw.from(), type, raw.to());
                    if (!present) {
                        linked.add(raw.from());
                        linked.add(raw.to());
                        if (type != RelationType.RELATED_TO) {
                            pairTypes.computeIfAbsent(pair(raw.from(), raw.to()), k -> new HashSet<>()).add(type);
                        }
                    }
                }
                pending.add(new PendingRelation(raw, issues, change, present));
            }
            for (var relation : pending) {
                var issues = new ArrayList<>(relation.issues());
                boolean optIn = false;
                if (relation.change() != null && !relation.present() && relation.change().type() == RelationType.RELATED_TO
                        && pairTypes.getOrDefault(pair(relation.raw().from(), relation.raw().to()), Set.of()).stream()
                        .anyMatch(t -> t != RelationType.RELATED_TO)) {
                    issues.add(Issue.warning(Code.REDUNDANT_RELATION, "These skills already have a stronger relation; RELATED_TO adds nothing."));
                    optIn = true;
                }
                String label = name(relation.raw().from()) + " " + relation.raw().type() + " " + name(relation.raw().to());
                add(Section.RELATIONS, relation.raw().from(), label, issues, relation.change(), relation.present(), optIn);
            }
        }

        // ---- changes to existing (or new) skills ----

        private void skillChange(UpdateDocument.SkillChange raw) {
            String key = raw.skill();
            boolean isNew = newSkills.containsKey(key);
            var unknown = new ArrayList<Issue>();
            requireKnown(key, unknown);
            SkillDetails current = unknown.isEmpty() && !isNew ? details(key) : null;
            String name = name(key);

            if (raw.proposedStatus() != null) {
                var issues = new ArrayList<>(unknown);
                SkillStatus to = parse(SkillStatus.class, raw.proposedStatus());
                if (to == null) {
                    issues.add(Issue.error(Code.INVALID_VALUE, "Unknown status \"" + raw.proposedStatus() + "\"."));
                } else if (isNew) {
                    issues.add(Issue.error(Code.INVALID_VALUE, "A new skill gets its status in \"newSkills\"."));
                }
                SkillStatus from = current == null ? null : current.status();
                boolean present = issues.isEmpty() && from == to;
                boolean downgrade = issues.isEmpty() && from != null && to.ordinal() < from.ordinal();
                if (downgrade) {
                    issues.add(Issue.warning(Code.STATUS_DOWNGRADE, "Lowers the status from " + from + " to " + to + "."));
                }
                var change = issues.stream().anyMatch(i -> i.severity() == Issue.Severity.ERROR) ? null : new Change.ChangeStatus(key, from, to);
                add(Section.STATUS_CHANGES, key, name + ": " + from + " → " + raw.proposedStatus(), issues, change, present, downgrade);
            }

            Set<String> evidenceTexts = current == null ? Set.of() : new HashSet<>(current.evidence().stream().map(SkillDetails.Evidence::text).toList());
            for (String text : nonBlank(raw.evidenceAdded())) {
                boolean present = unknown.isEmpty() && (evidenceTexts.contains(text) || !seen.add("evidence|" + key + "|" + text));
                add(Section.EVIDENCE, key, name + ": " + text, unknown, new Change.AddEvidence(key, text), present, false);
            }

            Set<String> questionTexts = current == null ? Set.of() : new HashSet<>(current.openQuestions().stream().map(SkillDetails.Question::text).toList());
            for (String text : nonBlank(raw.openQuestionsAdded())) {
                boolean present = unknown.isEmpty() && (questionTexts.contains(text) || !seen.add("question|" + key + "|" + text));
                add(Section.OPEN_QUESTIONS, key, name + ": + " + text, unknown, new Change.AddQuestion(key, text), present, false);
            }

            for (String text : nonBlank(raw.openQuestionsResolved())) {
                var issues = new ArrayList<>(unknown);
                boolean present = false;
                if (issues.isEmpty()) {
                    var question = current == null ? Optional.<SkillDetails.Question>empty()
                            : current.openQuestions().stream().filter(q -> q.text().equals(text)).findFirst();
                    if (question.isEmpty()) {
                        issues.add(Issue.error(Code.UNKNOWN_OPEN_QUESTION, "No open question with exactly this text on " + name + "."));
                    } else {
                        present = question.get().resolvedAt() != null || !seen.add("resolve|" + key + "|" + text);
                    }
                }
                add(Section.OPEN_QUESTIONS, key, name + ": resolve \"" + text + "\"", issues, new Change.ResolveQuestion(key, text), present, false);
            }
        }

        // ---- suggested skills: never selected by default (brief §20) ----

        private void suggestedSkills() {
            for (var raw : orEmpty(document.suggestedSkills())) {
                var issues = new ArrayList<Issue>();
                if (raw.key() == null || !KEY.matcher(raw.key()).matches()) {
                    issues.add(Issue.error(Code.INVALID_VALUE, "Key \"" + raw.key() + "\" must be lowercase words joined by '-' and '.'."));
                }
                if (isBlank(raw.name())) {
                    issues.add(Issue.error(Code.INVALID_VALUE, "Name is missing."));
                }
                boolean present = issues.isEmpty() && (existing.containsKey(raw.key()) || newSkills.containsKey(raw.key())
                        || !seen.add("suggested|" + raw.key()));
                var change = issues.isEmpty() ? new Change.SuggestSkill(raw.key(), raw.name().strip(), blankToNull(raw.reason())) : null;
                add(Section.SUGGESTED_SKILLS, raw.key(), raw.name() + " (" + raw.key() + ")", issues, change, present, true);
            }
        }

        // ---- study notes from the pasted chat text ----

        private void sessionNotes(String topic) {
            if (notes == null) return;
            String languages = notes.en() != null && notes.ru() != null ? "English and Русский"
                    : notes.en() != null ? "English only" : "Русский only";
            boolean present = sessions.exists(notes.en(), notes.ru());
            add(Section.SESSION_NOTES, null, "Study notes (" + languages + ")", List.of(),
                    new Change.SaveSessionNotes(topic, notes.en(), notes.ru()), present, false);
        }

        // ---- helpers ----

        private void add(Section section, String skill, String label, List<Issue> issues, Change change, boolean present, boolean optIn) {
            boolean invalid = issues.stream().anyMatch(i -> i.severity() == Issue.Severity.ERROR);
            Verdict verdict = invalid ? Verdict.INVALID : present ? Verdict.ALREADY_PRESENT : Verdict.READY;
            boolean selected = verdict == Verdict.READY && !optIn;
            items.add(new Item(items.size() + 1, section, skill, label, verdict, List.copyOf(issues), selected,
                    verdict == Verdict.READY ? change : null));
        }

        private void requireKnown(String key, List<Issue> issues) {
            if (key == null || !(existing.containsKey(key) || newSkills.containsKey(key))) {
                issues.add(Issue.error(Code.UNKNOWN_SKILL_REFERENCE, "Unknown skill \"" + key + "\": it is neither in MiniBrain nor in newSkills."));
            }
        }

        private SkillDetails details(String key) {
            return detailsCache.computeIfAbsent(key, detailsQuery::find).orElse(null);
        }

        private String name(String key) {
            if (existing.containsKey(key)) return existing.get(key).name();
            if (newSkills.containsKey(key)) return newSkills.get(key).name();
            return String.valueOf(key);
        }
    }

    // ---- small static helpers ----

    private static String pair(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return value == null ? null : Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static List<String> nonBlank(List<String> texts) {
        return orEmpty(texts).stream().filter(t -> !isBlank(t)).map(String::strip).toList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.strip();
    }
}
