package br.rndsconnect.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * DTO enriquecido do REL: o que a Camada 2 entrega à Camada 3 no serviço de resultado de
 * exame laboratorial.
 *
 * <p>É o primeiro dos três serviços com <strong>cardinalidade variável de resources</strong>.
 * O RIA-R produz dois ou três; o RIA-C, sempre dois. O REL produz
 * {@code 2 + 2 × (número de exames)}: uma {@code Composition}, uma {@code Condition} e, para
 * cada exame, um {@code Observation} com seu {@code Specimen}.
 *
 * <p>Cada exame carrega o próprio {@code Specimen} porque {@code Observation.specimen} é
 * {@code 1..1} no perfil — dois exames sobre a mesma coleta produzem dois resources
 * {@code Specimen} idênticos em conteúdo. É o que o perfil determina.
 *
 * @author Igor Mendes Domingues Miras
 */
@Value
@Builder
public class EnrichedRegistroExameLaboratorial {

    // ── Envelope ────────────────────────────────────────────────────────
    String idLocalRegistro;

    // ── Indivíduo — exatamente um dos dois ──────────────────────────────
    String cnsPaciente;
    String cpfPaciente;

    // ── Responsáveis ────────────────────────────────────────────────────
    /**
     * CNPJ do laboratório — vai para {@code Composition.author.identifier} e para
     * {@code Observation.performer:pessoaJuridicaProfissionalLiberal}.
     *
     * <p>Note a diferença em relação ao RIA: lá o autor do documento é o estabelecimento de
     * saúde (CNES); aqui é a pessoa jurídica que assina o laudo.
     */
    String cnpjLaboratorio;
    /** CNES — {@code Observation.performer:estabelecimentoSaude}, {@code 1..1}. */
    String cnesEstabelecimento;
    /** {@code Composition.date} — criação do documento no sistema de origem. */
    Instant dataRegistroOrigem;

    // ── Condition (BRCondicaoSaude) ─────────────────────────────────────
    /**
     * {@code Condition.code} — suspeita diagnóstica que motivou o exame.
     *
     * <p>O binding é ao <strong>ValueSet</strong> {@code BRTerminologiaSuspeitaDiagnostica},
     * que compõe o <strong>CodeSystem</strong> {@code BRCID10}. O {@code system} do alvo é,
     * portanto, {@code .../CodeSystem/BRCID10} — CID-10 sem ponto, como {@code "B04"}.
     *
     * <p><strong>Atenção (defeito nº 8).</strong> O exemplo oficial emite
     * {@code .../CodeSystem/BRTerminologiaSuspeitaDiagnostica}, usando o nome do ValueSet
     * como se fosse um CodeSystem. Essa URL não é definida por nenhum dos 113 artefatos de
     * conformidade, e um Bundle que a utilize reprova o binding {@code required}. É o mesmo
     * modo de falha que o mapeamento previne para códigos: valor certo, {@code system}
     * errado, significado quebrado.
     */
    CodedValue suspeitaDiagnostica;

    /** Um ou mais exames. {@code Composition.section.entry:diagnosticoLaboratorioClinico} é {@code 1..*}. */
    List<ExameLaboratorial> exames;

    /**
     * Um exame: o par {@code Observation} + {@code Specimen}.
     *
     * <p><strong>Sobre {@code metodo} e {@code valorReferencia}:</strong> são obrigatórios
     * pelo perfil e só aceitam texto livre. O perfil declara {@code Observation.method} e
     * {@code Observation.referenceRange} como {@code 1..1}, e proíbe todos os seus
     * sub-elementos estruturados — {@code method.coding} é {@code max=0};
     * {@code referenceRange.low}, {@code high}, {@code type}, {@code appliesTo} e
     * {@code age} também. Sobram {@code method.text} e {@code referenceRange.text}.
     *
     * <p>São, portanto, campos que a Camada 2 não tem como validar nem mapear: não há
     * vocabulário contra o qual conferir. O framework os repassa como texto, e essa
     * limitação é do perfil, não da implementação.
     */
    @Value
    @Builder
    public static class ExameLaboratorial {

        /** {@code Observation.code} — LOINC ou GAL; o {@code system} faz parte do alvo. */
        CodedValue nomeExame;
        /** {@code Observation.category} — binding a {@code BRCategoriaExame-1.0}. */
        CodedValue categoriaExame;
        /** {@code Specimen.type} — HL7 v2-0487 ou {@code BRTipoAmostraGAL}. */
        CodedValue tipoAmostra;

        /** {@code Observation.effective[x]} — coleta da amostra, como o perfil a define. */
        Instant dataColeta;
        /** {@code Observation.issued} — quando o laudo foi liberado. */
        Instant dataLiberacaoLaudo;

        /** {@code Observation.method.text} — texto livre por imposição do perfil. */
        String metodo;
        /** {@code Observation.referenceRange.text} — texto livre por imposição do perfil. */
        String valorReferencia;

        /**
         * {@code Observation.note} — observação textual sobre o resultado. Opcional.
         *
         * <p>É {@code MustSupport} no perfil, e o perfil <strong>proíbe</strong>
         * {@code note.author[x]} e {@code note.time}: só o texto é aceito. A anotação pertence
         * ao laudo, e não a alguém em determinado instante.
         */
        String observacao;

        // ── Resultado: qualitativo XOR quantitativo ─────────────────────
        /**
         * Resultado codificado. Binding a {@code BRResultadoQualitativoExame-2.0}, que compõe
         * o CodeSystem base mais oito {@code BRTipoResultado*} — por isso o alvo é o par
         * ({@code system}, {@code code}) e não um código solto.
         */
        CodedValue resultadoQualitativo;

        /**
         * Resultado numérico. Exige {@link #unidadeMedida}.
         *
         * <p><strong>Limitação estrutural do perfil.</strong> O framework não verifica se a
         * unidade é compatível com o exame, e <em>não tem como</em>: o perfil proíbe codificar
         * a unidade (ver {@link #unidadeMedida}), de modo que não existe vocabulário contra o
         * qual cruzá-la. O {@code LoincUcumCrossValidator} previsto como elo 5 da Frente A não
         * tem objeto no REL v3.2.1.
         */
        BigDecimal resultadoQuantitativo;
        /**
         * Unidade de medida, obrigatória quando há resultado quantitativo.
         *
         * <p><strong>Não é UCUM, e o nome do campo evita prometer que seja.</strong> O perfil
         * do REL impede codificar a unidade: o binding {@code required} de
         * {@code Observation.value[x]} recai sobre {@code Quantity.code} e exige ali um código
         * de resultado qualitativo. A unidade viaja em {@code Quantity.unit}, como texto livre.
         *
         * <p>A consequência é que nada valida a unidade: uma carga viral em {@code "mg/dL"}
         * produz Bundle aprovado. É o mesmo modo de falha que o mapeamento terminológico
         * previne para códigos, e neste perfil ele <strong>não tem solução técnica</strong> —
         * não há vocabulário contra o qual conferir.
         */
        String unidadeMedida;

        // ── Opcionais ───────────────────────────────────────────────────
        /**
         * {@code Observation.interpretation} — ValueSet <strong>distinto</strong> do resultado:
         * {@code BRResultadoQualitativoExame-1.0}, com apenas três códigos. Confundi-lo com o
         * ValueSet 2.0 do resultado é erro fácil e silencioso.
         */
        CodedValue interpretacao;
        /** Extensão {@code BRPatogeno}. */
        CodedValue patogeno;

        /** @return {@code true} se o resultado é numérico */
        public boolean isQuantitativo() {
            return resultadoQuantitativo != null;
        }
    }

    /**
     * @return o identificador do indivíduo — CNS quando presente, senão CPF
     * @throws IllegalStateException se nenhum dos dois foi informado
     */
    public String identificadorIndividuo() {
        if (cnsPaciente != null && !cnsPaciente.isBlank()) {
            return cnsPaciente;
        }
        if (cpfPaciente != null && !cpfPaciente.isBlank()) {
            return cpfPaciente;
        }
        throw new IllegalStateException("Nenhum identificador de indivíduo informado: CNS ou CPF é obrigatório "
                + "(Composition.subject.identifier é 1..1 no perfil).");
    }
}
