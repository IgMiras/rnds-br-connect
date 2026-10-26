package br.rndsconnect.semantic.regras;

import br.rndsconnect.model.EnrichedRegistroRegulacaoAssistencial;
import br.rndsconnect.semantic.RegraValidacao;
import br.rndsconnect.semantic.RndsValidationException;
import java.util.Set;

/**
 * Elo 10 (A2) — RN03 do manual RIRA: procedimentos dos grupos 03 e 04 exigem CBO.
 *
 * <p>O manual estabelece que, quando o procedimento solicitado pertence aos grupos
 * <strong>03</strong> (procedimentos clínicos) ou <strong>04</strong> (procedimentos
 * cirúrgicos) da Tabela SUS, a ocupação do profissional executante passa a ser obrigatória.
 *
 * <p><strong>A Camada 4 não alcança esta regra.</strong> No perfil,
 * {@code ServiceRequest.performerType} é {@code 0..1} e {@code Appointment.specialty} também.
 * Uma solicitação de cirurgia cardiovascular sem CBO passa em qualquer validador FHIR — a
 * regra está na prosa do manual, não no perfil.
 *
 * <p>A regra não é marginal: dos 4.932 códigos da Tabela SUS, <strong>2.533 pertencem aos
 * grupos 03 e 04</strong> — mais da metade.
 *
 * <h2>Por que o {@code system} é verificado antes do {@code code}</h2>
 *
 * <p>O binding de {@code ServiceRequest.code} é ao ValueSet
 * {@code BRProcedimentosNacionais-1.0}, que compõe <strong>dois</strong> CodeSystems de
 * estruturas incompatíveis:
 *
 * <table border="1">
 *   <caption>Os dois CodeSystems de procedimento</caption>
 *   <tr><th>CodeSystem</th><th>Códigos</th><th>Formato</th></tr>
 *   <tr><td>{@code BRTabelaSUS}</td><td>4.932</td><td>10 dígitos; os dois primeiros são o grupo</td></tr>
 *   <tr><td>{@code BRCBHPMTUSS}</td><td>32.891</td><td>3 dígitos; o prefixo não é grupo</td></tr>
 * </table>
 *
 * <p>Aplicar "começa com 03 ou 04" ao {@code BRCBHPMTUSS} produziria <strong>falso
 * positivo</strong>: os códigos {@code "030"} e {@code "031"} existem lá e não são "grupo 03"
 * no sentido da RN03. A regra fala da Tabela SUS, e só dela.
 *
 * <p>É a quarta vez no framework que o alvo do mapeamento precisa ser o par
 * ({@code system}, {@code code}) e não um código solto — depois de {@code NOME_EXAME},
 * {@code TIPO_AMOSTRA} e {@code RESULTADO_QUALITATIVO}.
 *
 * <h2>Por que é A2</h2>
 *
 * <p>A regra fala de grupos da Tabela SUS. Antes da tradução existe apenas o código local do
 * integrador — {@code "CIRURGIA_CIA"}, {@code "cod_proc_442"} —, e não há como determinar o
 * grupo. Ver Seção 7.1 da Arquitetura.
 *
 * @author Igor Mendes Domingues Miras
 */
public class RN03ProcedimentoExigeCbo implements RegraValidacao<EnrichedRegistroRegulacaoAssistencial> {

    /** Identificação da regra nas mensagens de erro. */
    public static final String REGRA = "ELO-10/RN03-PROCEDIMENTO-EXIGE-CBO";

    /** URL canônica da Tabela SUS — o único CodeSystem sobre o qual a RN03 se pronuncia. */
    public static final String SYSTEM_TABELA_SUS = "http://www.saude.gov.br/fhir/r4/CodeSystem/BRTabelaSUS";

    /** Grupos cujos procedimentos exigem a ocupação do executante. */
    private static final Set<String> GRUPOS_QUE_EXIGEM_CBO = Set.of("03", "04");

    @Override
    public void validar(EnrichedRegistroRegulacaoAssistencial dto) {
        if (dto.getProcedimento() == null) {
            return; // obrigatoriedade estrutural é do Builder
        }
        if (!SYSTEM_TABELA_SUS.equals(dto.getProcedimento().system())) {
            return; // a RN03 fala da Tabela SUS; o BRCBHPMTUSS tem outra estrutura
        }

        String codigo = dto.getProcedimento().code();
        if (codigo == null || codigo.length() < 2) {
            return; // código malformado é problema do mapeamento, não desta regra
        }
        String grupo = codigo.substring(0, 2);
        if (!GRUPOS_QUE_EXIGEM_CBO.contains(grupo)) {
            return;
        }

        if (dto.getCboExecutante() == null) {
            throw new RndsValidationException(
                    "cboExecutanteLocal",
                    REGRA,
                    "o procedimento " + codigo + " pertence ao grupo " + grupo
                            + " da Tabela SUS, e a RN03 exige a ocupação do executante. "
                            + "ServiceRequest.performerType é 0..1 no perfil, e a Camada 4 não pega isso",
                    null);
        }
    }

    @Override
    public String identificacao() {
        return REGRA;
    }
}
