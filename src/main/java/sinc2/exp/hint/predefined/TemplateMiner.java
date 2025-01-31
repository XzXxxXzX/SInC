package sinc2.exp.hint.predefined;

import sinc2.kb.Record;
import sinc2.rule.Eval;
import sinc2.rule.EvalMetric;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * This is the base class of all predefined template miners. Each miner should implement the "matchTemplate" method.
 * Followings are currently defined templates:
 * 1. Type Inference:
 *   h(X) :- p(..., Xi, ...)
 * 2. Reflexive:
 *   h(X, X) :-
 * 3. Subsumption:
 *   h(X0, ..., Xk) :- p(X0, ..., Xk); [(p, q)]
 * 4. Dual:
 *   h(X, Y) :- p(Y, X)
 * 5. Transition
 *   h(X, Y) :- p(X, Z), q(Z, Y); [(h, p, q)]
 *   h(X, Y) :- p(Y, Z), q(Z, X); [(h, p, q)]
 * 6. Shared Source/Sink
 *   h(X, Y) :- p(Z, X), q(Z, Y); [(h, p, q)]
 *   h(X, Y) :- p(X, Z), q(Y, Z); [(h, p, q)]
 *
 * @since 2.0
 */
public abstract class TemplateMiner {
    /* Mining Thresholds */
    public static double COVERAGE_THRESHOLD = 0.2;
    public static double TAU_THRESHOLD = 0.8;

    /**
     * This enumeration shows all available templates.
     */
    public enum TemplateType {
        TYPE_INFERENCE(new TypeInferenceMiner()),
        REFLEXIVE(new ReflexiveMiner()),
        SUBSUMPTION(new SubsumptionMiner()),
        DUAL(new DualMiner()),
        TRANSITION(new TransitionMiner()),
        SHARED_SOURCE_SINK(new SharedSourceSinkMiner());

        private static final Map<String, TemplateMiner> nameMap = new HashMap<>();
        static {
            for (TemplateType type: TemplateType.values()) {
                nameMap.put(type.miner.templateName(), type.miner);
            }
        }

        static public TemplateMiner getMinerByName(String name) {
            return nameMap.get(name);
        }

        public final TemplateMiner miner;

        TemplateType(TemplateMiner miner) {
            this.miner = miner;
        }
    }

    /**
     * This method finds all instantiations complying the template and the threshold restrictions from given relations.
     * It also records the positive entailments in corresponding sets.
     *
     * @param relations           The list of input relations
     * @param positiveEntailments The list of positive entailments by the matched rules in the relations
     * @param functorNames        The names of the input relations
     * @return All matched rules and their evaluations
     */
    public abstract List<MatchedRule> matchTemplate(
            List<Set<Record>> relations, List<Set<Record>> positiveEntailments, List<String> functorNames
    );

    /**
     * Check whether a rule satisfies the mining restrictions. If it does, add the rule to the result list and add all
     * positive entailments of the rule to the corresponding entailment set.
     *
     * @param headRelation The head relation
     * @param entailedHead The set of entailed records in the head
     * @param entailments  The entailments of the rule
     * @param matchedRules The list of discovered rules of the template
     * @param ruleString   The rule string
     */
    protected void checkThenAdd(
            Set<Record> headRelation, Set<Record> entailedHead, Set<Record> entailments, List<MatchedRule> matchedRules,
            String ruleString
    ) {
        if (COVERAGE_THRESHOLD > ((double) entailments.size()) / headRelation.size()) {
            return;
        }
        Set<Record> positive_entailments = new HashSet<>();
        for (Record entailment: entailments) {
            if (headRelation.contains(entailment)) {
                positive_entailments.add(entailment);
            }
        }
        double coverage = ((double) positive_entailments.size()) / headRelation.size();
        Eval eval = new Eval(null, positive_entailments.size(), entailments.size(), templateLength());
        if (COVERAGE_THRESHOLD <= coverage && TAU_THRESHOLD <= eval.value(EvalMetric.CompressionRatio)) {
            /* Add result */
            matchedRules.add(new MatchedRule(ruleString, eval, coverage));
            entailedHead.addAll(positive_entailments);
        }
    }

    /**
     * Dump the rules matched by the template to a ".tsv" file. Rules are sorted in descent order of compression ratio.
     *
     * @param matchedRules The matched rules
     * @param dirPath      The path to the dir where the rules file locates.
     */
    public void dumpResult(List<MatchedRule> matchedRules, String dirPath) throws FileNotFoundException {
        matchedRules.sort(
                Comparator.comparingDouble((MatchedRule e) -> e.eval.value(EvalMetric.CompressionRatio)).reversed()
        );
        PrintWriter writer = new PrintWriter(Paths.get(dirPath, String.format("rules_%s.tsv", templateName())).toFile());
        writer.printf("rule\t|r|\tE+\tE-\tFC\tτ\tδ\n");
        for (MatchedRule rule_info: matchedRules) {
            writer.print(String.format("%s\t%d\t%d\t%d\t%.2f\t%.2f\t%d\n",
                    rule_info.rule, rule_info.eval.getRuleLength(), (int) rule_info.eval.getPosEtls(),
                    (int) rule_info.eval.getNegEtls(), rule_info.factCoverage, rule_info.eval.value(EvalMetric.CompressionRatio),
                    (int) rule_info.eval.value(EvalMetric.CompressionCapacity)
            ));
        }
        writer.close();
    }

    public abstract int templateLength();

    public abstract String templateName();

}
