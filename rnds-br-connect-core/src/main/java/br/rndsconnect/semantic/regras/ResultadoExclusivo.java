package br.rndsconnect.semantic.regras;

import br.rndsconnect.dto.RegistroExameLaboratorialDTO;
import br.rndsconnect.semantic.RegraValidacao;
import br.rndsconnect.semantic.RndsValidationException;

/**
 * Elo 4 (A1) — o resultado é quantitativo <strong>ou</strong> qualitativo, nunca ambos.
 *
 * <p>{@code Observation.value[x]} é {@code 1..1} e admite {@code Quantity} ou
 * {@code CodeableConcept}. A notação {@code [x]} do FHIR já expressa exclusividade — mas ela
 * só é verificável depois que o Bundle existe. Aqui a violação é apanhada no DTO, com o nome
 * do campo do integrador e o índice do exame, em vez de um caminho FHIR.
 *
 * <p>O {@code RelBundleBuilder} mantém a mesma verificação, porque é API pública e precisa se
 * proteger de uso direto. A duplicação é deliberada: aqui o diagnóstico é melhor, lá a
 * garantia é estrutural.
 *
 * @author Igor Mendes Domingues Miras
 */
public class ResultadoExclusivo implements RegraValidacao<RegistroExameLaboratorialDTO> {

    /** Identificação da regra nas mensagens de erro. */
    public static final String REGRA = "ELO-4/RESULTADO-EXCLUSIVO";

    @Override
    public void validar(RegistroExameLaboratorialDTO dto) {
        if (dto.getExames() == null) {
            return;
        }
        for (int i = 0; i < dto.getExames().size(); i++) {
            RegistroExameLaboratorialDTO.ExameDTO e = dto.getExames().get(i);
            boolean qual = e.getResultadoQualitativoLocal() != null
                    && !e.getResultadoQualitativoLocal().isBlank();
            boolean quant = e.getResultadoQuantitativo() != null;

            if (qual && quant) {
                throw new RndsValidationException(
                        "exames[" + i + "].resultado",
                        REGRA,
                        "informados resultado qualitativo E quantitativo; Observation.value[x] é 1..1",
                        e.getResultadoQualitativoLocal() + " / " + e.getResultadoQuantitativo());
            }
            if (!qual && !quant) {
                throw new RndsValidationException(
                        "exames[" + i + "].resultado",
                        REGRA,
                        "nenhum resultado informado; Observation.value[x] é 1..1",
                        null);
            }
            if (quant && (e.getUnidadeMedida() == null || e.getUnidadeMedida().isBlank())) {
                throw new RndsValidationException(
                        "exames[" + i + "].unidadeMedida",
                        REGRA,
                        "resultado quantitativo exige unidade de medida — um número sem unidade "
                                + "não tem significado clínico",
                        String.valueOf(e.getResultadoQuantitativo()));
            }
        }
    }

    @Override
    public String identificacao() {
        return REGRA;
    }
}
