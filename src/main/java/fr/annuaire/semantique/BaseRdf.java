package fr.annuaire.semantique;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.update.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Accès à la base RDF, par SPARQL uniquement.
 * <p>
 * Deux modes, choisis par la configuration :
 * <ul>
 *   <li>un serveur Fuseki distant ({@code annuaire.rdf.fuseki}), comme en production ;</li>
 *   <li>une base TDB2 dans le même processus ({@code annuaire.rdf.dossier}), pour développer sans serveur.
 *       Dossier vide : base en mémoire, pour les tests.</li>
 * </ul>
 * Le reste du code ne sait pas dans quel mode il tourne.
 */
@Component
public class BaseRdf {

    private static final Logger log = LoggerFactory.getLogger(BaseRdf.class);

    private final String fuseki;
    private final Dataset locale;
    private final boolean surDisque;

    public BaseRdf(@Value("${annuaire.rdf.fuseki:}") String fuseki,
                   @Value("${annuaire.rdf.dossier:}") String dossier) throws java.io.IOException {
        this.fuseki = fuseki == null ? "" : fuseki.strip();
        if (!this.fuseki.isEmpty()) {
            this.locale = null;
            this.surDisque = false;
            log.info("Base RDF : serveur Fuseki {}", this.fuseki);
        } else if (dossier == null || dossier.isBlank()) {
            this.locale = DatasetFactory.createTxnMem();
            this.surDisque = false;
            log.info("Base RDF : en mémoire");
        } else {
            Path chemin = Path.of(dossier).toAbsolutePath();
            Files.createDirectories(chemin);
            this.locale = TDB2Factory.connectDataset(chemin.toString());
            this.surDisque = true;
            log.info("Base RDF : TDB2 dans {}", chemin);
        }
    }

    private RDFConnection connexion() {
        return locale != null ? RDFConnection.connect(locale) : RDFConnection.connect(fuseki);
    }

    public <T> List<T> selectionner(Query requete, Function<QuerySolution, T> lecture) {
        List<T> lignes = new ArrayList<>();
        try (RDFConnection c = connexion()) {
            c.querySelect(requete, ligne -> lignes.add(lecture.apply(ligne)));
        }
        return lignes;
    }

    public boolean demander(Query requete) {
        try (RDFConnection c = connexion()) {
            return c.queryAsk(requete);
        }
    }

    public void modifier(UpdateRequest mise) {
        try (RDFConnection c = connexion()) {
            c.update(mise);
        }
    }

    /** Copie d'un graphe nommé, pour la validation SHACL. */
    public Model graphe(String nom) {
        // CONSTRUCT renvoie un modèle indépendant de la base : il reste lisible une fois la transaction close.
        org.apache.jena.query.ParameterizedSparqlString q = new org.apache.jena.query.ParameterizedSparqlString(
                "CONSTRUCT { ?s ?p ?o } WHERE { GRAPH ?g { ?s ?p ?o } }");
        q.setIri("g", nom);
        try (RDFConnection c = connexion()) {
            return c.queryConstruct(q.asQuery());
        }
    }

    @PreDestroy
    public void fermer() {
        if (locale != null) {
            locale.close();
            if (surDisque) {
                // Libère les fichiers de la base, que TDB2 garde ouverts tant que le processus vit.
                org.apache.jena.tdb2.sys.TDBInternal.expel(locale.asDatasetGraph());
            }
        }
    }
}
