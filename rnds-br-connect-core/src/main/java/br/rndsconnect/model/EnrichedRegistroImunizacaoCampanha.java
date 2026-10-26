package br.rndsconnect.model;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

/**
 * DTO enriquecido do RIA-C: o que a Camada 2 entrega à Camada 3 no serviço de campanha.
 *
 * <p><strong>É deliberadamente uma classe separada de {@code EnrichedRegistroImunizacao}, e
 * não uma subclasse.</strong> Os dois perfis não se relacionam por especialização: eles
 * divergem em ambas as direções.
 *
 * <p>Campos que o RIA-R exige e o RIA-C <strong>proíbe</strong> ({@code max=0}):
 * {@code site}, {@code route}, {@code performer.function}, {@code reportOrigin},
 * {@code primarySource}, {@code location}, {@code expirationDate}, {@code reasonReference} e
 * a extensão de estratégia de vacinação. Campos que o RIA-C exige e o RIA-R deixa opcional:
 * {@code manufacturer.identifier} e {@code lotNumber}.
 *
 * <p>Um DTO comum precisaria carregar campos que, num dos dois serviços, são impossíveis de
 * preencher — e nada impediria o preenchimento. A separação transforma "não pode existir" em
 * "não existe o campo", que é a única forma de garantia que o compilador entende.
 *
 * <p>Note também o que <em>não</em> está aqui: campanha não tem estratégia de vacinação. A
 * campanha <strong>é</strong> a estratégia.
 *
 * @author Igor Mendes Domingues Miras
 */
@Value
@Builder
public class EnrichedRegistroImunizacaoCampanha {

    // ── Envelope ────────────────────────────────────────────────────────
    String idLocalRegistro;

    // ── Indivíduo — exatamente um dos dois ──────────────────────────────
    String cnsPaciente;
    String cpfPaciente;

    // ── Contato assistencial ────────────────────────────────────────────
    String cnesEstabelecimento;
    String cnsProfissional;
    /** {@code Composition.date} — criação no sistema de origem. */
    Instant dataRegistroOrigem;

    LocalDate dataAplicacao;

    // ── Obrigatórios pelo perfil ────────────────────────────────────────
    CodedValue grupoAtendimento;
    CodedValue condicaoMaternal;
    CodedValue vacina;
    /** {@code protocolApplied.doseNumberString} — código do {@code BRDose}. */
    String doseNumber;
    /**
     * Código do {@code BRFabricantePNI}.
     *
     * <p>{@code manufacturer.identifier} é {@code 1..1} aqui e opcional no RIA-R. Em
     * campanha, saber o fabricante do lote aplicado é requisito de farmacovigilância.
     */
    String codigoFabricante;
    /** {@code Immunization.lotNumber} — {@code 1..1} aqui, {@code 0..1} no RIA-R. */
    String lote;

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
