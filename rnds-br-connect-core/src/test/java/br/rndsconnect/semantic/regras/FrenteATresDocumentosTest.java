package br.rndsconnect.semantic.regras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.dto.RegistroAtendimentoClinicoDTO;
import br.rndsconnect.dto.RegistroDispensacaoMedicamentoDTO;
import br.rndsconnect.dto.RegistroSumarioAltaDTO;
import br.rndsconnect.semantic.RndsValidationException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Frente A nos três documentos que não tinham cadeia: REDFM, SA e RAC.
 *
 * <p>Até esta etapa, o {@code MappingOrchestrator} registrava cadeias apenas para RIA-R,
 * RIA-C, REL e RIRA. Nos outros três, <strong>nenhum CNS passava pelo Módulo 11</strong> — nem
 * o do paciente, nem os dos profissionais —, e a estrutura se apoiava no
 * {@code exigirObrigatorios} de cada Builder, que verifica presença e não dígito verificador.
 *
 * <p>Os testes abaixo exercitam as cadeias direto, sem Facade e sem pacote de conformidade:
 * o que se verifica aqui é a regra, não a montagem.
 *
 * @author Igor Mendes Domingues Miras
 */
class FrenteATresDocumentosTest {

    /** Reprova no Módulo 11 com resto 5. Era o CNS da fixture do RAC na Facade até 12/10. */
    private static final String CNS_INVALIDO = "819217217061851";

    /** Soma ponderada divisível por 11, faixa 8. */
    private static final String CNS_VALIDO = "898005900000003";

    private static final Instant ONTEM = Instant.parse("2026-10-11T12:00:00Z");

    // ══ O Módulo 11 chega aos três ══════════════════════════════════════

    @Test
    @DisplayName("CNS do paciente inválido reprova no REDFM, no SA e no RAC")
    void cnsInvalidoReprovaNosTres() {
        assertThatThrownBy(() -> RegrasPadrao.dispensacaoMedicamentoA1()
                        .validar(redfm().cnsPaciente(CNS_INVALIDO).build()))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining(CnsValido.REGRA)
                .hasMessageContaining("cnsPaciente");

        assertThatThrownBy(() -> RegrasPadrao.sumarioAltaA1()
                        .validar(sa().cnsPaciente(CNS_INVALIDO).build()))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining(CnsValido.REGRA);

        assertThatThrownBy(() -> RegrasPadrao.atendimentoClinicoA1()
                        .validar(rac().cnsPaciente(CNS_INVALIDO).build()))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining(CnsValido.REGRA);
    }

    @Test
    @DisplayName("O CNS do profissional também é verificado, não só o do paciente")
    void cnsDoProfissionalTambemEhVerificado() {
        assertThatThrownBy(() -> RegrasPadrao.atendimentoClinicoA1()
                        .validar(rac().contato(RegistroAtendimentoClinicoDTO.ContatoAssistencialDTO.builder()
                                        .cnsProfissional(CNS_INVALIDO)
                                        .build())
                                .build()))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining("contato.cnsProfissional");

        assertThatThrownBy(() -> RegrasPadrao.sumarioAltaA1()
                        .validar(sa().contato(RegistroSumarioAltaDTO.ContatoAssistencialDTO.builder()
                                        .cnsProfissionalAlta(CNS_INVALIDO)
                                        .build())
                                .build()))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining("contato.cnsProfissionalAlta");
    }

    // ══ A interação que poderia ter quebrado a oitava demonstração ══════

    @Test
    @DisplayName("Sem CNS a cadeia do RAC passa — é o indivíduo não identificado")
    void ausenciaDeCnsNaoReprovaOrac() {
        // Se o elo fosse registrado como obrigatório, o documento da oitava demonstração
        // reprovaria na Frente A, antes de chegar ao Builder. O ponto é esse: a
        // obrigatoriedade de haver UM identificador é do Builder, que sabe das duas formas
        // excludentes; a Frente A só verifica o que existe.
        assertThatCode(() -> RegrasPadrao.atendimentoClinicoA1().validar(rac().build()))
                .doesNotThrowAnyException();

        assertThatCode(() -> RegrasPadrao.sumarioAltaA1().validar(sa().build())).doesNotThrowAnyException();
    }

    // ══ Datas ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("Data de registro no futuro reprova nos três")
    void dataNoFuturoReprovaNosTres() {
        Instant daquiAUmAno = Instant.now().plusSeconds(365L * 24 * 3600);

        assertThatThrownBy(() -> RegrasPadrao.dispensacaoMedicamentoA1()
                        .validar(redfm().dataRegistroOrigem(daquiAUmAno).build()))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining(DataNaoFutura.REGRA);

        assertThatThrownBy(() -> RegrasPadrao.sumarioAltaA1()
                        .validar(sa().dataRegistroOrigem(daquiAUmAno).build()))
                .isInstanceOf(RndsValidationException.class);

        assertThatThrownBy(() -> RegrasPadrao.atendimentoClinicoA1()
                        .validar(rac().dataRegistroOrigem(daquiAUmAno).build()))
                .isInstanceOf(RndsValidationException.class);
    }

    @Test
    @DisplayName("Contato que termina antes de começar reprova no SA e no RAC")
    void inversaoCausalNoContatoReprova() {
        assertThatThrownBy(() -> RegrasPadrao.atendimentoClinicoA1()
                        .validar(rac().contato(RegistroAtendimentoClinicoDTO.ContatoAssistencialDTO.builder()
                                        .dataInicio(ONTEM)
                                        .dataFim(ONTEM.minusSeconds(3600))
                                        .build())
                                .build()))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining("contato.dataFim");

        assertThatThrownBy(() -> RegrasPadrao.sumarioAltaA1()
                        .validar(sa().contato(RegistroSumarioAltaDTO.ContatoAssistencialDTO.builder()
                                        .dataInicio(ONTEM)
                                        .dataFim(ONTEM.minusSeconds(3600))
                                        .build())
                                .build()))
                .isInstanceOf(RndsValidationException.class);
    }

    @Test
    @DisplayName("A validade do lote NÃO é submetida ao DataNaoFutura")
    void validadeDoLoteFicaDeFora() {
        // Data de vencimento tem de estar no futuro. Se entrasse na cadeia como "não futura",
        // todo lote válido reprovaria — o elo inverso, se vier, é outro elo.
        assertThatCode(() -> RegrasPadrao.dispensacaoMedicamentoA1()
                        .validar(redfm().validade(java.time.LocalDate.now().plusYears(2))
                                .build()))
                .doesNotThrowAnyException();
    }

    // ══ A dívida da parte 2, declarada ══════════════════════════════════

    @Test
    @DisplayName("As cadeias A2 dos três estão vazias, e isso é declarado")
    void cadeiasA2DosTresEstaoVazias() {
        assertThat(RegrasPadrao.dispensacaoMedicamentoA2().size())
                .as("A2 do REDFM: candidatos dependem de tabela de produto, ADR-05")
                .isZero();
        assertThat(RegrasPadrao.sumarioAltaA2().size()).isZero();
        assertThat(RegrasPadrao.atendimentoClinicoA2().size()).isZero();
    }

    @Test
    @DisplayName("Os sete pares existem e nenhum tem cadeia nula")
    void osSetePares() {
        assertThat(RegrasPadrao.imunizacaoRotina().a1()).isNotNull();
        assertThat(RegrasPadrao.imunizacaoCampanha().a1()).isNotNull();
        assertThat(RegrasPadrao.exameLaboratorial().a1()).isNotNull();
        assertThat(RegrasPadrao.regulacaoAssistencial().a1()).isNotNull();
        assertThat(RegrasPadrao.dispensacaoMedicamento().a1().size()).isEqualTo(8);
        assertThat(RegrasPadrao.sumarioAlta().a1().size()).isEqualTo(10);
        assertThat(RegrasPadrao.atendimentoClinico().a1().size()).isEqualTo(10);
    }

    // ══ Parte 2: os elos de lista ═══════════════════════════════════════

    @Test
    @DisplayName("O cnsExecutante do procedimento do RAC passa pelo Módulo 11")
    void cnsExecutanteDoProcedimentoEhVerificado() {
        // Era o último CNS do escopo que escapava: nenhum elo escalar o alcança, porque está
        // dentro de uma lista.
        assertThatThrownBy(() -> RegrasPadrao.atendimentoClinicoA1()
                        .validar(rac().procedimentos(List.of(
                                        RegistroAtendimentoClinicoDTO.ProcedimentoDTO.builder()
                                                .cnsExecutante(CNS_VALIDO)
                                                .build(),
                                        RegistroAtendimentoClinicoDTO.ProcedimentoDTO.builder()
                                                .cnsExecutante(CNS_INVALIDO)
                                                .build()))
                                .build()))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining("procedimentos[1].cnsExecutante");
    }

    @Test
    @DisplayName("Data de procedimento no futuro reprova, com a posição na mensagem")
    void dataDeProcedimentoNoFuturoReprovaComPosicao() {
        Instant daquiAUmAno = Instant.now().plusSeconds(365L * 24 * 3600);

        assertThatThrownBy(() -> RegrasPadrao.sumarioAltaA1()
                        .validar(sa().procedimentos(List.of(
                                        RegistroSumarioAltaDTO.ProcedimentoDTO.builder()
                                                .dataRealizacao(ONTEM)
                                                .build(),
                                        RegistroSumarioAltaDTO.ProcedimentoDTO.builder()
                                                .dataRealizacao(daquiAUmAno)
                                                .build()))
                                .build()))
                .isInstanceOf(RndsValidationException.class)
                .hasMessageContaining("procedimentos[1].dataRealizacao");
    }

    @Test
    @DisplayName("O afastamento do atestado PODE começar no futuro")
    void afastamentoFuturoNaoReprova() {
        // Atestado emitido hoje para repouso a partir de segunda é correto. Reprovar aqui
        // negaria atendimento — o erro mais caro, pelo mesmo critério do CnsValido.
        assertThatCode(() -> RegrasPadrao.atendimentoClinicoA1()
                        .validar(rac().atestados(List.of(RegistroAtendimentoClinicoDTO.AtestadoDTO.builder()
                                        .dataInicioAfastamento(Instant.now().plusSeconds(5L * 24 * 3600))
                                        .diasAfastamento(3)
                                        .build()))
                                .build()))
                .doesNotThrowAnyException();
    }

    // ══ Fixtures mínimas: só os campos que as cadeias leem ══════════════

    private static RegistroDispensacaoMedicamentoDTO.RegistroDispensacaoMedicamentoDTOBuilder redfm() {
        return RegistroDispensacaoMedicamentoDTO.builder()
                .idLocalRegistro("FA-REDFM-1")
                .cnsPaciente(CNS_VALIDO)
                .dataRegistroOrigem(ONTEM);
    }

    private static RegistroSumarioAltaDTO.RegistroSumarioAltaDTOBuilder sa() {
        return RegistroSumarioAltaDTO.builder().idLocalRegistro("FA-SA-1").dataRegistroOrigem(ONTEM);
    }

    private static RegistroAtendimentoClinicoDTO.RegistroAtendimentoClinicoDTOBuilder rac() {
        return RegistroAtendimentoClinicoDTO.builder()
                .idLocalRegistro("FA-RAC-1")
                .dataRegistroOrigem(ONTEM);
    }
}
