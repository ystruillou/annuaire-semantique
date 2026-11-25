package fr.annuaire.semantique;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.ParameterizedSparqlString;
import org.springframework.stereotype.Service;

import fr.annuaire.modele.Modele.Champ;
import fr.annuaire.modele.Modele.Domaine;
import fr.annuaire.modele.Modele.DonneeRefusee;
import fr.annuaire.modele.Modele.Introuvable;
import fr.annuaire.modele.Modele.NouveauChamp;
import fr.annuaire.modele.Modele.TypeChamp;

/**
 * Les domaines d'intérêt et leurs champs.
 * <p>
 * Un domaine est à la fois une classe RDFS et une forme SHACL ({@code sh:NodeShape}) : toute entité
 * de cette classe doit respecter la forme. Chaque champ est une {@code sh:PropertyShape}.
 * Ajouter un champ, c'est ajouter quelques triplets : ni table à modifier, ni redéploiement.
 */
@Service
public class Ontologies {

    private final BaseRdf base;

    public Ontologies(BaseRdf base) {
        this.base = base;
    }

    public List<Domaine> domaines() {
        ParameterizedSparqlString q = requete("""
                SELECT ?d ?libelleD ?forme ?prop ?libelle ?type ?facette ?ordre WHERE {
                  GRAPH ?g {
                    ?d a ann:Domaine ; rdfs:label ?libelleD .
                    OPTIONAL {
                      ?d sh:property ?forme .
                      ?forme sh:path ?prop ; sh:name ?libelle ; sh:datatype ?type ; sh:order ?ordre .
                      OPTIONAL { ?forme ann:facette ?facette }
                    }
                  }
                }
                ORDER BY ?libelleD ?ordre
                """);
        q.setIri("g", Vocabulaire.GRAPHE_ONTOLOGIES);

        Map<String, String> libelles = new LinkedHashMap<>();
        Map<String, List<Champ>> champs = new LinkedHashMap<>();
        base.selectionner(q.asQuery(), ligne -> {
            String id = Vocabulaire.idDepuisIri(ligne.getResource("d").getURI(), Vocabulaire.domaine(""));
            libelles.put(id, ligne.getLiteral("libelleD").getString());
            List<Champ> liste = champs.computeIfAbsent(id, k -> new ArrayList<>());
            if (ligne.contains("prop")) {
                liste.add(new Champ(
                        Vocabulaire.idDepuisIri(ligne.getResource("prop").getURI(), Vocabulaire.propriete("")),
                        ligne.getLiteral("libelle").getString(),
                        typeDepuisXsd(ligne.getResource("type").getURI()),
                        ligne.contains("facette") && ligne.getLiteral("facette").getBoolean(),
                        ligne.getLiteral("ordre").getInt()));
            }
            return id;
        });
        return libelles.entrySet().stream()
                .map(e -> new Domaine(e.getKey(), e.getValue(),
                        champs.get(e.getKey()).stream().sorted(Comparator.comparingInt(Champ::ordre)).toList()))
                .toList();
    }

    public Optional<Domaine> domaine(String id) {
        return domaines().stream().filter(d -> d.id().equals(id)).findFirst();
    }

    public Domaine exiger(String id) {
        return domaine(id).orElseThrow(() -> new Introuvable("Domaine inconnu : " + id));
    }

    public Domaine creerDomaine(String libelle) {
        String propre = texte(libelle, "Le libellé du domaine");
        String id = Vocabulaire.identifiant(propre);
        if (domaine(id).isPresent()) {
            throw new DonneeRefusee("Ce domaine existe déjà.");
        }
        ParameterizedSparqlString u = requete("""
                INSERT DATA { GRAPH ?g {
                  ?d a ann:Domaine , rdfs:Class , sh:NodeShape ; rdfs:label ?libelle .
                } }
                """);
        u.setIri("g", Vocabulaire.GRAPHE_ONTOLOGIES);
        u.setIri("d", Vocabulaire.domaine(id));
        u.setLiteral("libelle", propre);
        base.modifier(u.asUpdate());
        return exiger(id);
    }

    /** Ajoute un champ à un domaine. C'est l'opération que l'administrateur fait lui-même. */
    public Domaine ajouterChamp(String domaineId, NouveauChamp nouveau) {
        Domaine domaine = exiger(domaineId);
        String libelle = texte(nouveau.libelle(), "Le libellé du champ");
        if (nouveau.type() == null) {
            throw new DonneeRefusee("Le type du champ est obligatoire.");
        }
        String id = domaineId + "-" + Vocabulaire.identifiant(libelle);
        if (domaine.champs().stream().anyMatch(c -> c.id().equals(id))) {
            throw new DonneeRefusee("Ce champ existe déjà dans le domaine.");
        }
        int ordre = domaine.champs().stream().mapToInt(Champ::ordre).max().orElse(0) + 1;

        ParameterizedSparqlString u = requete("""
                INSERT DATA { GRAPH ?g {
                  ?d sh:property ?forme .
                  ?forme a sh:PropertyShape ;
                    sh:path ?prop ; sh:name ?libelle ; sh:datatype ?type ;
                    sh:maxCount 1 ; sh:order ?ordre ; ann:facette ?facette .
                } }
                """);
        u.setIri("g", Vocabulaire.GRAPHE_ONTOLOGIES);
        u.setIri("d", Vocabulaire.domaine(domaineId));
        u.setIri("forme", Vocabulaire.forme(domaineId, id));
        u.setIri("prop", Vocabulaire.propriete(id));
        u.setLiteral("libelle", libelle);
        u.setIri("type", xsd(nouveau.type()).getURI());
        u.setLiteral("ordre", ordre);
        // Une facette n'a de sens que sur du texte : on regroupe par valeur exacte.
        u.setLiteral("facette", nouveau.facette() && nouveau.type() == TypeChamp.TEXTE);
        base.modifier(u.asUpdate());
        return exiger(domaineId);
    }

    /** Retire le champ et ses valeurs sur toutes les entités. */
    public Domaine retirerChamp(String domaineId, String champId) {
        Domaine domaine = exiger(domaineId);
        if (domaine.champs().stream().noneMatch(c -> c.id().equals(champId))) {
            throw new Introuvable("Champ inconnu : " + champId);
        }
        ParameterizedSparqlString u = requete("""
                DELETE { GRAPH ?g { ?d sh:property ?forme . ?forme ?p ?o } }
                WHERE  { GRAPH ?g { ?d sh:property ?forme . ?forme ?p ?o } } ;
                DELETE { GRAPH ?donnees { ?e ?prop ?v } }
                WHERE  { GRAPH ?donnees { ?e ?prop ?v } }
                """);
        u.setIri("g", Vocabulaire.GRAPHE_ONTOLOGIES);
        u.setIri("donnees", Vocabulaire.GRAPHE_DONNEES);
        u.setIri("d", Vocabulaire.domaine(domaineId));
        u.setIri("forme", Vocabulaire.forme(domaineId, champId));
        u.setIri("prop", Vocabulaire.propriete(champId));
        base.modifier(u.asUpdate());
        return exiger(domaineId);
    }

    static ParameterizedSparqlString requete(String texte) {
        return new ParameterizedSparqlString(Vocabulaire.PREFIXES + texte);
    }

    static XSDDatatype xsd(TypeChamp type) {
        return switch (type) {
            case TEXTE -> XSDDatatype.XSDstring;
            case NOMBRE -> XSDDatatype.XSDinteger;
            case OUI_NON -> XSDDatatype.XSDboolean;
        };
    }

    private static TypeChamp typeDepuisXsd(String iri) {
        if (XSDDatatype.XSDinteger.getURI().equals(iri)) {
            return TypeChamp.NOMBRE;
        }
        if (XSDDatatype.XSDboolean.getURI().equals(iri)) {
            return TypeChamp.OUI_NON;
        }
        return TypeChamp.TEXTE;
    }

    private static String texte(String valeur, String nom) {
        if (valeur == null || valeur.isBlank()) {
            throw new DonneeRefusee(nom + " est obligatoire.");
        }
        String propre = valeur.strip();
        if (propre.length() > 80) {
            throw new DonneeRefusee(nom + " dépasse 80 caractères.");
        }
        return propre;
    }
}
