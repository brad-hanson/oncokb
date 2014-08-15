
package org.mskcc.cbio.oncokb.dao.importor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mskcc.cbio.oncokb.bo.AlterationBo;
import org.mskcc.cbio.oncokb.bo.ArticleBo;
import org.mskcc.cbio.oncokb.bo.ClinicalTrialBo;
import org.mskcc.cbio.oncokb.bo.DrugBo;
import org.mskcc.cbio.oncokb.bo.EvidenceBlobBo;
import org.mskcc.cbio.oncokb.bo.GeneBo;
import org.mskcc.cbio.oncokb.bo.NccnGuidelineBo;
import org.mskcc.cbio.oncokb.bo.TumorTypeBo;
import org.mskcc.cbio.oncokb.model.Alteration;
import org.mskcc.cbio.oncokb.model.AlterationType;
import org.mskcc.cbio.oncokb.model.Article;
import org.mskcc.cbio.oncokb.model.ClinicalTrial;
import org.mskcc.cbio.oncokb.model.Drug;
import org.mskcc.cbio.oncokb.model.Evidence;
import org.mskcc.cbio.oncokb.model.EvidenceBlob;
import org.mskcc.cbio.oncokb.model.EvidenceType;
import org.mskcc.cbio.oncokb.model.Gene;
import org.mskcc.cbio.oncokb.model.LevelOfEvidence;
import org.mskcc.cbio.oncokb.model.NccnGuideline;
import org.mskcc.cbio.oncokb.model.TumorType;
import org.mskcc.cbio.oncokb.util.ApplicationContextSingleton;
import org.mskcc.cbio.oncokb.util.FileUtils;
import org.mskcc.cbio.oncokb.util.GeneAnnotatorMyGeneInfo2;
import org.mskcc.cbio.oncokb.util.NcbiEUtils;

/**
 *
 * @author jgao
 */
public final class QuestDocAnnotationParser {
    private static final String GENE_P = "^>Gene: ?(.+)";
    private static final String GENE_BACKGROUND_P = "^Background:? *";
    
    private static final String MUTATIONS_P = "^Mutations";
    
    private static final String MUTATION_P = "^Mutations?: ?(.+)";
    private static final String MUTATION_EFFECT_P = "^Mutation effect: ?([^\\(]+)(\\(PMIDs?:.+\\))?";
    private static final String MUTATION_EFFECT_DESCRIPTION_P = "^Description of mutation effect:? *";
    
    private static final String TUMOR_TYPE_P = "^Tumor type: ?(.+)";
    
    private static final String PREVALENCE_P = "^Prevalence:? *";
    
    private static final String PROGNOSTIC_IMPLICATIONS_P = "^Prognostic implications:? *";
    
    private static final String STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY_P = "^Standard therapeutic implications for drug sensitivity:?";
    private static final String STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVE_TO_P = "^Sensitive to: ?(.*)";
    private static final String STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVE_EVIDENCE_P = "^Description of evidence: ?(.*)";
    
    private static final String STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE_P = "^Standard therapeutic implications for drug resistance:?";
    private static final String STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANT_TO_P = "^Resistant to: ?(.*)";
    private static final String STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANT_EVIDENCE_P = "^Description of evidence: ?(.*)";
    
    private static final String NCCN_GUIDELINES_P = "^NCCN guidelines$";
    private static final String NCCN_DISEASE_P = "^Disease: ?(.+)";
    private static final String NCCN_VERSION_P = "^Version: ?(.+)";
    private static final String NCCN_PAGES_P = "^Pages: ?(.+)";
        
    private static final String INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY_P = "^Investigational therapeutic implications for drug sensitivity$";
    private static final String INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVE_TO_P = "^Sensitive to \\(Highest level of evidence\\): ?(.*)";
    private static final String INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVE_EVIDENCE_P = "^Description of evidence: ?(.*)";
    
    private static final String INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE_P = "^Investigational therapeutic implications for drug resistance$";
    private static final String INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANT_TO_P = "^Resistant to: ?(.*)";
    private static final String INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANT_EVIDENCE_P = "^Description of evidence: ?(.*)";
    
    private static final String ONGOING_CLINICAL_TRIALS_P = "^Ongoing clinical trials:?$";
    private static final String CLINICAL_TRIALS_P = "(^NCT[0-9]+)";
    
    private static final String INVESTIGATIONAL_INTERACTING_GENE_ALTERATIONS_P = "^Interacting gene alterations$";
    
    private static final String[] CANCER_HEADERS_P = new String[] {
        PREVALENCE_P,
        PROGNOSTIC_IMPLICATIONS_P,
        STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY_P,
        STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE_P,
        NCCN_GUIDELINES_P,
        INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY_P,
        INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE_P,
        ONGOING_CLINICAL_TRIALS_P,
        INVESTIGATIONAL_INTERACTING_GENE_ALTERATIONS_P
    };
    
    private QuestDocAnnotationParser() {
        throw new AssertionError();
    }
    
    private static final String QUEST_CURATION_FILE = "/data/quest-curations.txt";
    
    public static void main(String[] args) throws IOException {
        parse(QUEST_CURATION_FILE);
    }
    
    private static void parse(String file) throws IOException {
        List<String> lines = FileUtils.readLinesStream(
                GeneLabelImporter.class.getResourceAsStream(file));
        List<int[]> geneLines = extractLines(lines, 0, lines.size(), GENE_P, GENE_P, -1);
        for (int[] ix : geneLines) {
            parseGene(lines, ix[0], ix[1]);
        }
    }
    
    private static void parseGene(List<String> lines, int start, int end) throws IOException {
        if (!lines.get(start).startsWith("Gene: ")) {
            System.err.println("Gene line should start with Gene: ");
        }
        
        System.out.println("##"+lines.get(start));
        
        GeneBo geneBo = ApplicationContextSingleton.getGeneBo();
        
        Pattern p = Pattern.compile(GENE_P);
        Matcher m = p.matcher(lines.get(start));
        m.matches();
        String hugo = m.group(1);
        Gene gene = geneBo.findGeneByHugoSymbol(hugo);
        if (gene == null) {
            System.out.println("Could not find gene "+hugo+". Loading from MyGene.Info...");
            gene = GeneAnnotatorMyGeneInfo2.readByHugoSymbol(hugo);
            if (gene == null) {
                System.err.println("Could not find gene "+hugo+" either.");
            }
            geneBo.save(gene);
        }
        
        // background
        int[] geneBgLines = parseGeneBackground(gene, lines, start, end);
        
        // mutations
        parseMutations(gene, lines, geneBgLines[1], end);
    }
    
    private static int[] parseGeneBackground(Gene gene, List<String> lines, int start, int end) {
        List<int[]> backgroundLines = extractLines(lines, start, end, GENE_BACKGROUND_P, MUTATION_P, 1);
        if (backgroundLines.size()!=1) {
            System.err.println("There should be one background section for a gene");
        }
        
        System.out.println("##  Background");
        
        int s = backgroundLines.get(0)[0];
        int e = backgroundLines.get(0)[1];
        StringBuilder sb = new StringBuilder();
        for (int i=s+1; i<e; i++) {
            if (!lines.get(i).startsWith("cBioPortal link:") &&
                    !lines.get(i).startsWith("COSMIC link:") &&
                    !lines.get(i).startsWith("Mutations")) {
                sb.append(lines.get(i)).append("\n");
            }
        }
        String bg = sb.toString();
        
        EvidenceBlob eb = new EvidenceBlob();
        eb.setEvidenceType(EvidenceType.GENE_BACKGROUND);
        eb.setGene(gene);
        eb.setDescription(bg);
        
        Set<Article> docs = extractArticles(bg);
        Evidence evidence = new Evidence();
        evidence.setArticles(docs);
        evidence.setEvidenceBlob(eb);
        eb.setEvidences(Collections.singleton(evidence));
        
        EvidenceBlobBo evidenceBlobBo = ApplicationContextSingleton.getEvidenceBlobBo();
        evidenceBlobBo.save(eb);
        
        return backgroundLines.get(0);
    }
    
    private static void parseMutations(Gene gene, List<String> lines, int start, int end) {
        List<int[]> mutationLines = extractLines(lines, start, end, MUTATION_P, MUTATION_P, -1);
        for (int[] ixMutationLines : mutationLines) {
            int startMutation = ixMutationLines[0];
            int endMutation = ixMutationLines[1];
            parseMutation(gene, lines, startMutation, endMutation);
        }
        
    }
    
    private static void parseMutation(Gene gene, List<String> lines, int start, int end) {
        // mutation
        Pattern p = Pattern.compile(MUTATION_P);
        Matcher m = p.matcher(lines.get(start));
        if (!m.matches()) {
            System.err.println("wrong format of mutation line: "+lines.get(0));
        }
        
        String mutation = m.group(1).trim();
        
        System.out.println("##  Mutation: "+mutation);
        
        AlterationBo alterationBo = ApplicationContextSingleton.getAlterationBo();
        AlterationType type = AlterationType.MUTATION; //TODO: cna and fution
        Alteration alteration = alterationBo.findAlteration(gene, type, mutation);
        if (alteration==null) {
            alteration = new Alteration(gene, mutation, type);
            alterationBo.save(alteration);
        }
        
        // mutation effect
        String mutationEffectStr = lines.get(start+1);
        
        p = Pattern.compile(MUTATION_EFFECT_P);
        m = p.matcher(mutationEffectStr);
        if (!m.matches()) {
            System.err.println("wrong format of mutation effect line: "+mutationEffectStr);
        } else if (m.groupCount()>0) {
            System.out.println("##    Effect");
            
            String effect = m.group(1).trim();
            Set<Article> docs = extractArticles(mutationEffectStr);

            // Description of mutation effect
            List<int[]> mutationEffectDescLine = extractLines(lines, start+2, end, MUTATION_EFFECT_DESCRIPTION_P, TUMOR_TYPE_P, 1);
            String descMutationEffectStr = joinLines(lines, mutationEffectDescLine.get(0)[0]+1, mutationEffectDescLine.get(0)[1]);

            EvidenceBlob eb = new EvidenceBlob();
            eb.setEvidenceType(EvidenceType.MUTATION_EFFECT);
            eb.setAlteration(alteration);
            eb.setGene(gene);
            eb.setDescription(descMutationEffectStr);

            Evidence evidence = new Evidence();
            evidence.setKnownEffect(effect);
            evidence.setArticles(docs);
            evidence.setEvidenceBlob(eb);
            eb.setEvidences(Collections.singleton(evidence));

            EvidenceBlobBo evidenceBlobBo = ApplicationContextSingleton.getEvidenceBlobBo();
            evidenceBlobBo.save(eb);
        }
        
        // cancers
        List<int[]> cancerLines = extractLines(lines, start+1, end, TUMOR_TYPE_P, TUMOR_TYPE_P, -1);
        for (int[] ixcancerLines : cancerLines) {
            int startCancer = ixcancerLines[0];
            int endCancer = ixcancerLines[1];
            parseCancer(alteration, lines, startCancer, endCancer);
        }
    }
    
    private static void parseCancer(Alteration alteration, List<String> lines, int start, int end) {
        String line = lines.get(start);
        Pattern p = Pattern.compile(TUMOR_TYPE_P);
        Matcher m = p.matcher(line);
        if (!m.matches()) {
            System.err.println("wrong format of type type line: "+line);
        }
        String cancer = m.group(1);
        
        System.out.println("##    Cancer type: " + cancer);
        
        TumorTypeBo tumorTypeBo = ApplicationContextSingleton.getTumorTypeBo();
        TumorType tumorType = tumorTypeBo.findTumorTypeByName(cancer);
        if (tumorType==null) {
            tumorType = new TumorType(cancer, cancer, cancer);
            tumorTypeBo.save(tumorType);
        }
        
        EvidenceBlobBo evidenceBlobBo = ApplicationContextSingleton.getEvidenceBlobBo();
        
        // Prevalance
        List<int[]> prevalenceLines = extractLines(lines, start+1, end, PREVALENCE_P, CANCER_HEADERS_P, 1);
        if (!prevalenceLines.isEmpty()) {
            System.out.println("##      Prevalance");
            String prevalenceTxt = joinLines(lines, prevalenceLines.get(0)[0]+1, prevalenceLines.get(0)[1]).trim();
            if (!prevalenceTxt.isEmpty()) {
                Set<Article> prevalenceDocs = extractArticles(prevalenceTxt);

                EvidenceBlob eb = new EvidenceBlob();
                eb.setEvidenceType(EvidenceType.PREVALENCE);
                eb.setAlteration(alteration);
                eb.setGene(alteration.getGene());
                eb.setTumorType(tumorType);
                eb.setDescription(prevalenceTxt);

                Evidence evidence = new Evidence();
                evidence.setArticles(prevalenceDocs);
                evidence.setEvidenceBlob(eb);
                eb.setEvidences(Collections.singleton(evidence));

                evidenceBlobBo.save(eb);
            }
        } else {
            System.out.println("##      No Prevalance");
        }
        
        // Prognostic implications
        List<int[]> prognosticLines = extractLines(lines, start+1, end, PROGNOSTIC_IMPLICATIONS_P, CANCER_HEADERS_P, 1);
        if (!prognosticLines.isEmpty()) {
            System.out.println("##      Proganostic implications");
            String prognosticTxt = joinLines(lines, prognosticLines.get(0)[0]+1, prognosticLines.get(0)[1]).trim();
            if (!prognosticTxt.isEmpty()) {
                Set<Article> prognosticDocs = extractArticles(prognosticTxt);

                EvidenceBlob eb = new EvidenceBlob();
                eb.setEvidenceType(EvidenceType.PROGNOSTIC_IMPLICATION);
                eb.setAlteration(alteration);
                eb.setGene(alteration.getGene());
                eb.setTumorType(tumorType);
                eb.setDescription(prognosticTxt);

                Evidence evidence = new Evidence();
                evidence.setArticles(prognosticDocs);
                evidence.setEvidenceBlob(eb);
                eb.setEvidences(Collections.singleton(evidence));

                evidenceBlobBo.save(eb);
            }
        } else {
            System.out.println("##      No Proganostic implications");
        }
        
        // standard therapeutic implications of drug sensitivity
        List<int[]> standardSensitivityLines = extractLines(lines, start+1, end, STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY_P, CANCER_HEADERS_P, 1);
        if (!standardSensitivityLines.isEmpty()) {
            parseTherapeuticImplcations(alteration, tumorType, lines, standardSensitivityLines.get(0)[0], standardSensitivityLines.get(0)[1],
                    EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS, "Sensitive",
                    STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVE_TO_P, STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVE_EVIDENCE_P);
        } else {
            System.out.println("##      No "+STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVE_TO_P);
        }
        
        // standard therapeutic implications of drug resistance
        List<int[]> standardResistanceLines = extractLines(lines, start+1, end, STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE_P, CANCER_HEADERS_P, 1);
        if (!standardResistanceLines.isEmpty()) {
            parseTherapeuticImplcations(alteration, tumorType, lines, standardResistanceLines.get(0)[0], standardResistanceLines.get(0)[1],
                    EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS, "Resistant",
                    STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANT_TO_P, STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANT_EVIDENCE_P); 
        } else {
            System.out.println("##      No "+STANDARD_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANT_TO_P);
        }
        
        // NCCN
        List<int[]> nccnLines = extractLines(lines, start+1, end, NCCN_GUIDELINES_P, CANCER_HEADERS_P, 1);
        if (!standardResistanceLines.isEmpty()) {
            System.out.println("##      NCCN");
            parseNCCN(alteration, tumorType, lines, nccnLines.get(0)[0], nccnLines.get(0)[1]);
        } else {
            System.out.println("##      No NCCN");
        }
        
        
        // Investigational therapeutic implications of drug sensitivity
        List<int[]> investigationalSensitivityLines = extractLines(lines, start+1, end, INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY_P, CANCER_HEADERS_P, 1);
        if (!investigationalSensitivityLines.isEmpty()) {
            parseTherapeuticImplcations(alteration, tumorType, lines, investigationalSensitivityLines.get(0)[0], investigationalSensitivityLines.get(0)[1],
                EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS, "Sensitive",
                INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVE_TO_P, INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVE_EVIDENCE_P);
        } else {
            System.out.println("##      No "+INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVE_TO_P);
        }
        
        // Investigational therapeutic implications of drug resistance
        List<int[]> investigationalResistanceLines = extractLines(lines, start+1, end, INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE_P, CANCER_HEADERS_P, 1);
        if (!investigationalResistanceLines.isEmpty()) {
            parseTherapeuticImplcations(alteration, tumorType, lines, investigationalResistanceLines.get(0)[0], investigationalResistanceLines.get(0)[1],
                EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS, "Resistant",
                INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANT_TO_P, INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANT_EVIDENCE_P);
        } else {
            System.out.println("##      No "+INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANT_TO_P);
        }
        
        List<int[]> clinicalTrialsLines = extractLines(lines, start+1, end, ONGOING_CLINICAL_TRIALS_P, CANCER_HEADERS_P, 1);
        if (!clinicalTrialsLines.isEmpty()) {
            System.out.println("##      Clincial trials");
            parseClinicalTrials(alteration, tumorType, lines, clinicalTrialsLines.get(0)[0], clinicalTrialsLines.get(0)[1]);
        } else {
            System.out.println("##      No Clincial trials");
        }
    }
    
    private static void parseClinicalTrials(Alteration alteration, TumorType tumorType, List<String> lines, int start, int end) {
        EvidenceBlob eb = new EvidenceBlob();
        eb.setEvidenceType(EvidenceType.CLINICAL_TRIAL);
        eb.setAlteration(alteration);
        eb.setGene(alteration.getGene());
        eb.setTumorType(tumorType);
        Set<Evidence> evidences = new HashSet<Evidence>();
        eb.setEvidences(evidences);
        ClinicalTrialBo clinicalTrialBo = ApplicationContextSingleton.getClinicalTrialBo();
        Pattern p = Pattern.compile(CLINICAL_TRIALS_P);
        for (int i=start; i<end; i++) {
            Matcher m = p.matcher(lines.get(i));
            if (m.find()) {
                String nctId = m.group(1);
                ClinicalTrial ct = clinicalTrialBo.findClinicalTrialByPmid(nctId);
                if (ct==null) {
                    ct = new ClinicalTrial(nctId);
                    clinicalTrialBo.save(ct);
                }
                Evidence ev = new Evidence();
                ev.setEvidenceBlob(eb);
                ev.setClinicalTrials(Collections.singleton(ct));
            }
        }
        
        EvidenceBlobBo evidenceBlobBo = ApplicationContextSingleton.getEvidenceBlobBo();
        evidenceBlobBo.save(eb);
    }
    
    private static void parseTherapeuticImplcations(Alteration alteration, TumorType tumorType, List<String> lines, int start, int end,
            EvidenceType evidenceType, String knownEffectOfEvidence, String sensitivieP, String evidenceP) {
        System.out.println("##      "+sensitivieP);
        
        List<int[]> descLines = extractLines(lines, start+1, end, evidenceP, 1);
        String desc = joinLines(lines, descLines.get(0)[0]+1, descLines.get(0)[1]).trim();
        if (desc.isEmpty()) {
            return;
        }
        
        EvidenceBlob eb = new EvidenceBlob();
        eb.setEvidenceType(evidenceType);
        eb.setAlteration(alteration);
        eb.setGene(alteration.getGene());
        eb.setTumorType(tumorType);
        eb.setDescription(desc);
        Set<Evidence> evidences = new HashSet<Evidence>();
        eb.setEvidences(evidences);
        
        // sensitive to
        List<int[]> sensitiveLines = extractLines(lines, start+1, end, sensitivieP, evidenceP, 1);
        String sensitiveTxt = joinLines(lines, sensitiveLines.get(0)[0],sensitiveLines.get(0)[1]);
        sensitiveTxt = sensitiveTxt.substring(sensitiveTxt.indexOf(":")+1).trim();
        if (!sensitiveTxt.isEmpty()) {
            String[] drugsTxt = sensitiveTxt.split("\n");
            for (String drugTxt : drugsTxt) {
                Evidence ev = new Evidence();
                ev.setEvidenceBlob(eb);
                ev.setKnownEffect(knownEffectOfEvidence);
                parseDrugEvidence(drugTxt, ev);
                evidences.add(ev);
            }
        }
        
        EvidenceBlobBo evidenceBlobBo = ApplicationContextSingleton.getEvidenceBlobBo();
        evidenceBlobBo.save(eb);
    }
    
    private static void parseNCCN(Alteration alteration, TumorType tumorType, List<String> lines, int start, int end) {
        // disease
        String txt = lines.get(start+1).trim();
        Pattern p = Pattern.compile(NCCN_DISEASE_P);
        Matcher m = p.matcher(txt);
        if (!m.matches()) {
            System.err.println("Problem with NCCN disease line: "+txt);
            return;
        }
        
        if (m.groupCount()==0) {
            return;
        }
        
        String disease = m.group(1);
        
        // version
        txt = lines.get(start+2).trim();
        p = Pattern.compile(NCCN_VERSION_P);
        m = p.matcher(txt);
        if (!m.matches()) {
            System.err.println("Problem with NCCN version line: "+txt);
        }
        
        String version = null;
        if (m.groupCount()>0) {
            version = m.group(1);
        }
        
        // pages
        txt = lines.get(start+3);
        p = Pattern.compile(NCCN_PAGES_P);
        m = p.matcher(txt);
        if (!m.matches()) {
            System.err.println("Problem with NCCN pages line: "+txt);
        }
        
        String pages = null;
        if (m.groupCount()>0) {
            pages = m.group(1);
        }
        
        EvidenceBlob eb = new EvidenceBlob();
        eb.setEvidenceType(EvidenceType.NCCN_GUIDELINES);
        eb.setAlteration(alteration);
        eb.setGene(alteration.getGene());
        eb.setTumorType(tumorType);
        
        NccnGuidelineBo nccnGuideLineBo = ApplicationContextSingleton.getNccnGuidelineBo();
        
        NccnGuideline nccnGuideline = nccnGuideLineBo.findNccnGuideline(disease, version, pages);
        if (nccnGuideline==null) {
            nccnGuideline = new NccnGuideline(disease, version, pages);
            nccnGuideLineBo.save(nccnGuideline);
        }

        Evidence evidence = new Evidence();
        evidence.setNccnGuidelines(Collections.singleton(nccnGuideline));
        evidence.setEvidenceBlob(eb);
        
        eb.setEvidences(Collections.singleton(evidence));
        
        EvidenceBlobBo evidenceBlobBo = ApplicationContextSingleton.getEvidenceBlobBo();
        evidenceBlobBo.save(eb);
    }
    
    private static void parseDrugEvidence(String txt, Evidence evidence) {
        Pattern p = Pattern.compile("([^\\(]+) ?(\\(.+\\))?");
        Matcher m = p.matcher(txt);
        if (!m.matches()) {
            System.err.println("Cannot process drug evidence: "+txt);
            return;
        }
        
        ArticleBo articleBo = ApplicationContextSingleton.getArticleBo();
        ClinicalTrialBo clinicalTrialBo = ApplicationContextSingleton.getClinicalTrialBo();
        DrugBo drugBo = ApplicationContextSingleton.getDrugBo();

        String drugName = m.group(1);
        Drug drug = drugBo.findDrugByName(drugName);
        if (drug==null) {
            drug = new Drug(drugName);
            drugBo.save(drug);
        }
        evidence.setDrugs(Collections.singleton(drug));
        
        if (m.groupCount()==2) {
            String[] parts = m.group(2).replaceAll("[\\(\\)]", "").split("; *");
            LevelOfEvidence loe = LevelOfEvidence.getByLevel(parts[0]);
            evidence.setLevelOfEvidence(loe); // note that it could be null
            Set<Article> docs = new HashSet<Article>();
            evidence.setArticles(docs);
            Set<ClinicalTrial> clinicalTrials = new HashSet<ClinicalTrial>();
            evidence.setClinicalTrials(clinicalTrials);
            for (String part : parts) {
                if (part.startsWith("PMID")) {
                    String[] pmids = part.substring(part.indexOf(":")+1).trim().split(", *");
                    for (String pmid : pmids) {
                        Article doc = articleBo.findArticleByPmid(pmid);
                        if (doc==null) {
                            doc = NcbiEUtils.readPubmedArticle(pmid);
                            articleBo.save(doc);
                        }
                        docs.add(doc);
                    }
                } else if (part.startsWith("NCT")) {
                    // support NCT numbers
                    String[] nctIds = part.split(", *");
                    for (String nctId : nctIds) {
                        ClinicalTrial ct = clinicalTrialBo.findClinicalTrialByPmid(nctId);
                        if (ct==null) {
                            ct = new ClinicalTrial(nctId);
                            clinicalTrialBo.save(ct);
                        }
                        clinicalTrials.add(ct);
                    }
                }
            }
        }
    }
    
    private static List<int[]> extractLines(List<String> lines, int start, int end, String startLinePatten, int limit) {
        return extractLines(lines, start, end, startLinePatten, (String[])null, limit);
    }
    
    private static List<int[]> extractLines(List<String> lines, int start, int end, String startLinePatten, String endLinePattern, int limit) {
        return extractLines(lines, start, end, startLinePatten, new String[]{endLinePattern}, limit);
    }
    
    private static List<int[]> extractLines(List<String> lines, int start, int end, String startLinePatten, String[] endLinePatterns, int limit) {
        List<int[]> indices = new ArrayList<int[]>();

        int s=start, e=start;
        
        while (s<end && e<end) {
            // find start line
            s = e;
            while (s<end && !lines.get(s).matches(startLinePatten)) {
                s++;
            }

            // find end line
            e = endLinePatterns==null ? end : (s + 1);
            while (e<end && !matchAnyPattern(lines.get(e), endLinePatterns)) {
                e++;
            }

            if (s<end) {
                indices.add(new int[]{s,e});
            }
            
            if (limit>0 && indices.size()>=limit) {
                break;
            }
        }
        
        return indices;
    }
    
    private static boolean matchAnyPattern(String s, String[] patterns) {
        for (String p : patterns) {
            if (s.matches(p)) {
                return true;
            }
        }
        return false;
    }
    
    private static String joinLines(List<String> lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i=start; i<end; i++) {
            sb.append(lines.get(i)).append("\n");
        }
        return sb.toString();
    }
    
    private static Set<Article> extractArticles(String str) {
        Set<Article> docs = new HashSet<Article>();
        ArticleBo articleBo = ApplicationContextSingleton.getArticleBo();
        Pattern pmidPattern = Pattern.compile("\\(PMIDs?:([^\\)]+)\\)");
        Matcher m = pmidPattern.matcher(str);
        if (m.matches()) {
            int n = m.groupCount();
            for (int i=1; i<=n; i++) {
                String pmids = m.group(i).trim();
                for (String pmid : pmids.split(", *")) {
                    Article doc = articleBo.findArticleByPmid(pmid);
                    if (doc==null) {
                        doc = NcbiEUtils.readPubmedArticle(pmid);
                        articleBo.save(doc);
                    }
                    docs.add(doc);
                }
            }
        }
        return docs;
    }
}
