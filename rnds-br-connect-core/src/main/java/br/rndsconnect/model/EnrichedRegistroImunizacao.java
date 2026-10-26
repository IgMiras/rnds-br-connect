package br.rndsconnect.model;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

/**
 * DTO enriquecido do RIA-R: o que a Camada 2 entrega à Camada 3.
 *
 * <p>A diferença em relação ao {@code RegistroImunizacaoDTO} (modelo canônico, Seção 6.3 da
 * arquitetura) é que aqui <strong>todos os códigos já estão no vocabulário oficial da
 * RNDS</strong>, como pares ({@code system}, {@code code}). O Builder não traduz nada: se um
 * {@link CodedValue} chegou até aqui, ele é o código que vai para o Bundle.
 *
 * <p>Essa separação é o que permite testar o Builder isoladamente, antes da Camada 2
 * existir — e é a mesma fronteira que a Seção 3.2 descreve entre "o que o dado parece" e "o
 * que o dado significa".
 *
 * <p><strong>Regras condicionais não moram aqui.</strong> "CID obrigatório na estratégia
 * Especial", "contato hanseníase obrigatório para BCG" e "fabricante e lote opcionais em
 * transcrição de caderneta" são elos da Frente A (Camada 2). O Builder confere apenas o que
 * o perfil FHIR exige estruturalmente.
 *
 * @author Igor Mendes Domingues Miras
 */
@Value
@Builder
public class EnrichedRegistroImunizacao {

    // ── Envelope ────────────────────────────────────────────────────────
    /** {@code Bundle.identifier.value} — identifica o registro na base de origem. */
    String idLocalRegistro;

    // ── Indivíduo — exatamente um dos dois ───────────────────────────────
    String cnsPaciente;
    String cpfPaciente;

    // ── Contato assistencial ────────────────────────────────────────────
    /** CNES — vai para {@code Composition.author.identifier}. */
    String cnesEstabelecimento;
    /** CNS do aplicador — vai para {@code Immunization.performer.actor.reference}. */
    String cnsProfissional;
    /**
     * {@code Composition.date} — quando o documento foi criado no sistema de origem.
     *
     * <p>Não confundir com {@code Bundle.timestamp}, que é o instante de montagem do
     * documento e fica a cargo do Builder. O exemplo oficial distingue os dois:
     * {@code Composition.date} = 08:18:30 e {@code Bundle.timestamp} = 14:59:51.
     */
    Instant dataRegistroOrigem;

    // ── Obrigatórios pelo perfil ────────────────────────────────────────
    CodedValue grupoAtendimento;
    CodedValue condicaoMaternal;
    CodedValue estrategiaVacinacao;
    CodedValue vacina;
    CodedValue localAplicacao;
    CodedValue viaAdministracao;
    /** {@code protocolApplied.doseNumberString} — código do {@code BRDose}. */
    String doseNumber;

    LocalDate dataAplicacao;

    // ── Opcionais pelo perfil ───────────────────────────────────────────
    /** Código do {@code BRFabricantePNI}. O {@code system} é {@code fixedUri} no perfil. */
    String codigoFabricante;

    String lote;
    LocalDate validadeLote;
    CodedValue registroOrigem;
    Boolean primarySource;
    /** Código do {@code BRPais}. O {@code system} é {@code fixedUri} no perfil. */
    String codigoPaisOrigem;
    /** CBO do profissional <strong>prescritor</strong>, não do aplicador. */
    CodedValue cboPrescritor;

    Boolean contatoHanseniase;
    PesquisaClinica pesquisaClinica;

    // ── Condition (BRCID10Avaliado-1.0) ─────────────────────────────────
    /** Presente ⇒ o Builder emite o {@code Condition} e o {@code reasonReference}. */
    CodedValue cid10;
    /** Texto livre: não passa pelo Motor Semântico. */
    String observacaoCondicao;
    /**
     * {@code Condition.onsetDateTime} — início da condição que motivou a indicação.
     *
     * <p>É {@code Instant}, e não {@code LocalDate}, porque o perfil e o exemplo usam
     * precisão de milissegundos aqui — ao contrário de {@code dataAplicacao}, que o exemplo
     * emite como data pura.
     */
    Instant dataInicioCondicao;

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
