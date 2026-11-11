// Executa o Validador Local da RNDS sem a janela.
//
// O aplicativo do DATASUS (SOA-RNDS_ValidadorLocal_Aplicativo) é uma aplicação Swing cuja
// janela, ao clicar em "Validar", chama fhir.val.ExecutarValidacao.executar. Este programa
// chama o mesmo método, com os mesmos três parâmetros da janela: a pasta dos arquivos de
// definição, o arquivo JSON e o arquivo de mensagens. Nada do aplicativo é alterado nem
// copiado para este repositório.
//
// Uso (Java 11 ou superior, modo de arquivo-fonte):
//   java -cp "<pasta-do-validador>/libs/*" scripts/ValidadorLocalSemJanela.java \
//        <pasta-de-definicoes> <arquivo.json> <arquivo-de-mensagens>
//
// O resumo vai para a saída padrão; as mensagens, para o arquivo indicado, como na janela.
public class ValidadorLocalSemJanela {

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("uso: ValidadorLocalSemJanela <pasta-de-definicoes> <arquivo.json> <arquivo-de-mensagens>");
            System.exit(2);
        }
        System.setProperty("java.awt.headless", "true");
        String resumo = fhir.val.ExecutarValidacao.executar(args[0], args[1], args[2], false, null, false);
        System.out.println(resumo);
    }
}
