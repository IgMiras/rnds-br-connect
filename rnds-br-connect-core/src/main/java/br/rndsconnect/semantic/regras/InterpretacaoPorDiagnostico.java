package br.rndsconnect.semantic.regras;

import br.rndsconnect.model.EnrichedRegistroExameLaboratorial;
import br.rndsconnect.model.EnrichedRegistroExameLaboratorial.ExameLaboratorial;
import br.rndsconnect.semantic.RegraValidacao;
import br.rndsconnect.semantic.RndsValidationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Elo 7 (A2) — RN1 do manual REL: a interpretação depende do exame realizado.
 *
 * <p>O manual enuncia, na seção 7.2:
 *
 * <blockquote>«Cada tipo de interpretação está condicionado ao tipo de diagnóstico
 * laboratorial realizado de acordo com o anexo tabela regra de negócio»</blockquote>
 *
 * <p>A tabela do Anexo associa cada código LOINC ao CodeSystem {@code BRTipoResultado*} de
 * onde o resultado qualitativo daquele exame deve vir. São três na prática: {@code DTNT}
 * (detectável / não detectável), {@code RGNR} (reagente / não reagente) e {@code PRAU}.
 *
 * <p><strong>O que a regra impede.</strong> Um RT-PCR cujo resultado venha do vocabulário
 * sorológico — «reagente» em vez de «detectável» — usa código válido, de CodeSystem válido,
 * e passa em qualquer validador FHIR: o binding de {@code Observation.value[x]} aceita os
 * nove CodeSystems que compõem o {@code BRResultadoQualitativoExame-2.0}, sem distinguir
 * qual deles combina com qual exame. Só a RN1 faz essa distinção, e ela vive na prosa do
 * manual.
 *
 * <h2>Silêncio do manual não é proibição</h2>
 *
 * <p>A tabela cobre <strong>160 dos 442</strong> códigos LOINC aceitos. Sobre os 282
 * restantes a RN1 nada diz, e este validador os <strong>aceita</strong>.
 *
 * <p>A escolha é deliberada e vale a pena explicitá-la: tratar ausência na tabela como
 * proibição transformaria uma regra de 160 linhas num catálogo fechado de 442, atribuindo ao
 * manual uma afirmação que ele não faz. O custo do erro é assimétrico — recusar um exame
 * legítimo bloqueia registro clínico; aceitar uma combinação sobre a qual o manual se cala
 * preserva o fluxo e deixa a decisão com quem tem competência para ela.
 *
 * <h2>Por que é A2</h2>
 *
 * <p>A tabela é indexada por códigos da RNDS — LOINC de um lado, {@code BRTipoResultado*} do
 * outro. Antes da tradução existem apenas os códigos locais do integrador, e não há como
 * consultá-la. Ver Seção 7.1 da Arquitetura.
 *
 * <h2>Opt-in</h2>
 *
 * <p>Não integra a cadeia padrão de {@link RegrasPadrao}, pelo mesmo motivo do elo 5: a
 * regra depende de tabela clínica, e o ADR-05 reserva ao integrador a decisão de adotá-la.
 * A tabela é distribuída com o framework porque é <strong>dado publicado pelo Ministério da
 * Saúde</strong>, não correspondência que o framework tenha inventado — o que a distingue de
 * uma tabela de equivalência de exames.
 *
 * @author Igor Mendes Domingues Miras
 */
public class InterpretacaoPorDiagnostico implements RegraValidacao<EnrichedRegistroExameLaboratorial> {

    /** Identificação da regra nas mensagens de erro. */
    public static final String REGRA = "ELO-7/RN1-INTERPRETACAO-POR-EXAME";

    /** Caminho padrão da tabela no classpath. */
    public static final String RECURSO_PADRAO = "regras/rn1-interpretacao-por-exame.yml";

    private static final String CS_LOINC = "http://www.saude.gov.br/fhir/r4/CodeSystem/BRNomeExameLOINC";

    private final Map<String, String> codeSystemEsperado;

    /** Carrega a tabela do caminho padrão. */
    public InterpretacaoPorDiagnostico() {
        this(RECURSO_PADRAO);
    }

    /**
     * @param recurso caminho da tabela no classpath
     */
    @SuppressWarnings("unchecked")
    public InterpretacaoPorDiagnostico(String recurso) {
        byte[] conteudo = lerBytes(recurso);
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object raiz = yaml.load(new ByteArrayInputStream(conteudo));
        if (!(raiz instanceof Map<?, ?> mapa) || !(mapa.get("codigos") instanceof Map<?, ?> codigos)) {
            throw new IllegalStateException("Tabela da RN1 malformada: " + recurso);
        }
        Map<String, String> tabela = new LinkedHashMap<>();
        codigos.forEach((k, v) -> tabela.put(String.valueOf(k), String.valueOf(v)));
        if (tabela.isEmpty()) {
            throw new IllegalStateException(
                    "Tabela da RN1 vazia: " + recurso + ". Regenere com " + "scripts/extrair-rn1-manual-rel.py.");
        }
        this.codeSystemEsperado = Collections.unmodifiableMap(tabela);
    }

    @Override
    public void validar(EnrichedRegistroExameLaboratorial dto) {
        if (dto.getExames() == null) {
            return;
        }
        for (int i = 0; i < dto.getExames().size(); i++) {
            validarExame(dto.getExames().get(i), i);
        }
    }

    private void validarExame(ExameLaboratorial e, int indice) {
        if (e.getNomeExame() == null || e.getResultadoQualitativo() == null) {
            return; // resultado quantitativo não tem interpretação a conferir aqui
        }
        if (!CS_LOINC.equals(e.getNomeExame().system())) {
            return; // a tabela da RN1 só se pronuncia sobre exames LOINC
        }

        String esperado = codeSystemEsperado.get(e.getNomeExame().code());
        if (esperado == null) {
            return; // silêncio do manual não é proibição — ver Javadoc da classe
        }

        String recebido = e.getResultadoQualitativo().system();
        if (!esperado.equals(recebido)) {
            throw new RndsValidationException(
                    "exames[" + indice + "].resultadoQualitativoLocal",
                    REGRA,
                    "o exame " + e.getNomeExame().code() + " exige resultado de "
                            + nomeCurto(esperado) + ", e veio de " + nomeCurto(recebido)
                            + ". Ambos são CodeSystems válidos do BRResultadoQualitativoExame-2.0, "
                            + "e por isso a Camada 4 aprova a troca — só a RN1 do manual REL a impede",
                    recebido);
        }
    }

    /** @return quantos códigos a tabela cobre */
    public int totalDeCodigos() {
        return codeSystemEsperado.size();
    }

    private static String nomeCurto(String system) {
        int barra = system.lastIndexOf('/');
        return barra >= 0 ? system.substring(barra + 1) : system;
    }

    private static byte[] lerBytes(String recurso) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        InputStream in = cl != null ? cl.getResourceAsStream(recurso) : null;
        if (in == null) {
            in = InterpretacaoPorDiagnostico.class.getClassLoader().getResourceAsStream(recurso);
        }
        if (in == null) {
            throw new IllegalStateException("Tabela da RN1 ausente no classpath: " + recurso);
        }
        try (InputStream stream = in) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler a tabela da RN1: " + recurso, e);
        }
    }
}
