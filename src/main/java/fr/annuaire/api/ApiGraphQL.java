package fr.annuaire.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import fr.annuaire.modele.Modele.Domaine;
import fr.annuaire.modele.Modele.Entite;
import fr.annuaire.modele.Modele.Recherche;
import fr.annuaire.modele.Modele.ResultatRecherche;
import fr.annuaire.recherche.IndexRecherche;
import fr.annuaire.semantique.Entites;
import fr.annuaire.semantique.Ontologies;

/**
 * API GraphQL, en lecture seule. Elle s'appuie sur les mêmes services que l'API REST :
 * le client choisit les champs qu'il veut recevoir.
 */
@Controller
public class ApiGraphQL {

    public record ValeurChamp(String champ, String valeur) {
    }

    public record Filtre(String champ, String valeur) {
    }

    private final Ontologies ontologies;
    private final Entites entites;
    private final IndexRecherche index;

    public ApiGraphQL(Ontologies ontologies, Entites entites, IndexRecherche index) {
        this.ontologies = ontologies;
        this.entites = entites;
        this.index = index;
    }

    @QueryMapping
    public List<Domaine> domaines() {
        return ontologies.domaines();
    }

    @QueryMapping
    public Entite entite(@Argument String id) {
        return entites.parId(id).orElse(null);
    }

    @QueryMapping
    public ResultatRecherche recherche(@Argument String texte, @Argument String domaine,
                                       @Argument List<Filtre> filtres,
                                       @Argument Integer page, @Argument Integer taille) {
        Map<String, String> f = filtres == null ? Map.of()
                : filtres.stream().collect(Collectors.toMap(Filtre::champ, Filtre::valeur, (a, b) -> b));
        return index.chercher(new Recherche(texte, domaine, f, page == null ? 0 : page, taille == null ? 20 : taille));
    }

    /** Les champs de domaine varient d'une entité à l'autre : GraphQL les reçoit en liste de paires. */
    @SchemaMapping(typeName = "Entite", field = "champs")
    public List<ValeurChamp> champs(Entite entite) {
        return entite.champs().entrySet().stream()
                .map(e -> new ValeurChamp(e.getKey(), String.valueOf(e.getValue()))).toList();
    }
}
