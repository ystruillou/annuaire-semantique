package fr.annuaire.semantique;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.stereotype.Service;

import fr.annuaire.modele.Modele.Champ;
import fr.annuaire.modele.Modele.Domaine;
import fr.annuaire.modele.Modele.DonneeRefusee;
import fr.annuaire.modele.Modele.Entite;
import fr.annuaire.modele.Modele.TypeChamp;

/**
 * Les entités, stockées en RDF.
 * <p>
 * Avant chaque enregistrement, l'entité est validée par SHACL contre les formes des domaines
 * auxquels elle est rattachée. Les règles viennent donc des données de l'ontologie, pas du code.
 */
@Service
public class Entites {

    private static final String GEO = "http://www.w3.org/2003/01/geo/wgs84_pos#";
    private static final String TYPE_ENTITE = Vocabulaire.ANN + "Entite";
    private static final String SIREN = Vocabulaire.ANN + "siren";
    private static final String COMMUNE = Vocabulaire.ANN + "commune";
    private static final String CODE_POSTAL = Vocabulaire.ANN + "codePostal";

    private final BaseRdf base;
    private final Ontologies ontologies;

    public Entites(BaseRdf base, Ontologies ontologies) {
        this.base = base;
        this.ontologies = ontologies;
    }

    public List<Entite> toutes() {
        return lire(null);
    }

    public Optional<Entite> parId(String id) {
        return lire(id).stream().findFirst();
    }

    /** Crée l'entité si {@code id} est vide, la remplace sinon. Renvoie l'entité telle qu'enregistrée. */
    public Entite enregistrer(Entite saisie) {
        if (saisie.nom() == null || saisie.nom().isBlank()) {
            throw new DonneeRefusee("Le nom de l'entité est obligatoire.");
        }
        String id = saisie.id() == null || saisie.id().isBlank() ? UUID.randomUUID().toString() : saisie.id();
        List<Domaine> domaines = (saisie.domaines() == null ? List.<String>of() : saisie.domaines()).stream()
                .distinct().map(ontologies::exiger).toList();
        Map<String, Champ> champsPermis = domaines.stream().flatMap(d -> d.champs().stream())
                .collect(Collectors.toMap(Champ::id, c -> c, (a, b) -> a));

        Model m = ModelFactory.createDefaultModel();
        Resource e = m.createResource(Vocabulaire.entite(id));
        e.addProperty(RDF.type, m.createResource(TYPE_ENTITE));
        domaines.forEach(d -> e.addProperty(RDF.type, m.createResource(Vocabulaire.domaine(d.id()))));
        e.addProperty(RDFS.label, saisie.nom().strip());
        ajouter(m, e, SIREN, saisie.siren());
        ajouter(m, e, COMMUNE, saisie.commune());
        ajouter(m, e, CODE_POSTAL, saisie.codePostal());
        if (saisie.latitude() != null && saisie.longitude() != null) {
            e.addLiteral(m.createProperty(GEO, "lat"), saisie.latitude().doubleValue());
            e.addLiteral(m.createProperty(GEO, "long"), saisie.longitude().doubleValue());
        }
        if (saisie.champs() != null) {
            saisie.champs().forEach((champId, valeur) -> {
                if (valeur == null || valeur.toString().isBlank()) {
                    return;
                }
                Champ champ = champsPermis.get(champId);
                if (champ == null) {
                    throw new DonneeRefusee("Le champ « " + champId + " » n'appartient à aucun domaine de l'entité.");
                }
                // Le littéral porte le type déclaré dans l'ontologie ; s'il est mal formé, SHACL le refuse.
                e.addProperty(m.createProperty(Vocabulaire.propriete(champId)),
                        m.createTypedLiteral(valeur.toString().strip(), Ontologies.xsd(champ.type())));
            });
        }

        valider(m, champsPermis);

        StringWriter triplets = new StringWriter();
        RDFDataMgr.write(triplets, m, Lang.NTRIPLES);
        ParameterizedSparqlString u = Ontologies.requete("""
                DELETE WHERE { GRAPH ?g { ?e ?p ?o } } ;
                INSERT DATA { GRAPH ?g {
                """ + triplets + "} }");
        u.setIri("g", Vocabulaire.GRAPHE_DONNEES);
        u.setIri("e", e.getURI());
        base.modifier(UpdateFactory.create(u.toString()));
        return parId(id).orElseThrow();
    }

    public boolean supprimer(String id) {
        if (parId(id).isEmpty()) {
            return false;
        }
        ParameterizedSparqlString u = Ontologies.requete("DELETE WHERE { GRAPH ?g { ?e ?p ?o } }");
        u.setIri("g", Vocabulaire.GRAPHE_DONNEES);
        u.setIri("e", Vocabulaire.entite(id));
        base.modifier(u.asUpdate());
        return true;
    }

    private void valider(Model entite, Map<String, Champ> champs) {
        Shapes formes = Shapes.parse(base.graphe(Vocabulaire.GRAPHE_ONTOLOGIES).getGraph());
        ValidationReport rapport = ShaclValidator.get().validate(formes, entite.getGraph());
        if (!rapport.conforms()) {
            String detail = rapport.getEntries().stream().map(entree -> {
                String prop = entree.resultPath() == null ? "" : entree.resultPath().toString();
                String libelle = champs.values().stream()
                        .filter(c -> prop.contains(Vocabulaire.propriete(c.id())))
                        .map(Champ::libelle).findFirst().orElse("valeur");
                return "« " + libelle + " » : valeur refusée";
            }).distinct().collect(Collectors.joining(" ; "));
            throw new DonneeRefusee("L'entité ne respecte pas la structure de ses domaines. " + detail + ".");
        }
    }

    private List<Entite> lire(String id) {
        // Une variable remplacée par une IRI ne peut pas rester dans le SELECT : deux textes de requête.
        ParameterizedSparqlString q = Ontologies.requete(id == null
                ? "SELECT ?e ?p ?o WHERE { GRAPH ?g { ?e a ann:Entite ; ?p ?o } } ORDER BY ?e"
                : "SELECT ?p ?o WHERE { GRAPH ?g { ?cible a ann:Entite ; ?p ?o } }");
        q.setIri("g", Vocabulaire.GRAPHE_DONNEES);
        if (id != null) {
            q.setIri("cible", Vocabulaire.entite(id));
        }
        Map<String, TypeChamp> types = ontologies.domaines().stream().flatMap(d -> d.champs().stream())
                .collect(Collectors.toMap(Champ::id, Champ::type, (a, b) -> a));

        Map<String, Brouillon> brouillons = new LinkedHashMap<>();
        String iriFixe = id == null ? null : Vocabulaire.entite(id);
        base.selectionner(q.asQuery(), ligne -> {
            String iri = iriFixe != null ? iriFixe : ligne.getResource("e").getURI();
            brouillons.computeIfAbsent(iri, k -> new Brouillon()).lire(
                    ligne.getResource("p").getURI(), ligne.get("o"), types);
            return iri;
        });
        List<Entite> entites = new ArrayList<>();
        brouillons.forEach((iri, b) -> entites.add(b.entite(Vocabulaire.idDepuisIri(iri, Vocabulaire.ENTITE_BASE))));
        return entites;
    }

    private static void ajouter(Model m, Resource e, String propriete, String valeur) {
        if (valeur != null && !valeur.isBlank()) {
            e.addProperty(m.createProperty(propriete), valeur.strip());
        }
    }

    /** Rassemble les triplets d'une entité au fil de la lecture. */
    private static final class Brouillon {
        String nom = "";
        String siren;
        String commune;
        String codePostal;
        Double latitude;
        Double longitude;
        final List<String> domaines = new ArrayList<>();
        final Map<String, Object> champs = new TreeMap<>();

        void lire(String p, RDFNode o, Map<String, TypeChamp> types) {
            String prefixeChamp = Vocabulaire.propriete("");
            if (p.equals(RDF.type.getURI())) {
                String iri = o.asResource().getURI();
                if (iri.startsWith(Vocabulaire.domaine(""))) {
                    domaines.add(Vocabulaire.idDepuisIri(iri, Vocabulaire.domaine("")));
                }
            } else if (p.equals(RDFS.label.getURI())) {
                nom = o.asLiteral().getString();
            } else if (p.equals(SIREN)) {
                siren = o.asLiteral().getString();
            } else if (p.equals(COMMUNE)) {
                commune = o.asLiteral().getString();
            } else if (p.equals(CODE_POSTAL)) {
                codePostal = o.asLiteral().getString();
            } else if (p.equals(GEO + "lat")) {
                latitude = o.asLiteral().getDouble();
            } else if (p.equals(GEO + "long")) {
                longitude = o.asLiteral().getDouble();
            } else if (p.startsWith(prefixeChamp) && o.isLiteral()) {
                String champId = p.substring(prefixeChamp.length());
                Literal l = o.asLiteral();
                champs.put(champId, switch (types.getOrDefault(champId, TypeChamp.TEXTE)) {
                    case NOMBRE -> l.getLong();
                    case OUI_NON -> l.getBoolean();
                    case TEXTE -> l.getString();
                });
            }
        }

        Entite entite(String id) {
            domaines.sort(null);
            return new Entite(id, nom, siren, commune, codePostal, latitude, longitude,
                    List.copyOf(domaines), Map.copyOf(champs));
        }
    }
}
