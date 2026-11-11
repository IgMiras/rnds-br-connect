// Valida arquivos JSON com a Camada 4 do framework, contra o pacote de conformidade de um
// serviço, para comparar o veredicto com o do Validador Local da RNDS sobre o mesmo arquivo.
//
// Uso (modo de arquivo-fonte, com as classes do core e as suas dependências no classpath):
//   java -cp "<classes>:<dependências>" scripts/CamadaQuatroEmArquivos.java \
//        <SERVICO> <pacote-no-classpath> <pasta-de-mensagens> <arquivo.json>...
//
// Para cada arquivo, grava <nome>.camada4.txt com todas as mensagens do validador e imprime
// uma linha "RESULTADO<TAB>arquivo<TAB>APROVADO|REPROVADO<TAB>erros". Como na Facade, só as
// severidades erro e fatal reprovam.
import br.rndsconnect.validator.ConformancePackage;
import br.rndsconnect.validator.LocalFhirValidator;
import br.rndsconnect.validator.Servico;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TimeZone;
import org.hl7.fhir.r4.model.Bundle;

public class CamadaQuatroEmArquivos {

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("uso: CamadaQuatroEmArquivos <SERVICO> <pacote> <pasta-de-mensagens> <arquivo.json>...");
            System.exit(2);
        }
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        FhirContext ctx = FhirContext.forR4();
        LocalFhirValidator validador =
                new LocalFhirValidator(ctx, ConformancePackage.paraServico(ctx, Servico.valueOf(args[0]), args[1]));
        IParser parser = ctx.newJsonParser();
        Path mensagens = Path.of(args[2]);
        Files.createDirectories(mensagens);

        for (int i = 3; i < args.length; i++) {
            Path arquivo = Path.of(args[i]);
            Bundle bundle = parser.parseResource(Bundle.class, Files.readString(arquivo, StandardCharsets.UTF_8));
            ValidationResult resultado = validador.validar(bundle);
            int erros = LocalFhirValidator.erros(resultado).size();

            StringBuilder texto = new StringBuilder();
            for (SingleValidationMessage m : resultado.getMessages()) {
                texto.append('[')
                        .append(m.getSeverity())
                        .append("] ")
                        .append(m.getLocationString())
                        .append(": ")
                        .append(m.getMessage())
                        .append('\n');
            }
            String nome = arquivo.getFileName().toString().replaceFirst("\\.json$", "");
            Files.writeString(mensagens.resolve(nome + ".camada4.txt"), texto, StandardCharsets.UTF_8);

            System.out.println(
                    "RESULTADO\t" + arquivo + "\t" + (erros == 0 ? "APROVADO" : "REPROVADO") + "\t" + erros);
        }
    }
}
