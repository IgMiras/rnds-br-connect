package br.rndsconnect.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.model.CodedTarget;
import br.rndsconnect.model.TerminologyDomain;
import br.rndsconnect.semantic.MappingSource;
import br.rndsconnect.semantic.YamlMappingSource;
import ca.uhn.fhir.context.FhirContext;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>A segunda metade da classe exercita o que o catálogo de medidas ganhou ao ficar no mesmo
 * pacote do de exames: a classe de carga e a conferência da tabela da instituição.
 *
 * @author Igor Mendes Domingues Miras
 */
class CatalogoMedidasRndsTest {

    private static final String PACOTE = "conformance/rac";
    private static final String CATALOGO = CatalogoCodigosAceitos.RECURSO_MEDIDAS;
    private static final String VS = "http://www.saude.gov.br/fhir/r4/ValueSet/BRTipoObservacao-1.0";

    private static final String CS = "http://www.saude.gov.br/fhir/r4/CodeSystem/";
    private static final String SYSTEM_TIPO_OBSERVACAO = CS + "BRTipoObservacao";
    private static final String SYSTEM_TABELA_SUS = CS + "BRTabelaSUS";
    /** O LOINC como o ValueSet o declara, com a barra final. */
    private static final String SYSTEM_LOINC = "https://loinc.org/";

    private static RecalculoDeCatalogo artefatos;
    private static CatalogoCodigosAceitos catalogo;

    @BeforeAll
    static void carregar() {
        artefatos = RecalculoDeCatalogo.doPacote(FhirContext.forR4(), PACOTE, VS, "BRTipoObservacao");
        catalogo = CatalogoCodigosAceitos.medidas();
    }

    // ══ O portão ════════════════════════════════════════════════════════

    @Test
    @DisplayName("GATE: o catálogo das medidas é exatamente o que os artefatos declaram")
    void catalogoNaoDivergeDosArtefatos() {
        Map<String, Map<String, String>> medido = artefatos.codigosAceitos();
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
        assertThat(artefatos.valueSet().getUrl()).isEqualTo(VS);
        assertThat(artefatos.valueSet().getCompose().getInclude())
                .as("as três fontes que o ValueSet compõe")
                .hasSize(3);
    }

    // ══ Os dois achados que o catálogo registra ═════════════════════════

    @Test
    @DisplayName("A mesma medida tem duas grafias oficiais no mesmo ValueSet")
    void cincoParesDeSinonimo() {
        Map<String, String> todos = new LinkedHashMap<>();
        artefatos.codigosAceitos().values().forEach(todos::putAll);

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
        artefatos.codigosAceitos().values().forEach(todos::putAll);

        @SuppressWarnings("unchecked")
        List<String> naoMedida = (List<String>) raiz().get("naoSaoMedida");
        assertThat(naoMedida).containsExactly("DSIA", "RECIDI", "DF", "IAC");
        assertThat(naoMedida)
                .as("são do CodeSystem da RNDS, e o binding os aceita como medida")
                .allSatisfy(c -> assertThat(todos).containsKey(c));
    }

    // ══ A carga pela classe comum ═══════════════════════════════════════

    @Test
    @DisplayName("A classe de carga entrega os 23 códigos, das três fontes, iguais aos dos artefatos")
    void aClasseDeCargaLeOCatalogoDeMedidas() {
        assertThat(catalogo.total()).isEqualTo(23);
        assertThat(catalogo.systems())
                .containsExactlyInAnyOrder(SYSTEM_TIPO_OBSERVACAO, SYSTEM_TABELA_SUS, SYSTEM_LOINC);
        assertThat(catalogo.totalDe(SYSTEM_TIPO_OBSERVACAO)).isEqualTo(9);
        assertThat(catalogo.totalDe(SYSTEM_TABELA_SUS)).isEqualTo(1);
        assertThat(catalogo.totalDe(SYSTEM_LOINC)).isEqualTo(13);

        // As listas `sinonimos` e `naoSaoMedida` do arquivo não são códigos aceitos, e a
        // classe de carga não as conta: o que ela entrega é o que os artefatos declaram.
        assertThat(catalogo.codigos())
                .as("o que a classe de carga entrega, contra o recálculo do pacote %s", PACOTE)
                .isEqualTo(artefatos.codigosAceitos());
    }

    @Test
    @DisplayName("As duas grafias do peso são aceitas; a URI canônica do LOINC, não")
    void aceitaAsDuasGrafiasENaoAUriCanonica() {
        assertThat(catalogo.aceita(SYSTEM_LOINC, "29463-7")).isTrue();
        assertThat(catalogo.aceita(SYSTEM_TIPO_OBSERVACAO, "P")).isTrue();
        assertThat(catalogo.aceita("http://loinc.org", "29463-7"))
                .as("o ValueSet declara o LOINC com a barra final, e é essa a URL que o vínculo aceita")
                .isFalse();
    }

    // ══ Conferência da tabela do integrador ═════════════════════════════

    @Test
    @DisplayName("A tabela de exemplo de medidas, lida do YAML, passa na conferência")
    void tabelaDeExemploPassaNaConferencia() {
        MappingSource fonte =
                new YamlMappingSource(TerminologyDomain.TIPO_OBSERVACAO, "mapeamentos/tipos-observacao.yml");
        assertThat(new MapeamentoConformeCatalogo(catalogo).conferir(fonte)).isEmpty();
    }

    @Test
    @DisplayName("Medida fora do catálogo é apanhada na inicialização, com o domínio na mensagem")
    void medidaForaDoCatalogoEApanhadaNaInicializacao() {
        Map<String, CodedTarget> tabela = new LinkedHashMap<>();
        tabela.put("PESO", TabelaDeTeste.alvo(SYSTEM_LOINC, "29463-7"));
        // 2339-0 é a glicose no sangue: código LOINC verdadeiro, fora dos 13 enumerados.
        tabela.put("GLICEMIA", TabelaDeTeste.alvo(SYSTEM_LOINC, "2339-0"));
        tabela.put("PESO_URI_CANONICA", TabelaDeTeste.alvo("http://loinc.org", "29463-7"));
        MappingSource fonte = TabelaDeTeste.de(TerminologyDomain.TIPO_OBSERVACAO, tabela);

        MapeamentoConformeCatalogo conferencia = new MapeamentoConformeCatalogo(catalogo);

        assertThat(conferencia.conferir(fonte))
                .extracting(MapeamentoConformeCatalogo.Divergencia::codigoLocal)
                .containsExactly("GLICEMIA", "PESO_URI_CANONICA");
        assertThat(conferencia.conferir(fonte).get(0).motivo()).contains("não consta entre os 13 aceitos");
        assertThat(conferencia.conferir(fonte).get(1).motivo()).contains("CodeSystem fora do catálogo");

        assertThatThrownBy(() -> conferencia.exigirConformidade(fonte))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TIPO_OBSERVACAO")
                .hasMessageContaining("GLICEMIA")
                .hasMessageContaining("PESO_URI_CANONICA")
                .hasMessageContaining(CATALOGO);
    }

    // ══ Leitura direta do YAML, para o que a classe de carga não entrega ═

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
}
