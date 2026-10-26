#!/usr/bin/env python3
"""
Gera o catalogo dos codigos que a RNDS aceita nas MEDIDAS OBSERVADAS, a partir
dos artefatos de conformidade oficiais.

    uso: python3 scripts/gerar-catalogo-medidas.py [pacote]
         (a partir da raiz do repositorio; pacote default: rac)

O catalogo NAO e uma tabela de equivalencia clinica (ADR-05). Ele responde
"este codigo e aceito?", nao "qual codigo corresponde ao que eu medi".

Fonte: ValueSet BRTipoObservacao-1.0, que e o binding `required` de
Observation.code no BRMedidaObservada -- conferido no artefato, nao inferido.

A conta tem tres fontes, e duas delas vem ENUMERADAS no proprio ValueSet:

     9 codigos do CodeSystem BRTipoObservacao (incluido inteiro)
  +  1 codigo da BRTabelaSUS, enumerado no ValueSet
  + 13 codigos LOINC, enumerados no ValueSet
  = 23 codigos aceitos

Que os LOINC venham enumerados importa: o catalogo sai inteiro do ValueSet
mais um CodeSystem, e nao depende do LOINC, que nenhum pacote distribui.

Registra tambem dois achados que NAO sao escolha do framework:
  - cinco pares de sinonimo DENTRO do mesmo ValueSet (peso e "P" e "29463-7");
  - quatro codigos que nao sao medida de valor, e sim observacao descritiva.
"""
import datetime
import hashlib
import os
import sys
import xml.etree.ElementTree as ET

NS = "{http://hl7.org/fhir}"
RAIZ = "rnds-br-connect-core/src/main/resources/conformance"
DESTINO = "rnds-br-connect-core/src/main/resources/catalogo/medidas-rnds.yml"

SINONIMOS = [
    ("Peso", ["P", "29463-7"]),
    ("Altura", ["A", "8302-2"]),
    ("Perimetro cefalico", ["PC", "9843-4"]),
    ("Circunferencia abdominal", ["CA", "8280-0"]),
    ("Pressao arterial", ["PA", "8480-6", "8462-4"]),
]
NAO_SAO_MEDIDA = ["DSIA", "RECIDI", "DF", "IAC"]


def indice(pacote):
    d = os.path.join(RAIZ, pacote)
    p = os.path.join(d, "index.txt")
    if not os.path.exists(p):
        raise SystemExit("ERRO: pacote nao encontrado: %s (rode a partir da raiz do repositorio)" % d)
    return d, [os.path.join(d, l.strip())
               for l in open(p, encoding="utf-8")
               if l.strip() and not l.startswith("#") and l.strip().lower().endswith(".xml")]


def por_url(arquivos, sufixo, tipo):
    for f in arquivos:
        try:
            r = ET.parse(f).getroot()
        except Exception:
            continue
        if r.tag != NS + tipo:
            continue
        u = r.find(NS + "url")
        if u is not None and u.get("value", "").endswith(sufixo):
            return f, r
    return None, None


def valor(r, tag):
    e = r.find(NS + tag)
    return e.get("value") if e is not None else "?"


def conceitos(no):
    """Codigos e displays, recursivamente (um CodeSystem pode aninhar conceito)."""
    out = []
    for c in no.iter(NS + "concept"):
        k = c.find(NS + "code")
        if k is None:
            continue
        d = c.find(NS + "display")
        out.append((k.get("value"), (d.get("value") if d is not None else "").strip()))
    return out


def deduplicar(pares, nome):
    vistos = {}
    dup = []
    for c, d in pares:
        if c in vistos:
            dup.append(c)
            continue
        vistos[c] = d
    if dup:
        print("   AVISO: %s declara %d codigo(s) duplicado(s): %s"
              % (nome, len(dup), ", ".join(sorted(set(dup)))))
        print("          mantida a primeira ocorrencia de cada.")
    return list(vistos.items())


def main(pacote):
    d, arquivos = indice(pacote)

    f_sd, sd = por_url(arquivos, "/BRMedidaObservada", "StructureDefinition")
    binding = None
    if sd is not None:
        for parte in ("snapshot", "differential"):
            b = sd.find(NS + parte)
            if b is None:
                continue
            for el in b.findall(NS + "element"):
                if (el.get("id") or "") == "Observation.code":
                    bd = el.find(NS + "binding")
                    if bd is not None and bd.find(NS + "valueSet") is not None:
                        binding = bd.find(NS + "valueSet").get("value")
            if binding:
                break
    if binding is None:
        raise SystemExit("ERRO: nao achei o binding de Observation.code no BRMedidaObservada.")
    if "BRTipoObservacao" not in binding:
        raise SystemExit("ERRO: o binding mudou e aponta para %s. Reveja este gerador." % binding)

    f_vs, vs = por_url(arquivos, "/BRTipoObservacao-1.0", "ValueSet")
    if vs is None:
        f_vs, vs = por_url(arquivos, "/BRTipoObservacao", "ValueSet")
    if vs is None:
        raise SystemExit("ERRO: ValueSet BRTipoObservacao ausente no pacote %s." % pacote)

    fontes = [f_vs]
    grupos = []
    linhas_fonte = []
    total = 0
    presentes = set()

    for inc in vs.iter(NS + "include"):
        sy = inc.find(NS + "system")
        sistema = sy.get("value") if sy is not None else "?"
        enumerados = [(c.find(NS + "code").get("value"),
                       (c.find(NS + "display").get("value")
                        if c.find(NS + "display") is not None else "").strip())
                      for c in inc.findall(NS + "concept")]
        if enumerados:
            pares = deduplicar(enumerados, sistema.rsplit("/", 1)[-1] or sistema)
            origem = "%d codigos enumerados no ValueSet" % len(pares)
        else:
            nome = sistema.rstrip("/").rsplit("/", 1)[-1]
            f_cs, cs = por_url(arquivos, "/" + nome, "CodeSystem")
            if cs is None:
                raise SystemExit("ERRO: CodeSystem %s ausente no pacote %s." % (nome, pacote))
            fontes.append(f_cs)
            pares = deduplicar(conceitos(cs), nome)
            origem = "CodeSystem %s v%s, %d codigos" % (nome, valor(cs, "version"), len(pares))
        excl = {c.find(NS + "code").get("value")
                for e in vs.iter(NS + "exclude")
                for c in e.findall(NS + "concept")
                if (e.find(NS + "system") is not None
                    and e.find(NS + "system").get("value") == sistema)}
        if excl:
            pares = [(c, dd) for c, dd in pares if c not in excl]
            origem += ", menos %d do compose.exclude" % len(excl)
        grupos.append((sistema, pares))
        linhas_fonte.append("#        %s: %s" % (sistema, origem))
        total += len(pares)
        presentes.update(c for c, _ in pares)

    sha = hashlib.sha256()
    for f in fontes:
        sha.update(open(f, "rb").read())

    cab = [
        "# Catalogo dos codigos aceitos nas MEDIDAS OBSERVADAS da RNDS.",
        "#",
        "# GERADO por scripts/gerar-catalogo-medidas.py. NAO EDITAR A MAO.",
        "# Gerado em: %s" % datetime.datetime.now().isoformat(timespec="seconds"),
        "# Pacote:    conformance/%s" % pacote,
        "# SHA-256 das fontes: %s" % sha.hexdigest()[:16],
        "#",
        "# NAO e tabela de equivalencia clinica (ADR-05): responde 'este codigo e",
        "# aceito?', nao 'qual codigo corresponde ao que eu medi'. A segunda pergunta",
        "# e decisao clinica e permanece com a equipe de saude.",
        "#",
        "# Binding: Observation.code no %s v%s," % (
            os.path.basename(f_sd) if f_sd else "BRMedidaObservada", valor(sd, "version")),
        "#          required -> %s" % binding,
        "# Fonte:   ValueSet %s v%s" % (valor(vs, "url"), valor(vs, "version")),
    ] + linhas_fonte + [
        "# TOTAL: %d codigos aceitos" % total,
        "",
        "totalDeclarado: %d" % total,
        "codigos:",
    ]

    corpo = []
    for sistema, pares in grupos:
        corpo.append("  %s:" % sistema)
        for c, dd in pares:
            corpo.append('    "%s": "%s"' % (c, dd.replace('"', "'")))

    rodape = [
        "",
        "# Dois achados do artefato, registrados aqui porque nao sao escolha do framework.",
        "#",
        "# 1. A MESMA medida tem duas grafias oficiais dentro do MESMO ValueSet. O artefato",
        "#    de referencia do RAC escolheu a LOINC. Nao e equivalencia clinica: e a",
        "#    constatacao de que o binding aceita as duas.",
        "sinonimos:",
    ]
    for medida, codigos in SINONIMOS:
        faltam = [c for c in codigos if c not in presentes]
        if faltam:
            raise SystemExit("ERRO: o par de sinonimo de %s nao esta completo no ValueSet "
                             "(faltam %s). O artefato mudou; reveja este gerador."
                             % (medida, ", ".join(faltam)))
        rodape.append('  - medida: "%s"' % medida)
        rodape.append("    codigos: [%s]" % ", ".join('"%s"' % c for c in codigos))

    rodape += [
        "",
        "# 2. Quatro codigos do CodeSystem da RNDS nao sao medida de VALOR: sao observacao",
        "#    descritiva (BRObservacaoDescritiva). O mesmo ValueSet serve aos dois perfis,",
        "#    de modo que emitir um deles como 'medida' passa no binding e na Camada 4.",
        "naoSaoMedida: [%s]" % ", ".join('"%s"' % c for c in NAO_SAO_MEDIDA),
    ]
    for c in NAO_SAO_MEDIDA:
        if c not in presentes:
            raise SystemExit("ERRO: o codigo %s nao esta mais no ValueSet. O artefato mudou." % c)

    os.makedirs(os.path.dirname(DESTINO), exist_ok=True)
    with open(DESTINO, "w", encoding="utf-8") as fh:
        fh.write("\n".join(cab + corpo + rodape) + "\n")

    print("\n%d codigos gravados em %s" % (total, DESTINO))
    for sistema, pares in grupos:
        print("   %-62s %2d" % (sistema, len(pares)))
    print("   binding conferido: %s" % binding)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "rac")
