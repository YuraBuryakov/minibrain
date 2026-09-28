package dev.minibrain.importing.application;

import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.importing.application.ImportPreview.Verdict;
import dev.minibrain.skill.domain.Skill;
import dev.minibrain.skill.domain.SkillStatus;
import dev.minibrain.skill.persistence.EvidenceRepository;
import dev.minibrain.skill.persistence.OpenQuestionRepository;
import dev.minibrain.skill.persistence.SkillRelationRepository;
import dev.minibrain.skill.persistence.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

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

    public ImportApplier(ImportPreviewer previewer, SkillRepository skills, EvidenceRepository evidence,
                         OpenQuestionRepository questions, SkillRelationRepository relations) {
        this.previewer = previewer;
        this.skills = skills;
        this.evidence = evidence;
        this.questions = questions;
        this.relations = relations;
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
        chosen.forEach(this::apply);
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
        };
    }

    // Exhaustive switch over the sealed interface: a new Change kind will not compile until it is handled here.
    private void apply(Change change) {
        switch (change) {
            case Change.CreateSkill c -> skills.create(c.key(), c.name(), c.description(), c.status());
            // Choosing a suggested skill unlocks it (brief §14: it becomes a DISCOVERED skill).
            case Change.SuggestSkill c -> skills.create(c.key(), c.name(), c.reason(), SkillStatus.DISCOVERED);
            case Change.ChangeStatus c -> skills.changeStatus(id(c.skill()), c.to());
            case Change.AddEvidence c -> evidence.add(id(c.skill()), c.text());
            case Change.AddQuestion c -> questions.add(id(c.skill()), c.text());
            case Change.ResolveQuestion c -> {
                long skillId = id(c.skill());
                questions.findBySkillId(skillId).stream()
                        .filter(q -> q.text().equals(c.text()))
                        .findFirst()
                        .ifPresent(q -> questions.resolve(skillId, q.id()));
            }
            case Change.AddRelation c -> relations.add(id(c.from()), id(c.to()), c.type());
        }
    }

    private long id(String key) {
        return skills.findByKey(key).map(Skill::id)
                .orElseThrow(() -> new NotApplicableException("Skill disappeared during import: " + key));
    }
}
