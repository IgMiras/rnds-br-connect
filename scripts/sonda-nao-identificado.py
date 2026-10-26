#!/usr/bin/env python3
"""Mede se a família `subject.extension:unidentifiedPatient` é exequível num pacote da RNDS.

Três partes, porque são perguntas diferentes e a auditoria de emissão não distingue uma da
outra:

  PARTE 0 — as seções do documento. Lista as fatias de `Composition.section`, a cardinalidade
  de cada uma e o perfil que o `entry` exige, dizendo de qual campo veio (`targetProfile` ou
  `profile`). Existe para a PARTE 2 mostrar o próprio serviço: se esta parte vier vazia num
  documento que tem seções, o veredicto da PARTE 2 é inconclusivo, não negativo.

  PARTE 1 — nível do ELEMENTO. O perfil que marca a extensão como MustSupport exige, ele mesmo,
  o identificador que a extensão existe para substituir? `subject.identifier` 1..1 com
  `display`/`reference` max=0 é contradição dentro de um único perfil — defeito nº 21.

  PARTE 2 — nível do DOCUMENTO. Mesmo onde a extensão é permitida, o documento pode ser
  forçado a instanciar OUTRO perfil que exige o identificador. Percorre o alcance obrigatório
  a partir da raiz (só arestas com min>=1, e para seções fatiadas exige que a fatia também
  seja obrigatória) e mostra, para cada perfil alcançado, o que cabe no `subject`.

Lê o `snapshot` quando o arquivo tem um — é ele que manda na cardinalidade efetiva — e cai no
`differential` quando não tem, o que é suficiente nos perfis da RNDS porque derivam direto do
recurso base do R4. A PARTE 1 avisa quando essa premissa não vale.

Uso:  python3 sonda-nao-identificado.py <pacote> [perfil-raiz ...]
Rode na raiz do repositório. Não compila nada e não usa o HAPI — lê os XML do pacote.
"""
import glob
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

N = {"f": "http://hl7.org/fhir"}
RAIZ = Path("rnds-br-connect-core/src/main/resources/conformance")
BASE_R4 = "http://hl7.org/fhir/StructureDefinition/"
EXT = "extension:unidentifiedPatient"


def val(el, tag):
    if el is None:
        return None
    x = el.find("f:" + tag, N)
    return x.get("value") if x is not None else None


def minimo(el):
    if el is None:
        return 0
    x = val(el, "min")
    return int(x) if x and x.isdigit() else 0


def obrigatorio(p, ident):
    """Um elemento só é obrigatório se ele E todos os seus ancestrais o forem.

    A regra vale para qualquer fatia, não só para `Composition.section`: no SA, a fatia
    `Encounter.diagnosis:problemAndDiagnosis` é 0..* e o `condition` dentro dela é 1..1 — ler
    só o `condition` faria o perfil parecer obrigatório, que foi o erro da primeira versão
    desta sonda. O id da fatia já carrega o segmento (`diagnosis:problemAndDiagnosis`), então
    quebrar por ponto dá a cadeia certa e ignora o elemento-base do slicing.
    """
    if minimo(p["els"].get(ident)) < 1:
        return False
    partes = ident.split(".")
    # n começa em 2: o primeiro segmento é o elemento-raiz do recurso, que vem 0..* no
    # snapshot e reprovaria a cadeia inteira.
    for n in range(2, len(partes)):
        pai = ".".join(partes[:n])
        if pai in p["els"] and minimo(p["els"][pai]) < 1:
            return False
    return True


def destinos(el):
    """Perfis que o elemento exige. `targetProfile` é o normal num Reference; `profile`
    aparece em fatias discriminadas por `entry.resolve()` em alguns pacotes."""
    fora = []
    for campo in ("targetProfile", "profile"):
        for x in el.findall(f"f:type/f:{campo}", N):
            fora.append((x.get("value").rsplit("/", 1)[-1], campo))
    return fora


def carregar(pacote):
    perfis = {}
    for arq in sorted(glob.glob(str(RAIZ / pacote / "*.xml"))):
        try:
            r = ET.parse(arq).getroot()
        except ET.ParseError:
            continue
        if not r.tag.endswith("StructureDefinition"):
            continue
        snap = r.find("f:snapshot", N)
        parte = snap if snap is not None else r.find("f:differential", N)
        if parte is None:
            continue
        url = val(r, "url") or arq
        perfis[url.rsplit("/", 1)[-1]] = {
            "els": {e.get("id"): e for e in parte.findall("f:element", N) if e.get("id")},
            "fonte": "snapshot" if snap is not None else "differential",
            "base": val(r, "baseDefinition") or "",
            "arq": arq,
        }
    return perfis


def contextos(p):
    """Os `*.subject` / `*.patient` que o perfil restringe, com o que cabe em cada um."""
    vistos = set()
    for i in sorted(p["els"]):
        ctx = i.split(".extension:")[0]
        if ctx in vistos or not (ctx.endswith(".subject") or ctx.endswith(".patient")):
            continue
        vistos.add(ctx)
        def c(sufixo, tag):
            return val(p["els"].get(ctx + sufixo), tag) or "-"
        yield {
            "ctx": ctx,
            "ext": any(k.startswith(ctx + "." + EXT) for k in p["els"]),
            "ext_ms": val(p["els"].get(ctx + "." + EXT), "mustSupport") == "true",
            "ident": c(".identifier", "min"),
            "ivalor": c(".identifier.value", "min"),
            "display": c(".display", "max"),
            "ref": c(".reference", "max"),
        }


def parte0(perfis, raizes):
    print("═" * 112)
    print("PARTE 0 — seções do documento, como o pacote as declara")
    print("═" * 112)
    achou = {}
    for raiz in raizes:
        p = perfis.get(raiz)
        if p is None:
            print(f"  ?? perfil-raiz {raiz} não está no pacote")
            continue
        print(f"\n── {raiz}  (fonte: {p['fonte']})")
        linhas = 0
        for i in sorted(p["els"]):
            if not i.startswith("Composition.section"):
                continue
            if i.count(".") > 1 and not i.endswith(".entry"):
                continue
            e = p["els"][i]
            alvos = destinos(e)
            mn, mx = val(e, "min") or "-", val(e, "max") or "-"
            etiqueta = ", ".join(f"{n} [{campo}]" for n, campo in alvos) or ""
            print(f"   {i:62s} {mn:>3s}..{mx:<4s} {etiqueta}")
            linhas += 1
            if alvos:
                achou.setdefault(raiz, 0)
                achou[raiz] += 1
        if not linhas:
            print("   (nenhuma fatia de section neste perfil)")
    return achou


def parte1(perfis):
    print()
    print("═" * 112)
    print("PARTE 1 — a extensão é exequível no próprio perfil que a marca MustSupport?")
    print("═" * 112)
    print(f"{'perfil':44s} {'contexto':22s} {'ident':6s} {'i.val':6s} {'disp':5s} {'ref':4s} veredicto")
    conta = {"EXEQUÍVEL": 0, "CONTRADIÇÃO": 0, "?": 0}
    for nome, p in perfis.items():
        for c in contextos(p):
            if not c["ext_ms"]:
                continue
            if p["fonte"] == "differential" and not p["base"].startswith(BASE_R4):
                v, chave = f"?  (deriva de {p['base'].rsplit('/', 1)[-1]} e não tem snapshot)", "?"
            elif c["ident"] == "1" and c["display"] == "0" and c["ref"] == "0":
                v, chave = "CONTRADIÇÃO  (defeito nº 21)", "CONTRADIÇÃO"
            elif c["ident"] in ("-", "0"):
                v, chave = "EXEQUÍVEL", "EXEQUÍVEL"
            else:
                v, chave = "?  (combinação não prevista)", "?"
            conta[chave] += 1
            print(f"{nome[:44]:44s} {c['ctx'][:22]:22s} {c['ident']:6s} {c['ivalor']:6s} "
                  f"{c['display']:5s} {c['ref']:4s} {v}")
    print(f"\n{conta['EXEQUÍVEL']} exequível(is) · {conta['CONTRADIÇÃO']} em contradição · "
          f"{conta['?']} a conferir   (cada contexto vale 4 MustSupport: a extensão e os três filhos)")
    return conta


def parte3(perfis):
    """Tabela de referência: o que cabe no `subject` de CADA perfil do pacote.

    A PARTE 1 só mostra os contextos em que a extensão é MustSupport. Quem escreve o Builder
    precisa do inverso: dado o perfil que ele está instanciando, a extensão é permitida ali?
    Um documento não identificado só pode conter recursos cujos perfis a aceitem — os demais
    têm de ficar de fora, e é esta tabela que diz quais.
    """
    print()
    print("═" * 112)
    print("PARTE 3 — o que cabe no subject de cada perfil do pacote (referência para o Builder)")
    print("═" * 112)
    print(f"{'perfil':42s} {'contexto':30s} {'extensão':12s} {'ident':6s} {'disp':5s} {'ref':4s} pode ir no documento?")
    linhas = []
    for nome, p in sorted(perfis.items()):
        for c in contextos(p):
            ext = ("MustSupport" if c["ext_ms"] else "permitida") if c["ext"] else "NÃO"
            if c["ext"] and c["ident"] != "1":
                v = "SIM"
            elif c["ident"] == "1":
                v = "NÃO — identifier 1..1"
            else:
                v = "NÃO — extensão não permitida aqui"
            linhas.append(f"{nome[:42]:42s} {c['ctx'][:30]:30s} {ext:12s} {c['ident']:6s} "
                          f"{c['display']:5s} {c['ref']:4s} {v}")
    for l in linhas:
        print(l)
    print(f"\n{sum(1 for l in linhas if l.rstrip().endswith('SIM'))} de {len(linhas)} contexto(s) "
          f"aceitam um indivíduo não identificado.")


def raizes_automaticas(perfis):
    alvo = {n for p in perfis.values() for e in p["els"].values() for n, _ in destinos(e)}
    return [n for n, p in perfis.items()
            if p["base"].endswith("/Composition") and n not in alvo
            and any(i.startswith("Composition.section") for i in p["els"])]


def parte2(perfis, raizes, secoes_vistas):
    print()
    print("═" * 112)
    print("PARTE 2 — alcance obrigatório: o documento é forçado a instanciar quais perfis?")
    print("═" * 112)
    for raiz in raizes:
        if raiz not in perfis:
            continue
        vistos, fila = {}, [(raiz, "(raiz)")]
        quase = []
        while fila:
            nome, via = fila.pop(0)
            if nome in vistos:
                continue
            vistos[nome] = via
            p = perfis.get(nome)
            if p is None:
                continue
            for i, e in p["els"].items():
                alvos = [n for n, _ in destinos(e) if n in perfis]
                if not alvos:
                    continue
                if not obrigatorio(p, i):
                    quase += [f"{nome} · {i} -> {a}" for a in alvos]
                    continue
                for a in alvos:
                    if a not in vistos:
                        fila.append((a, f"{nome} · {i.split('.', 1)[-1]}"))
        print(f"\n── raiz {raiz}: {len(vistos)} perfil(is) obrigatório(s)")
        bloqueios = []
        for nome, via in vistos.items():
            p = perfis.get(nome)
            print(f"   {nome:44s} via {via}")
            if p is None:
                continue
            for c in contextos(p):
                cabe = []
                if c["ext"]:
                    cabe.append("extensão" + ("*" if c["ext_ms"] else ""))
                if c["display"] != "0":
                    cabe.append("display")
                if c["ref"] != "0":
                    cabe.append("reference")
                cabe.append("identifier" + (" (1..1)" if c["ident"] == "1" else ""))
                marca = ""
                if not c["ext"] and c["display"] == "0" and c["ref"] == "0":
                    marca = "  <<< SÓ identifier preenche este subject"
                    bloqueios.append(f"{nome} · {c['ctx']}  (extensão não permitida aqui)")
                elif c["ident"] == "1":
                    marca = "  <<< identifier 1..1 ao lado da extensão"
                    bloqueios.append(f"{nome} · {c['ctx']}  (contradição, defeito nº 21)")
                print(f"      {c['ctx']:26s} cabe: {', '.join(cabe)}{marca}")
        inconclusivo = len(vistos) == 1 and secoes_vistas.get(raiz, 0) == 0 \
            and any(i.startswith("Composition.section") for i in perfis[raiz]["els"])
        print(f"\n   Veredicto de {raiz}: ", end="")
        if inconclusivo:
            print("INCONCLUSIVO.")
            print("      O perfil tem fatias de section, mas nenhuma delas nomeia um perfil deste")
            print("      pacote — a sonda não achou aresta nenhuma para percorrer. Ver a PARTE 0.")
        elif bloqueios:
            print("NÃO representa indivíduo não identificado.")
            for b in bloqueios:
                print(f"      bloqueio: {b}")
        else:
            print("nenhum bloqueio no alcance obrigatório — a forma não identificada é representável.")
            if quase:
                print(f"      ({len(quase)} referência(s) opcional(is) não percorrida(s): um documento "
                      f"mínimo pode omiti-las)")
    print("\nNota: um `subject` que só aceita identifier bloqueia o documento quando o próprio "
          "`subject`\né obrigatório no R4 base — ClinicalImpression.subject e ServiceRequest.subject "
          "são 1..1.")


def main(argv):
    if not argv:
        sys.exit(__doc__)
    if not RAIZ.is_dir():
        sys.exit(f"!! não encontrei {RAIZ} — rode na raiz do repositório")
    pacote, raizes = argv[0], argv[1:]
    perfis = carregar(pacote)
    if not perfis:
        sys.exit(f"!! nenhum StructureDefinition em {RAIZ / pacote}")
    snaps = sum(1 for p in perfis.values() if p["fonte"] == "snapshot")
    print(f"pacote {pacote}: {len(perfis)} perfis lidos de {RAIZ / pacote} "
          f"({snaps} com snapshot, {len(perfis) - snaps} só com differential)\n")
    raizes = raizes or raizes_automaticas(perfis)
    secoes = parte0(perfis, raizes)
    parte1(perfis)
    parte2(perfis, raizes, secoes)
    parte3(perfis)


if __name__ == "__main__":
    main(sys.argv[1:])
