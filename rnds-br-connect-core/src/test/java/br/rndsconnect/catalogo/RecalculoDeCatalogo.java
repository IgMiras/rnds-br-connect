package br.rndsconnect.catalogo;

import ca.uhn.fhir.context.FhirContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ValueSet;

/**
 * Recalcula, dos artefatos de um pacote de conformidade, a lista dos códigos que um ValueSet
 * aceita. É a conta que os scripts geradores fazem para escrever um catálogo, refeita em Java
 * para que um teste de portão a compare com o arquivo distribuído.
 *
 * <p>A conta, para cada {@code compose.include} do ValueSet:
 *
 * <ol>
 *   <li>se o include enumera conceitos, valem os enumerados;
 *   <li>se não enumera, vale o CodeSystem inteiro, percorrido recursivamente;
 *   <li>um código repetido entra uma vez, com o display da primeira ocorrência;
 *   <li>saem os códigos do {@code compose.exclude} do mesmo CodeSystem.
 * </ol>
 *
 * <p>Um teste de portão por catálogo é o que impede o arquivo gerado de envelhecer calado: se
 * o pacote mudar e o catálogo não for gerado de novo, a suíte reprova.
 *
 * @author Igor Mendes Domingues Miras
 */
final class RecalculoDeCatalogo {

    private final String pacote;
    private final ValueSet valueSet;
    private final Map<String, CodeSystem> codeSystems;

    private RecalculoDeCatalogo(String pacote, ValueSet valueSet, Map<String, CodeSystem> codeSystems) {
        this.pacote = pacote;
        this.valueSet = valueSet;
        this.codeSystems = codeSystems;
    }

    /**
     * Lê do pacote o ValueSet e os CodeSystems de que ele pode precisar.
     *
     * @param ctx contexto FHIR R4
     * @param pacote diretório do pacote no classpath, como {@code conformance/rac}
     * @param urlDoValueSet URL canônica do ValueSet do vínculo
     * @param crivo texto que aparece no início dos arquivos que interessam, como
     *     {@code BRTipoObservacao}; os demais nem são lidos por inteiro
     * @return o recálculo pronto
     * @throws IllegalStateException se o ValueSet não estiver no pacote
     */
    static RecalculoDeCatalogo doPacote(FhirContext ctx, String pacote, String urlDoValueSet, String crivo) {
        ValueSet valueSet = null;
        Map<String, CodeSystem> codeSystems = new LinkedHashMap<>();
        for (String linha : ler(pacote + "/index.txt").split("\\R")) {
            String rel = linha.trim();
            if (rel.startsWith("#") || !rel.toLowerCase().endsWith(".xml")) {
                continue;
            }
            // Crivo de 4 KiB antes de ler o arquivo inteiro: os pacotes têm CodeSystem cadastral
            // de dezenas de MB, e nenhum deles interessa a um catálogo.
            if (!amostra(pacote + "/" + rel).contains(crivo)) {
                continue;
            }
            IBaseResource r = ctx.newXmlParser().parseResource(ler(pacote + "/" + rel));
            if (r instanceof ValueSet v && urlDoValueSet.equals(v.getUrl())) {
                valueSet = v;
            } else if (r instanceof CodeSystem c && c.hasUrl()) {
                codeSystems.put(c.getUrl(), c);
            }
        }
        if (valueSet == null) {
            throw new IllegalStateException("ValueSet " + urlDoValueSet + " ausente no pacote " + pacote + " (crivo \""
                    + crivo + "\"). O pacote mudou, ou o crivo não aparece no início do arquivo.");
        }
        return new RecalculoDeCatalogo(pacote, valueSet, codeSystems);
    }

    /** @return o ValueSet do vínculo, como está no pacote */
    ValueSet valueSet() {
        return valueSet;
    }

    /**
     * @return URL do CodeSystem → (código → display), como o gerador o monta
     * @throws IllegalStateException se o ValueSet incluir por inteiro um CodeSystem que o
     *     pacote não traz
     */
    Map<String, Map<String, String>> codigosAceitos() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (ValueSet.ConceptSetComponent inc : valueSet.getCompose().getInclude()) {
            Map<String, String> grupo = out.computeIfAbsent(inc.getSystem(), k -> new LinkedHashMap<>());
            if (!inc.getConcept().isEmpty()) {
                for (ValueSet.ConceptReferenceComponent c : inc.getConcept()) {
                    grupo.putIfAbsent(c.getCode(), limpo(c.getDisplay()));
                }
            } else {
                CodeSystem cs = codeSystems.get(inc.getSystem());
                if (cs == null) {
                    throw new IllegalStateException("O ValueSet " + valueSet.getUrl() + " inclui por inteiro o "
                            + inc.getSystem() + ", que não foi encontrado no pacote " + pacote + ".");
                }
                for (CodeSystem.ConceptDefinitionComponent c : planos(cs.getConcept())) {
                    grupo.putIfAbsent(c.getCode(), limpo(c.getDisplay()));
                }
            }
            for (ValueSet.ConceptSetComponent exc : valueSet.getCompose().getExclude()) {
                if (inc.getSystem().equals(exc.getSystem())) {
                    exc.getConcept().forEach(c -> grupo.remove(c.getCode()));
                }
            }
        }
        return out;
    }

    /** O CodeSystem pode aninhar conceito; o gerador percorre recursivamente. */
    private static List<CodeSystem.ConceptDefinitionComponent> planos(List<CodeSystem.ConceptDefinitionComponent> cs) {
        List<CodeSystem.ConceptDefinitionComponent> out = new ArrayList<>();
        for (CodeSystem.ConceptDefinitionComponent c : cs) {
            out.add(c);
            out.addAll(planos(c.getConcept()));
        }
        return out;
    }

    /** O gerador troca aspas duplas por simples para não quebrar o YAML. */
    private static String limpo(String s) {
        return s == null ? "" : s.trim().replace("\"", "'");
    }

    /** Lê no máximo 4 KiB — o crivo tem de ser barato de verdade, não só parecer. */
    private static String amostra(String caminho) {
        try (InputStream in = RecalculoDeCatalogo.class.getClassLoader().getResourceAsStream(caminho)) {
            if (in == null) {
                return "";
            }
            byte[] buf = new byte[4096];
            int lidos = in.readNBytes(buf, 0, buf.length);
            return new String(buf, 0, lidos, StandardCharsets.UTF_8).replace("﻿", "");
        } catch (IOException e) {
            return "";
        }
    }

    private static String ler(String caminho) {
        try (InputStream in = RecalculoDeCatalogo.class.getClassLoader().getResourceAsStream(caminho)) {
            if (in == null) {
                return "";
            }
            // A marca de ordem de bytes aparece em quatro artefatos do REDFM; aqui é barata.
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("﻿", "");
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + caminho, e);
        }
    }
}
