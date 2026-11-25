/*
 * Widget « annuaire sémantique ».
 *
 * Intégration sur un site tiers, sans autre développement :
 *   <script src="https://<serveur>/widget.js" defer></script>
 *   <annuaire-widget widget="<identifiant du widget>"></annuaire-widget>
 *
 * Le type (annuaire ou carte), le domaine et les champs affichés sont réglés par l'administrateur
 * dans l'application ; cette balise ne porte que l'identifiant.
 * Le rendu est isolé dans un Shadow DOM : les styles du site hôte et ceux du widget ne se mélangent pas.
 * Toutes les données sont insérées par textContent, jamais interprétées comme du HTML.
 */
(() => {
  const SERVEUR = new URL(document.currentScript.src).origin;
  // Bibliothèque de cartes, version figée, avec contrôle d'intégrité : un fichier modifié ne se charge pas.
  const LEAFLET = 'https://unpkg.com/leaflet@1.9.4/dist/';
  const LEAFLET_JS = 'sha384-cxOPjt7s7Iz04uaHJceBmS+qpjv2JkIHNVcuOrM+YHwZOmJGBXI00mdUXEq65HTH';
  const LEAFLET_CSS = 'sha384-sHL9NAb7lN7rfvG5lfHpm643Xkcjzp4jFvuavGOndn6pjVqS6ny56CAt3nsEVT4H';

  const STYLE = `
    :host { display: block; font: 15px/1.45 system-ui, sans-serif; color: #1f2733; }
    * { box-sizing: border-box; }
    .cadre { border: 1px solid #d5dae1; border-radius: 6px; background: #fff; overflow: hidden; }
    .tete { display: flex; flex-wrap: wrap; gap: 8px; padding: 12px; background: #f4f6f8; border-bottom: 1px solid #d5dae1; }
    input, select, button { font: inherit; padding: 6px 9px; border: 1px solid #b8c0cc; border-radius: 4px; background: #fff; color: inherit; }
    input { flex: 1 1 180px; min-width: 0; }
    button { cursor: pointer; }
    button:disabled { opacity: .45; cursor: default; }
    ul { list-style: none; margin: 0; padding: 0; }
    li { padding: 11px 14px; border-top: 1px solid #e6e9ee; }
    li:first-child { border-top: 0; }
    .nom { font-weight: 600; }
    .lieu { color: #5f6b7a; font-size: 13px; }
    dl { display: flex; flex-wrap: wrap; gap: 2px 16px; margin: 5px 0 0; font-size: 13px; }
    dt { color: #5f6b7a; display: inline; } dd { display: inline; margin: 0 0 0 4px; }
    .pied { display: flex; align-items: center; gap: 10px; padding: 9px 12px; border-top: 1px solid #d5dae1; font-size: 13px; color: #5f6b7a; }
    .pied span { flex: 1; }
    .carte { height: 420px; }
    .message { padding: 16px; color: #5f6b7a; }
  `;

  class AnnuaireWidget extends HTMLElement {
    connectedCallback() {
      this.racine = this.attachShadow({ mode: 'open' });
      this.etat = { q: '', page: 0, filtres: {} };
      this.charger();
    }

    async charger() {
      const id = this.getAttribute('widget');
      if (!id) return this.message('Attribut « widget » manquant.');
      const url = new URL(`${SERVEUR}/widget/${encodeURIComponent(id)}/donnees`);
      if (this.etat.q) url.searchParams.set('q', this.etat.q);
      url.searchParams.set('page', this.etat.page);
      for (const [champ, valeur] of Object.entries(this.etat.filtres)) {
        if (valeur) url.searchParams.set('f.' + champ, valeur);
      }
      try {
        const reponse = await fetch(url);
        if (!reponse.ok) throw new Error(String(reponse.status));
        this.afficher(await reponse.json());
      } catch (e) {
        this.message('Les données ne sont pas disponibles pour le moment.');
      }
    }

    message(texte) {
      this.racine.replaceChildren(el('style', STYLE), el('div', texte, 'cadre message'));
    }

    afficher(d) {
      const tete = el('div', null, 'tete');
      const saisie = el('input');
      saisie.type = 'search';
      saisie.placeholder = 'Rechercher';
      saisie.value = this.etat.q;
      saisie.setAttribute('aria-label', 'Rechercher dans ' + d.nom);
      saisie.addEventListener('change', () => { this.etat.q = saisie.value.trim(); this.etat.page = 0; this.charger(); });
      tete.append(saisie);

      for (const f of d.facettes) {
        const choix = el('select');
        choix.setAttribute('aria-label', f.libelle);
        choix.append(option('', f.libelle + ' : tout'));
        for (const v of f.valeurs) choix.append(option(v.valeur, `${v.valeur} (${v.nombre})`));
        choix.value = this.etat.filtres[f.champ] || '';
        choix.addEventListener('change', () => { this.etat.filtres[f.champ] = choix.value; this.etat.page = 0; this.charger(); });
        tete.append(choix);
      }

      const cadre = el('div', null, 'cadre');
      cadre.append(tete);
      if (d.type === 'carte') {
        const zone = el('div', null, 'carte');
        cadre.append(zone);
        this.racine.replaceChildren(el('style', STYLE), lienStyle(LEAFLET + 'leaflet.css', LEAFLET_CSS), cadre);
        carte(zone, d);
      } else {
        cadre.append(d.entites.length ? liste(d) : el('div', 'Aucun résultat.', 'message'));
        cadre.append(this.pied(d));
        this.racine.replaceChildren(el('style', STYLE), cadre);
      }
    }

    pied(d) {
      const pied = el('div', null, 'pied');
      const pages = Math.max(1, Math.ceil(d.total / 20));
      const avant = el('button', 'Précédent');
      const apres = el('button', 'Suivant');
      avant.disabled = this.etat.page === 0;
      apres.disabled = this.etat.page >= pages - 1;
      avant.addEventListener('click', () => { this.etat.page--; this.charger(); });
      apres.addEventListener('click', () => { this.etat.page++; this.charger(); });
      pied.append(el('span', `${d.total} résultat${d.total > 1 ? 's' : ''} · page ${this.etat.page + 1} sur ${pages}`), avant, apres);
      return pied;
    }
  }

  function liste(d) {
    const ul = el('ul');
    for (const e of d.entites) {
      const li = el('li');
      li.append(el('div', e.nom, 'nom'));
      const lieu = [e.codePostal, e.commune].filter(Boolean).join(' ');
      if (lieu) li.append(el('div', lieu, 'lieu'));
      const dl = el('dl');
      for (const c of d.colonnes) {
        const v = e.champs[c.id];
        if (v === undefined || v === null || v === '') continue;
        const paire = el('div');
        paire.append(el('dt', c.libelle + ' :'), el('dd', v === true ? 'oui' : v === false ? 'non' : String(v)));
        dl.append(paire);
      }
      if (dl.children.length) li.append(dl);
      ul.append(li);
    }
    return ul;
  }

  let leaflet;
  function chargerLeaflet() {
    leaflet ??= new Promise((ok, echec) => {
      if (window.L) return ok(window.L);
      const s = document.createElement('script');
      s.src = LEAFLET + 'leaflet.js';
      s.integrity = LEAFLET_JS;
      s.crossOrigin = 'anonymous';
      s.onload = () => ok(window.L);
      s.onerror = echec;
      document.head.append(s);
    });
    return leaflet;
  }

  async function carte(zone, d) {
    const points = d.entites.filter(e => e.latitude != null && e.longitude != null);
    try {
      const L = await chargerLeaflet();
      const c = L.map(zone);
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 18, attribution: '© OpenStreetMap',
      }).addTo(c);
      const marques = points.map(e => {
        const bulle = el('div');
        bulle.append(el('strong', e.nom), el('div', [e.codePostal, e.commune].filter(Boolean).join(' ')));
        return L.circleMarker([e.latitude, e.longitude], { radius: 7, color: '#1b2a4a', fillOpacity: .7 }).bindPopup(bulle);
      });
      if (marques.length) {
        const groupe = L.featureGroup(marques).addTo(c);
        c.fitBounds(groupe.getBounds().pad(0.2));
      } else {
        c.setView([48.2, -2.9], 7);
      }
    } catch (e) {
      zone.replaceChildren(el('div', 'La carte ne peut pas être affichée.', 'message'));
    }
  }

  function el(balise, texte, classe) {
    const e = document.createElement(balise);
    if (texte != null) e.textContent = texte;
    if (classe) e.className = classe;
    return e;
  }

  function option(valeur, texte) {
    const o = el('option', texte);
    o.value = valeur;
    return o;
  }

  function lienStyle(href, integrite) {
    const l = document.createElement('link');
    l.rel = 'stylesheet';
    l.href = href;
    l.integrity = integrite;
    l.crossOrigin = 'anonymous';
    return l;
  }

  if (!customElements.get('annuaire-widget')) customElements.define('annuaire-widget', AnnuaireWidget);
})();
