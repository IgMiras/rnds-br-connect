package br.rndsconnect.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Modelo canônico do resultado de exame laboratorial — entrada pública do serviço REL.
 *
 * <p>Todos os códigos estão no vocabulário do sistema cliente, como nos demais DTOs. A
 * novidade estrutural é a lista de exames: um mesmo laudo pode conter vários resultados, e
 * o perfil declara {@code Composition.section.entry:diagnosticoLaboratorioClinico} como
 * {@code 1..*}.
 *
 * <p>Note que {@code metodo} e {@code valorReferencia} são {@code String} e não códigos: o
 * perfil os exige e proíbe que sejam estruturados. Ver
 * {@code EnrichedRegistroExameLaboratorial.ExameLaboratorial}.
 *
 * @author Igor Mendes Domingues Miras
 */
@Value
@Builder
public class RegistroExameLaboratorialDTO {

    String idLocalRegistro;

    String cnsPaciente;
    String cpfPaciente;

    /** CNPJ do laboratório que assina o laudo. */
    String cnpjLaboratorio;
    /** CNES do estabelecimento executor. */
    String cnesEstabelecimento;

    Instant dataRegistroOrigem;

    /** Suspeita diagnóstica no vocabulário local. */
    String suspeitaDiagnosticaLocal;

    /** Um ou mais exames do mesmo laudo. */
    List<ExameDTO> exames;

    /** Um exame do laudo, no vocabulário do sistema cliente. */
    @Value
    @Builder
    public static class ExameDTO {

        String nomeExameLocal;
        String categoriaExameLocal;
        String tipoAmostraLocal;

        /**
         * Coleta da amostra — vai para {@code Observation.effective[x]}, que o perfil define
         * como "Data/hora da coleta da amostra". Não é a data de realização do exame: a
         * Portaria GM/MS nº 8.276/2025 distingue coleta, realização e emissão, e o perfil 3.2.1
         * só tem lugar para a coleta e para a liberação do resultado.
         */
        Instant dataColeta;
        Instant dataLiberacaoLaudo;

        /** Texto livre — o perfil proíbe codificar. */
        String metodo;
        /** Texto livre — o perfil proíbe faixa numérica estruturada. */
        String valorReferencia;
        /** Observação textual sobre o resultado. Opcional; o perfil só aceita o texto. */
        String observacao;

        // ── Informe um OU outro, nunca os dois ──────────────────────────
        String resultadoQualitativoLocal;
        BigDecimal resultadoQuantitativo;
        /**
         * Unidade de medida; obrigatória quando o resultado é quantitativo.
         *
         * <p>Texto livre, não código UCUM: o perfil do REL proíbe codificar a unidade. Escreva
         * como um profissional leria — {@code "log10 cópias/mL"}, {@code "UI/mL"}.
         */
        String unidadeMedida;

        String interpretacaoLocal;
        String patogenoLocal;
    }
}
