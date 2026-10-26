package br.rndsconnect.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

/**
 * DTO enriquecido do REDFM: o que a Camada 2 entrega à Camada 3 no serviço de dispensação de
 * medicamentos.
 *
 * <h2>O que distingue este serviço</h2>
 *
 * <p>É o primeiro da geração <strong>{@code RNDS*}</strong>, publicada em 2025, e o primeiro
 * que <strong>deriva do BR-Core</strong>: oito de seus perfis têm {@code baseDefinition}
 * apontando para {@code br-core.saude.gov.br}, inclusive o perfil de documento — a razão de a
 * Arquitetura distinguir duas gerações de artefatos (§2.2): a fundação da geração {@code BR*},
 * o FHIR R4 base, não vale para a RNDS inteira.
 *
 * <p>Seu pacote de conformidade é o único que combina <strong>dois formatos</strong>: 381
 * artefatos XML do DATASUS mais 324 JSON do IG BR-Core, distribuído pelo Simplifier.
 *
 * <h2>Resources contidos, e não referenciados</h2>
 *
 * <p>É também o primeiro serviço a usar {@code contained}. O {@code MedicationDispense}
 * carrega quatro recursos aninhados, cada um com {@code id} fixado pelo perfil:
 *
 * <ul>
 *   <li>{@code prescription} — a receita, quando ela <em>não</em> está na RNDS
 *   <li>{@code prescriber-identifier} — o registro no conselho de quem prescreveu
 *   <li>{@code prescriber-specialty} — a lotação e o CBO do prescritor
 *   <li>{@code pharmacist-crf} — o CRF do farmacêutico que dispensou
 * </ul>
 *
 * <p>Referências a eles usam <strong>{@code #id}</strong>, e não {@code urn:uuid} — o que
 * convive com o ADR-07 sem contradizê-lo: {@code urn:uuid} referencia entradas do Bundle;
 * {@code #id} referencia recursos contidos, que não são entradas.
 *
 * @author Igor Mendes Domingues Miras
 */
@Value
@Builder
public class EnrichedRegistroDispensacaoMedicamento {

    // ── Envelope ────────────────────────────────────────────────────────
    String idLocalRegistro;
    /** Identificador do item dispensado — {@code MedicationDispense.identifier}. */
    String idItemDispensado;

    // ── Indivíduo ───────────────────────────────────────────────────────
    String cnsPaciente;
    String cpfPaciente;

    // ── Estabelecimento e datas ─────────────────────────────────────────
    /** CNES do estabelecimento que dispensou — {@code Composition.author}. */
    String cnesEstabelecimento;
    /** {@code Composition.date}. */
    Instant dataRegistroOrigem;
    /** {@code MedicationDispense.whenHandedOver} — quando o medicamento foi entregue. */
    Instant dataEntrega;

    // ── O medicamento ───────────────────────────────────────────────────
    /**
     * {@code Medication.code} — o medicamento no vocabulário da RNDS.
     *
     * <p>Binding ao {@code BRObmVMP}, o Vocabulário de Medicamentos Padronizado. Os códigos
     * têm forma {@code @brasilNNNNNNNNNNN}, diferente de tudo que os outros serviços usam.
     */
    CodedValue medicamento;
    /** Texto do medicamento, para {@code display} e {@code section.entry.display}. */
    String descricaoMedicamento;
    /** {@code Medication.form} — forma farmacêutica. */
    CodedValue formaFarmaceutica;
    /** CNPJ do fabricante — {@code Medication.manufacturer}. */
    String cnpjFabricante;
    /** {@code Medication.batch.lotNumber}. */
    String lote;
    /** {@code Medication.batch.expirationDate}. */
    LocalDate validade;

    // ── A dispensação ───────────────────────────────────────────────────
    /** {@code MedicationDispense.category} — ambiente da dispensação. */
    CodedValue categoriaDispensacao;
    /** {@code MedicationDispense.type} — dispensação total, parcial ou não realizada. */
    CodedValue tipoDispensacao;
    /** {@code MedicationDispense.quantity.value}. */
    BigDecimal quantidade;
    /** Unidade da quantidade, em texto — {@code quantity.unit}. */
    String unidadeQuantidade;

    /** {@code MedicationDispense.note} — observação sobre a dispensação. Opcional. */
    String observacaoDispensacao;

    /**
     * {@code Composition.relatesTo} — identificador, na RNDS, do documento que este substitui.
     *
     * <p>{@code MustSupport} e {@code 0..1}. Só faz sentido numa retificação: o documento novo
     * declara qual registro anterior ele corrige. Quando ausente, o Builder não emite o
     * elemento.
     */
    String substituiDocumentoRnds;

    // ── Quem dispensou ──────────────────────────────────────────────────
    /** CNS do farmacêutico — {@code performer:pharmacist}. */
    String cnsFarmaceutico;
    /** CBO do farmacêutico — {@code performer:pharmacist.function}. */
    CodedValue cboFarmaceutico;
    /** Conselho do farmacêutico, ex.: {@code "crf-sp"} — vira {@code contained:pharmacist-crf}. */
    String conselhoFarmaceutico;
    /** Número de inscrição no conselho. */
    String inscricaoFarmaceutico;

    // ── A prescrição ────────────────────────────────────────────────────
    /**
     * Identificador da prescrição <strong>na RNDS</strong>.
     *
     * <p>Quando presente, o Builder emite a fatia {@code authorizingPrescription:inRNDS},
     * por {@code identifier}, e <strong>não</strong> gera o {@code contained:prescription}.
     * As duas fatias são mutuamente exclusivas: ou a receita já está na base nacional, ou
     * vem embutida no documento.
     */
    String idPrescricaoNaRnds;

    /**
     * Dados da receita, quando ela <strong>não</strong> está na RNDS.
     *
     * <p>Presente ⇒ o Builder emite {@code contained:prescription} e a fatia
     * {@code authorizingPrescription:outOfRNDS}, que referencia {@code #prescription}.
     */
    Prescricao prescricao;

    /** A receita embutida, para o caso de ela não estar na RNDS. */
    @Value
    @Builder
    public static class Prescricao {
        /** Identificador do item prescrito. */
        String idItemPrescrito;
        /** {@code MedicationRequest.authoredOn}. */
        Instant dataPrescricao;
        /** CNS do prescritor. */
        String cnsPrescritor;
        /** Conselho do prescritor, ex.: {@code "crm-sp"}. */
        String conselhoPrescritor;
        /** Número de inscrição no conselho. */
        String inscricaoPrescritor;
        /** CBO do prescritor — vai para {@code contained:prescriber-specialty}. */
        CodedValue cboPrescritor;
        /** CNES da lotação do prescritor. */
        String cnesLotacaoPrescritor;
        /** {@code MedicationRequest.reasonCode} — CID que motivou a prescrição. */
        CodedValue cid10;
        /** {@code dosageInstruction.route}. */
        CodedValue viaAdministracao;
        /** {@code doseAndRate.doseQuantity.value}. */
        BigDecimal dose;
        /** Unidade da dose. */
        CodedValue unidadeDose;
        /** Texto da unidade da dose. */
        String descricaoUnidadeDose;
        /** {@code MedicationRequest.note} — observação sobre a prescrição. Opcional. */
        String observacao;
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
        throw new IllegalStateException("Nenhum identificador de indivíduo informado: CNS ou CPF é obrigatório.");
    }

    /** @return {@code true} se a receita vem embutida, em vez de referenciada na RNDS */
    public boolean prescricaoEmbutida() {
        return prescricao != null;
    }
}
