package dev.minibrain.importing.application;

import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.importing.application.ImportPreview.Verdict;
import dev.minibrain.learning.persistence.LearningSessionRepository;
import dev.minibrain.revision.domain.RevisionChange;
import dev.minibrain.revision.persistence.RevisionRepository;
import dev.minibrain.skill.domain.Skill;
import dev.minibrain.skill.domain.SkillStatus;
import dev.minibrain.skill.persistence.EvidenceRepository;
import dev.minibrain.skill.persistence.OpenQuestionRepository;
import dev.minibrain.skill.persistence.SkillRelationRepository;
import dev.minibrain.skill.persistence.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Brief §19 "apply transaction": re-runs the preview on the same text and applies the chosen READY items.
 * Stateless on purpose: the server never trusts a preview kept by the client, it recomputes it.
 */
@Service
public class ImportApplier {

    public record Result(int applied, int skipped) {
    }

    public static class NotApplicableException extends RuntimeException {
        NotApplicableException(String message) {
            super(message);
        }
    }

    private final ImportPreviewer previewer;
    private final SkillRepository skills;
    private final EvidenceRepository evidence;
    private final OpenQuestionRepository questions;
    private final SkillRelationRepository relations;
    private final LearningSessionRepository sessions;
    private final RevisionRepository revisions;

    public ImportApplier(ImportPreviewer previewer, SkillRepository skills, EvidenceRepository evidence,
                         OpenQuestionRepository questions, SkillRelationRepository relations,
                         LearningSessionRepository sessions, RevisionRepository revisions) {
        this.previewer = previewer;
        this.skills = skills;
        this.evidence = evidence;
        this.questions = questions;
        this.relations = relations;
        this.sessions = sessions;
        this.revisions = revisions;
    }

    /** All or nothing: any failure rolls back every change of this import. */
    @Transactional
    public Result apply(String text, Set<Integer> selectedIds) {
        ImportPreview preview = previewer.preview(text);
        if (!preview.documentIssues().isEmpty()) {
            throw new NotApplicableException(preview.documentIssues().getFirst().message());
        }
        List<Change> chosen = preview.items().stream()
                .filter(i -> selectedIds.contains(i.id()) && i.verdict() == Verdict.READY)
                .map(Item::change)
                .sorted(Comparator.comparingInt(ImportApplier::order))
                .toList();
        // The session notes belong to every skill the document talks about: the ones changed now, and the ones that
        // already had these changes (re-import, or notes arriving later). Unticked new skills are skipped at linking.
        Set<String> touched = Stream.concat(
                        chosen.stream().flatMap(ImportApplier::skillsOf),
                        preview.items().stream()
                                .filter(i -> i.verdict() == Verdict.ALREADY_PRESENT && i.skill() != null)
                                .map(Item::skill))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        chosen.forEach(change -> apply(change, touched));
        // Knowledge changes create a Revision (brief §22), inside the same transaction as the changes.
        revisions.record(RevisionRepository.Source.IMPORT, preview.topic(), chosen.stream().map(ImportApplier::toRevision).toList());
        return new Result(chosen.size(), selectedIds.size() - chosen.size());
    }

    // Skills must exist before anything refers to them.
    private static int order(Change change) {
        return switch (change) {
            case Change.CreateSkill c -> 0;
            case Change.SuggestSkill c -> 1;
            case Change.ChangeStatus c -> 2;
            case Change.AddEvidence c -> 3;
            case Change.AddQuestion c -> 4;
            case Change.ResolveQuestion c -> 5;
            case Change.AddRelation c -> 6;
            case Change.Translate c -> 7;
            case Change.SaveSessionNotes c -> 8; // last: every touched skill exists by now
        };
    }

    private static Stream<String> skillsOf(Change change) {
        return switch (change) {
            case Change.CreateSkill c -> Stream.of(c.key());
            case Change.SuggestSkill c -> Stream.of(c.key());
            case Change.ChangeStatus c -> Stream.of(c.skill());
            case Change.AddEvidence c -> Stream.of(c.skill());
            case Change.AddQuestion c -> Stream.of(c.skill());
            case Change.ResolveQuestion c -> Stream.of(c.skill());
            case Change.AddRelation c -> Stream.of(c.from(), c.to());
            case Change.Translate c -> Stream.of(c.skill());
            case Change.SaveSessionNotes c -> Stream.empty();
        };
    }

    // Exhaustive switch over the sealed interface: a new Change kind will not compile until it is handled here.
    private void apply(Change change, Set<String> touched) {
        switch (change) {
            case Change.CreateSkill c -> skills.create(c.key(), c.name(), c.nameRu(), c.description(), c.descriptionRu(), c.status());
            // Choosing a suggested skill unlocks it (brief §14: it becomes a DISCOVERED skill).
            case Change.SuggestSkill c -> skills.create(c.key(), c.name(), c.nameRu(), c.reason(), null, SkillStatus.DISCOVERED);
            case Change.ChangeStatus c -> skills.changeStatus(id(c.skill()), c.to());
            case Change.AddEvidence c -> evidence.add(id(c.skill()), c.text(), c.textRu());
            case Change.AddQuestion c -> questions.add(id(c.skill()), c.text(), c.textRu());
            case Change.ResolveQuestion c -> {
                long skillId = id(c.skill());
                questions.findBySkillId(skillId).stream()
                        .filter(q -> q.text().equals(c.text()))
                        .findFirst()
                        .ifPresent(q -> questions.resolve(skillId, q.id()));
            }
            case Change.AddRelation c -> relations.add(id(c.from()), id(c.to()), c.type());
            case Change.Translate c -> {
                long skillId = id(c.skill());
                switch (c.target()) {
                    case NAME -> skills.setNameRu(skillId, c.ru());
                    case DESCRIPTION -> skills.setDescriptionRu(skillId, c.ru());
                    case EVIDENCE -> evidence.setTextRu(skillId, c.original(), c.ru());
                    case QUESTION -> questions.setTextRu(skillId, c.original(), c.ru());
                }
            }
            case Change.SaveSessionNotes c -> sessions.add(c.topic(), c.notesEn(), c.notesRu(), touched.stream()
                    .flatMap(key -> skills.findByKey(key).map(Skill::id).stream()) // skip skills that were not created
                    .toList());
        }
    }

    /** The history entry for an applied change. Exhaustive: a new Change kind must say how it is remembered. */
    private static RevisionChange toRevision(Change change) {
        return switch (change) {
            case Change.CreateSkill c -> RevisionChange.skillCreated(c.key(), c.status().name(), c.name());
            case Change.SuggestSkill c -> RevisionChange.skillCreated(c.key(), SkillStatus.DISCOVERED.name(), c.name());
            case Change.ChangeStatus c -> RevisionChange.statusChanged(c.skill(), c.from().name(), c.to().name());
            case Change.AddEvidence c -> RevisionChange.evidenceAdded(c.skill(), c.text());
            case Change.AddQuestion c -> RevisionChange.questionAdded(c.skill(), c.text());
            case Change.ResolveQuestion c -> RevisionChange.questionResolved(c.skill(), c.text());
            case Change.AddRelation c -> RevisionChange.relationAdded(c.from(), c.type().name(), c.to());
            case Change.Translate c -> RevisionChange.translationAdded(c.skill(), c.target().name().toLowerCase() + ": " + c.ru());
            case Change.SaveSessionNotes c -> RevisionChange.notesSaved(c.topic());
        };
    }

    private long id(String key) {
        return skills.findByKey(key).map(Skill::id)
                .orElseThrow(() -> new NotApplicableException("Skill disappeared during import: " + key));
    }
}
