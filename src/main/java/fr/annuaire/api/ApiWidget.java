package fr.annuaire.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fr.annuaire.widget.Widget;
import fr.annuaire.widget.Widgets;
import fr.annuaire.widget.Widgets.Donnees;

/**
 * Point d'accès public appelé par le widget depuis le site d'un partenaire.
 * Il ne renvoie que les champs publiés par l'administrateur, et seulement aux sites qu'il a autorisés
 * (en-tête CORS calculé par widget).
 */
@RestController
public class ApiWidget {

    private final Widgets widgets;

    public ApiWidget(Widgets widgets) {
        this.widgets = widgets;
    }

    @GetMapping("/widget/{id}/donnees")
    public ResponseEntity<Donnees> donnees(@PathVariable UUID id,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam Map<String, String> tous,
                                           @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origine) {
        Widget w = widgets.exiger(id);
        // Sans en-tête Origin, l'appel vient de la même origine ou d'un outil : rien à autoriser côté navigateur.
        if (origine != null && !widgets.origineAutorisee(w, origine)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ResponseEntity.BodyBuilder reponse = ResponseEntity.ok().header(HttpHeaders.VARY, HttpHeaders.ORIGIN);
        if (origine != null) {
            reponse.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origine);
        }
        return reponse.body(widgets.donnees(w, q, ApiAdministration.filtres(tous), page));
    }
}
