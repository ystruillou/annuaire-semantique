package fr.annuaire.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import fr.annuaire.modele.Modele.Domaine;
import fr.annuaire.modele.Modele.DonneeRefusee;
import fr.annuaire.modele.Modele.Entite;
import fr.annuaire.modele.Modele.Introuvable;
import fr.annuaire.modele.Modele.NouveauChamp;
import fr.annuaire.modele.Modele.NouveauDomaine;
import fr.annuaire.modele.Modele.Recherche;
import fr.annuaire.modele.Modele.ResultatRecherche;
import fr.annuaire.recherche.IndexRecherche;
import fr.annuaire.semantique.Entites;
import fr.annuaire.semantique.Ontologies;
import fr.annuaire.sources.ConnecteurSirene;
import fr.annuaire.widget.Widget;
import fr.annuaire.widget.Widgets;
import fr.annuaire.widget.Widgets.NouveauWidget;

/**
 * API REST. Les lectures sont ouvertes ; toute modification exige le jeton d'administration
 * (voir {@link JetonAdministration}).
 */
@RestController
@RequestMapping("/api")
public class ApiAdministration {

    private final Ontologies ontologies;
    private final Entites entites;
    private final IndexRecherche index;
    private final ConnecteurSirene sirene;
    private final Widgets widgets;

    public ApiAdministration(Ontologies ontologies, Entites entites, IndexRecherche index,
                             ConnecteurSirene sirene, Widgets widgets) {
        this.ontologies = ontologies;
        this.entites = entites;
        this.index = index;
        this.sirene = sirene;
        this.widgets = widgets;
    }

    // --- Domaines et champs (l'ontologie) ---

    @GetMapping("/domaines")
    public List<Domaine> domaines() {
        return ontologies.domaines();
    }

    @PostMapping("/domaines")
    @ResponseStatus(HttpStatus.CREATED)
    public Domaine creerDomaine(@RequestBody NouveauDomaine nouveau) {
        return ontologies.creerDomaine(nouveau.libelle());
    }

    @PostMapping("/domaines/{id}/champs")
    @ResponseStatus(HttpStatus.CREATED)
    public Domaine ajouterChamp(@PathVariable String id, @RequestBody NouveauChamp nouveau) {
        return ontologies.ajouterChamp(id, nouveau);
    }

    @DeleteMapping("/domaines/{id}/champs/{champ}")
    public Domaine retirerChamp(@PathVariable String id, @PathVariable String champ) {
        Domaine d = ontologies.retirerChamp(id, champ);
        index.reconstruire();
        return d;
    }

    // --- Entités ---

    @GetMapping("/entites/{id}")
    public Entite entite(@PathVariable String id) {
        return entites.parId(id).orElseThrow(() -> new Introuvable("Entité inconnue"));
    }

    @PostMapping("/entites")
    @ResponseStatus(HttpStatus.CREATED)
    public Entite creer(@RequestBody Entite saisie) {
        return enregistrer(new Entite(null, saisie.nom(), saisie.siren(), saisie.commune(), saisie.codePostal(),
                saisie.latitude(), saisie.longitude(), saisie.domaines(), saisie.champs()));
    }

    @PutMapping("/entites/{id}")
    public Entite modifier(@PathVariable String id, @RequestBody Entite saisie) {
        entite(id);
        return enregistrer(new Entite(id, saisie.nom(), saisie.siren(), saisie.commune(), saisie.codePostal(),
                saisie.latitude(), saisie.longitude(), saisie.domaines(), saisie.champs()));
    }

    @DeleteMapping("/entites/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable String id) {
        if (!entites.supprimer(id)) {
            throw new Introuvable("Entité inconnue");
        }
        index.retirer(id);
    }

    private Entite enregistrer(Entite e) {
        Entite enregistree = entites.enregistrer(e);
        index.indexer(enregistree);
        return enregistree;
    }

    // --- Recherche ---

    /** Les paramètres qui commencent par « f. » sont des filtres de facette : {@code f.commune=Brest}. */
    @GetMapping("/recherche")
    public ResultatRecherche recherche(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) String domaine,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int taille,
                                       @RequestParam Map<String, String> tous) {
        return index.chercher(new Recherche(q, domaine, filtres(tous), page, taille));
    }

    @PostMapping("/index/reconstruire")
    public Map<String, Integer> reconstruire() {
        return Map.of("entites", index.reconstruire());
    }

    // --- Source externe ---

    @GetMapping("/sirene")
    public List<Entite> sirene(@RequestParam String q) {
        return sirene.chercher(q);
    }

    // --- Widgets ---

    @GetMapping("/widgets")
    public List<Widget> widgets() {
        return widgets.tous();
    }

    @PostMapping("/widgets")
    @ResponseStatus(HttpStatus.CREATED)
    public Widget creerWidget(@RequestBody NouveauWidget nouveau) {
        return widgets.creer(nouveau);
    }

    @DeleteMapping("/widgets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerWidget(@PathVariable UUID id) {
        widgets.supprimer(id);
    }

    static Map<String, String> filtres(Map<String, String> parametres) {
        Map<String, String> filtres = new java.util.LinkedHashMap<>();
        parametres.forEach((cle, valeur) -> {
            if (cle.startsWith("f.") && cle.length() > 2) {
                filtres.put(cle.substring(2), valeur);
            }
        });
        return filtres;
    }

    /** Traduit les erreurs métier en réponses HTTP lisibles (RFC 9457). */
    @RestControllerAdvice
    static class Erreurs {

        @ExceptionHandler(DonneeRefusee.class)
        ProblemDetail refusee(DonneeRefusee e) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }

        @ExceptionHandler(Introuvable.class)
        ProblemDetail introuvable(Introuvable e) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
