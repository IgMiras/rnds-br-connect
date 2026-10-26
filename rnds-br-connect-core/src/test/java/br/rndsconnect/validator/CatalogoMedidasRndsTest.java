package br.rndsconnect.validator;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * O catálogo das medidas observadas não pode divergir dos artefatos.
 *
 * <p>O {@code catalogo/medidas-rnds.yml} é gerado por
 * {@code scripts/gerar-catalogo-medidas.py} a partir do pacote de conformidade. Um arquivo
 * gerado e versionado envelhece calado: se o DATASUS publicar outra versão do
 * {@code BRTipoObservacao-1.0} e alguém trocar o pacote, o catálogo continua afirmando o
 * número antigo e ninguém fica sabendo.
 *
 * <p>Daí este teste, que é o mecanismo do portão da Arquitetura §12.3 aplicado a um recurso:
 * <strong>recomputa o catálogo direto dos artefatos e exige igualdade com o YAML</strong>.
 * Código novo no ValueSet reprova; código que saiu reprova também, e obriga a regerar.
 *
 * <p><strong>O catálogo não é tabela de equivalência clínica (ADR-05).</strong> Ele responde
 * «este código é aceito?», não «qual código corresponde ao que eu medi» — e a segunda pergunta
 * permanece com a equipe de saúde.
 *
 * @author Igor Mendes Domingues Miras
 */
class CatalogoMedidasRndsTest {

    private static final String PACOTE = "conformance/rac";
    private static final String CATALOGO = "catalogo/medidas-rnds.yml";
    private static final String VS = "http://www.saude.gov.br/fhir/r4/ValueSet/BRTipoObservacao-1.0";

    private static FhirContext ctx;
    private static ValueSet valueSet;
    private static CodeSystem codeSystem;

    @BeforeAll
    static void carregar() {
        ctx = FhirContext.forR4();
        for (String rel : indice()) {
            if (!rel.toLowerCase().endsWith(".xml")) {
                continue;
            }
            // Crivo de 4 KiB antes de ler o arquivo inteiro: o pacote tem CodeSystem cadastral
            // de dezenas de MB, e nenhum deles interessa aqui.
            if (!amostra(rel).contains("BRTipoObservacao")) {
                continue;
            }
            IBaseResource r = ctx.newXmlParser().parseResource(conteudo(rel));
            if (r instanceof ValueSet v && VS.equals(v.getUrl())) {
                valueSet = v;
            } else if (r instanceof CodeSystem c && c.hasUrl() && c.getUrl().endsWith("/BRTipoObservacao")) {
                codeSystem = c;
            }
        }
        assertThat(valueSet)
                .as("ValueSet BRTipoObservacao-1.0 no pacote do RAC")
                .isNotNull();
        assertThat(codeSystem)
                .as("CodeSystem BRTipoObservacao no pacote do RAC")
                .isNotNull();
    }

    // ══ O portão ════════════════════════════════════════════════════════

    @Test
    @DisplayName("GATE: o catálogo das medidas é exatamente o que os artefatos declaram")
    void catalogoNaoDivergeDosArtefatos() {
        Map<String, Map<String, String>> medido = recomputar();
        Map<String, Map<String, String>> declarado = doCatalogo();

        assertThat(declarado)
                .as(
                        "o YAML gerado contra o pacote %s — regerar com "
                                + "scripts/gerar-catalogo-medidas.py se os artefatos mudaram",
                        PACOTE)
                .isEqualTo(medido);

        int soma = medido.values().stream().mapToInt(Map::size).sum();
        assertThat(soma).as("códigos aceitos nas medidas observadas").isEqualTo(23);
        assertThat(((Number) raiz().get("totalDeclarado")).intValue())
                .as("o totalDeclarado do YAML")
                .isEqualTo(soma);
    }

    @Test
    @DisplayName("O catálogo é do elemento certo: Observation.code do BRMedidaObservada")
    void oCatalogoEhDoBindingDaMedida() {
        // Sem isto, o catálogo poderia estar correto e ser de outra coisa. O binding é
        // `required`, de modo que código fora do ValueSet reprova na Camada 4.
        assertThat(valueSet.getUrl()).isEqualTo(VS);
        assertThat(valueSet.getCompose().getInclude())
                .as("as três fontes que o ValueSet compõe")
                .hasSize(3);
    }

    // ══ Os dois achados que o catálogo registra ═════════════════════════

    @Test
    @DisplayName("A mesma medida tem duas grafias oficiais no mesmo ValueSet")
    void cincoParesDeSinonimo() {
        Map<String, String> todos = new LinkedHashMap<>();
        recomputar().values().forEach(todos::putAll);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sinonimos = (List<Map<String, Object>>) raiz().get("sinonimos");
        assertThat(sinonimos).as("pares declarados no catálogo").hasSize(5);

        for (Map<String, Object> par : sinonimos) {
            @SuppressWarnings("unchecked")
            List<String> codigos = (List<String>) par.get("codigos");
            assertThat(codigos)
                    .as("os códigos do par %s existem no ValueSet", par.get("medida"))
                    .allSatisfy(c -> assertThat(todos).containsKey(c));
        }
    }

    @Test
    @DisplayName("Quatro códigos do ValueSet não são medida de valor, e passam no binding")
    void quatroCodigosNaoSaoMedida() {
        Map<String, String> todos = new LinkedHashMap<>();
        recomputar().values().forEach(todos::putAll);

        @SuppressWarnings("unchecked")
        List<String> naoMedida = (List<String>) raiz().get("naoSaoMedida");
        assertThat(naoMedida).containsExactly("DSIA", "RECIDI", "DF", "IAC");
        assertThat(naoMedida)
                .as("são do CodeSystem da RNDS, e o binding os aceita como medida")
                .allSatisfy(c -> assertThat(todos).containsKey(c));
    }

    // ══ Recomputar dos artefatos ════════════════════════════════════════

    /** @return sistema → (código → display), como o gerador o monta */
    private static Map<String, Map<String, String>> recomputar() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (ValueSet.ConceptSetComponent inc : valueSet.getCompose().getInclude()) {
            Map<String, String> grupo = new LinkedHashMap<>();
            if (!inc.getConcept().isEmpty()) {
                for (ValueSet.ConceptReferenceComponent c : inc.getConcept()) {
                    grupo.putIfAbsent(c.getCode(), limpo(c.getDisplay()));
                }
            } else {
                for (CodeSystem.ConceptDefinitionComponent c : planos(codeSystem.getConcept())) {
                    grupo.putIfAbsent(c.getCode(), limpo(c.getDisplay()));
                }
            }
            for (ValueSet.ConceptSetComponent exc : valueSet.getCompose().getExclude()) {
                if (inc.getSystem().equals(exc.getSystem())) {
                    exc.getConcept().forEach(c -> grupo.remove(c.getCode()));
                }
            }
            out.put(inc.getSystem(), grupo);
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

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, String>> doCatalogo() {
        return (Map<String, Map<String, String>>) raiz().get("codigos");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> raiz() {
        try (InputStream in = CatalogoMedidasRndsTest.class.getClassLoader().getResourceAsStream(CATALOGO)) {
            if (in == null) {
                throw new IllegalStateException(CATALOGO + " ausente. Gere com scripts/gerar-catalogo-medidas.py");
            }
            return (Map<String, Object>) new Yaml().load(in);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Falha ao ler " + CATALOGO, e);
        }
    }

    // ══ Leitura dos artefatos pelo índice do pacote ═════════════════════

    private static List<String> indice() {
        return List.of(ler(PACOTE + "/index.txt").split("\\R"));
    }

    /** Lê no máximo 4 KiB — o crivo tem de ser barato de verdade, não só parecer. */
    private static String amostra(String rel) {
        try (InputStream in =
                CatalogoMedidasRndsTest.class.getClassLoader().getResourceAsStream(PACOTE + "/" + rel.trim())) {
            if (in == null) {
                return "";
            }
            byte[] buf = new byte[4096];
            int lidos = in.readNBytes(buf, 0, buf.length);
            return new String(buf, 0, lidos, StandardCharsets.UTF_8).replace("\uFEFF", "");
        } catch (java.io.IOException e) {
            return "";
        }
    }

    private static String conteudo(String rel) {
        return ler(PACOTE + "/" + rel.trim());
    }

    private static String ler(String caminho) {
        try (InputStream in = CatalogoMedidasRndsTest.class.getClassLoader().getResourceAsStream(caminho)) {
            if (in == null) {
                return "";
            }
            // A marca de ordem de bytes aparece em quatro artefatos do REDFM; aqui é barata.
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("﻿", "");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Falha ao ler " + caminho, e);
        }
    }
}
