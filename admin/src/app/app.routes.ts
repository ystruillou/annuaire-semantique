import { Routes } from '@angular/router';

import { PageDomaines } from './page-domaines';
import { PageEntites } from './page-entites';
import { PageWidgets } from './page-widgets';

export const routes: Routes = [
  { path: 'domaines', component: PageDomaines },
  { path: 'fiches', component: PageEntites },
  { path: 'widgets', component: PageWidgets },
  { path: '**', redirectTo: 'domaines' },
];
