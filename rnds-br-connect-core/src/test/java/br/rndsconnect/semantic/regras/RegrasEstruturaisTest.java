package br.rndsconnect.semantic.regras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.semantic.CadeiaDeValidacao;
import br.rndsconnect.semantic.RndsValidationException;
import java.time.Instant;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Regras estruturais da Frente A (A1) — as que operam sobre a <em>forma</em> do dado e não
 * dependem de vocabulário.
 *
 * <p>Cada regra é testada contra o que a Camada 4 <strong>não</strong> pega: o perfil exige
 * que {@code identifier.value} esteja preenchido, não que o número seja um CNS legítimo;
 * aceita {@code dateTime} de 2099; e não relaciona duas datas entre si. É a lacuna que a
 * Frente A ocupa.
 *
 * @author Igor Mendes Domingues Miras
 */
class RegrasEstruturaisTest {

    /** DTO mínimo para exercitar as regras sem depender dos DTOs reais. */
    private record Alvo(String cns, String cpf, Instant coleta, Instant laudo) {}

    private static final Function<Alvo, String> CNS = Alvo::cns;
    private static final Function<Alvo, String> CPF = Alvo::cpf;

    // ══ Elo 1 — CNS ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Elo 1 — CNS por Módulo 11")
    class Cns {

        private final CnsValido<Alvo> regra = new CnsValido<>("cnsPaciente", CNS, true);

        @Test
        @DisplayName("Aceita as duas faixas: definitiva (1, 2) e provisória (7, 8, 9)")
        void aceitaAsDuasFaixas() {
            // A soma ponderada dos 15 dígitos é múltipla de 11 em AMBAS as faixas —
            // no definitivo, por construção do dígito verificador sobre o PIS.
            for (String cns : new String[] {"123456789010000", "700000000000005", "898005900000003"}) {
                assertThat(CnsValido.somaPonderada(cns) % 11)
                        .as("CNS %s deveria ter soma ponderada múltipla de 11", cns)
                        .isZero();
                assertThatCode(() -> regra.validar(new Alvo(cns, null, null, null)))
                        .doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("Aceita CNS com máscara — a normalização é responsabilidade da regra")
        void aceitaComMascara() {
            assertThatCode(() -> regra.validar(new Alvo("898 0059 0000 0003", null, null, null)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Reprova dígito verificador inválido, com o resto no diagnóstico")
        void reprovaDigitoInvalido() {
            assertThatThrownBy(() -> regra.validar(new Alvo("898005900000004", null, null, null)))
                    .isInstanceOf(RndsValidationException.class)
                    .hasMessageContaining("ELO-1/CNS-MOD11")
                    .hasMessageContaining("não é múltipla de 11");
        }

        @Test
        @DisplayName("Reprova troca de dígitos de posição — o erro de digitação mais comum")
        void reprovaTrocaDeDigitos() {
            assertThat(CnsValido.somaPonderada("898005900000003") % 11).isZero();
            // 0 e 3 finais trocados: mesmos dígitos, posições diferentes.
            assertThatThrownBy(() -> regra.validar(new Alvo("898005900000030", null, null, null)))
                    .isInstanceOf(RndsValidationException.class)
                    .hasMessageContaining("dígitos trocados de posição");
        }

        @Test
        @DisplayName("Reprova primeiro dígito fora das faixas conhecidas")
        void reprovaFaixaDesconhecida() {
            assertThatThrownBy(() -> regra.validar(new Alvo("012345678912345", null, null, null)))
                    .isInstanceOf(RndsValidationException.class)
                    .hasMessageContaining("fora das faixas conhecidas");
        }

        @Test
        @DisplayName("Reprova comprimento diferente de 15")
        void reprovaComprimento() {
            assertThatThrownBy(() -> regra.validar(new Alvo("8980059", null, null, null)))
                    .isInstanceOf(RndsValidationException.class)
                    .hasMessageContaining("15 dígitos");
        }

        @Test
        @DisplayName("Os CNS dos exemplos oficiais da RNDS não passam — e isso é esperado")
        void cnsDosExemplosOficiaisSaoFicticios() {
            // Dado de exemplo DEVE ser fictício. O registro aqui serve para explicar por que
            // os artefatos de referência preservam esses números enquanto as fixtures da
            // Facade usam CNS matematicamente válidos.
            assertThat(CnsValido.somaPonderada("898005900000823") % 11)
                    .as("exemplo REL, paciente")
                    .isEqualTo(6);
            assertThatThrownBy(() -> regra.validar(new Alvo("012345678912345", null, null, null)))
                    .as("exemplo RIA-R, profissional — começa com 0")
                    .isInstanceOf(RndsValidationException.class);
        }

        @Test
        @DisplayName("Campo opcional ausente é aceito; obrigatório ausente reprova")
        void obrigatoriedadeConfiguravel() {
            assertThatCode(() -> new CnsValido<Alvo>("cnsProfissional", CNS, false)
                            .validar(new Alvo(null, null, null, null)))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> regra.validar(new Alvo(" ", null, null, null)))
                    .isInstanceOf(RndsValidationException.class)
                    .hasMessageContaining("obrigatório e ausente");
        }
    }

    // ══ Elo 2 — CPF ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Elo 2 — CPF pelos dois dígitos verificadores")
    class Cpf {

        private final CpfValido<Alvo> regra = new CpfValido<>("cpfPaciente", CPF, true);

        @Test
        @DisplayName("Aceita CPF válido, com e sem máscara")
        void aceitaValido() {
            assertThatCode(() -> regra.validar(new Alvo(null, "01234567890", null, null)))
                    .doesNotThrowAnyException();
            assertThatCode(() -> regra.validar(new Alvo(null, "529.982.247-25", null, null)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Reprova sequência de dígito repetido, que passa no Módulo 11 por acidente")
        void reprovaDigitoRepetido() {
            assertThatThrownBy(() -> regra.validar(new Alvo(null, "11111111111", null, null)))
                    .isInstanceOf(RndsValidationException.class)
                    .hasMessageContaining("acidente");
        }

        @Test
        @DisplayName("Reprova dígitos verificadores incorretos")
        void reprovaDvIncorreto() {
            assertThatThrownBy(() -> regra.validar(new Alvo(null, "01234567891", null, null)))
                    .isInstanceOf(RndsValidationException.class)
                    .hasMessageContaining("não conferem");
        }
    }

    // ══ Elo 3 — datas ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("Elo 3 — datas")
    class Datas {

        private static final Instant HOJE = Instant.parse("2024-04-01T12:00:00Z");
        private final DataNaoFutura<Alvo> regra = new DataNaoFutura<>("dataAplicacao", Alvo::coleta, () -> HOJE);

        @Test
        @DisplayName("Aceita passado e presente")
        void aceitaPassado() {
            assertThatCode(() -> regra.validar(new Alvo(null, null, HOJE.minusSeconds(86_400), null)))
                    .doesNotThrowAnyException();
            assertThatCode(() -> regra.validar(new Alvo(null, null, HOJE, null)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Tolera adiantamento de fuso — até um dia à frente")
        void toleraFuso() {
            assertThatCode(() -> regra.validar(new Alvo(null, null, HOJE.plusSeconds(18_000), null)))
                    .as("um sistema em UTC−5 pode produzir instante à frente do relógio de quem valida")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Reprova ano trocado — o erro que nenhum perfil FHIR impede")
        void reprovaFuturo() {
            assertThatThrownBy(() -> regra.validar(new Alvo(null, null, Instant.parse("2027-04-01T12:00:00Z"), null)))
                    .isInstanceOf(RndsValidationException.class)
                    .hasMessageContaining("ELO-3/DATA-FUTURA");
        }

        @Test
        @DisplayName("Elo 3b — laudo não pode ser liberado antes da coleta")
        void reprovaInversaoCausal() {
            OrdemCronologica<Alvo> ordem =
                    new OrdemCronologica<>("dataColeta", Alvo::coleta, "dataLiberacaoLaudo", Alvo::laudo);

            assertThatCode(() -> ordem.validar(new Alvo(null, null, HOJE, HOJE.plusSeconds(3600))))
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> ordem.validar(new Alvo(null, null, HOJE, HOJE.minusSeconds(3600))))
                    .isInstanceOf(RndsValidationException.class)
                    .hasMessageContaining("inversão causal");
        }
    }

    // ══ A cadeia ════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cadeia de validação")
    class Cadeia {

        @Test
        @DisplayName("Fail-fast: para na primeira violação, não acumula derivadas")
        void paraNaPrimeiraViolacao() {
            CadeiaDeValidacao<Alvo> cadeia = CadeiaDeValidacao.de(
                    new CnsValido<>("cnsPaciente", CNS, true), new CpfValido<>("cpfPaciente", CPF, true));

            // Os dois campos estão errados; só o primeiro erro é reportado.
            assertThatThrownBy(() -> cadeia.validar(new Alvo("000000000000000", "11111111111", null, null)))
                    .isInstanceOf(RndsValidationException.class)
                    .hasMessageContaining("cnsPaciente")
                    .hasMessageNotContaining("cpfPaciente");
        }

        @Test
        @DisplayName("Cadeia vazia aceita tudo — um serviço sem regras não é um erro")
        void cadeiaVaziaAceitaTudo() {
            assertThat(CadeiaDeValidacao.<Alvo>vazia().size()).isZero();
            assertThatCode(() -> CadeiaDeValidacao.<Alvo>vazia().validar(new Alvo(null, null, null, null)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Cada elo se identifica para o diagnóstico")
        void elosSeIdentificam() {
            CadeiaDeValidacao<Alvo> cadeia = CadeiaDeValidacao.de(
                    new CnsValido<>("cnsPaciente", CNS, true), new CpfValido<>("cpfPaciente", CPF, false));

            assertThat(cadeia.elos())
                    .extracting(e -> e.identificacao())
                    .containsExactly("ELO-1/CNS-MOD11(cnsPaciente)", "ELO-2/CPF-MOD11(cpfPaciente)");
        }

        @Test
        @DisplayName("A exceção carrega campo, regra e valor para tratamento programático")
        void excecaoEEstruturada() {
            RndsValidationException e = null;
            try {
                new CnsValido<Alvo>("cnsPaciente", CNS, true).validar(new Alvo("898005900000004", null, null, null));
            } catch (RndsValidationException ex) {
                e = ex;
            }
            assertThat(e).isNotNull();
            assertThat(e.getCampo()).isEqualTo("cnsPaciente");
            assertThat(e.getRegra()).isEqualTo("ELO-1/CNS-MOD11");
            assertThat(e.getValorRecebido()).isEqualTo("898005900000004");
        }
    }
}
