import { Routes } from '@angular/router';
import { GameSelectorComponent } from './game-selector/game-selector.component';
import { authGuard } from './auth/auth.guard';

// Shto këtu route-n e çdo loje të re që krijon në vazhdim (me authGuard, si te lojërat ekzistuese).
// loadComponent (jo import statik) -> kodi i çdo loje shkarkohet vetëm kur hyn në të, jo që në ngarkimin fillestar.
export const routes: Routes = [
  { path: '', component: GameSelectorComponent },
  {
    path: 'pesekatesh',
    loadComponent: () => import('./games/pesekatesh/components/game-board/game-board.component')
      .then((m) => m.GameBoardComponent),
    canActivate: [authGuard],
  },
  {
    path: 'peseqindsh',
    loadComponent: () => import('./games/peseqindsh/components/peseqindsh-board/peseqindsh-board.component')
      .then((m) => m.PeseqindshBoardComponent),
    canActivate: [authGuard],
  },
  {
    path: 'derr',
    loadComponent: () => import('./games/derr/components/derr-board/derr-board.component')
      .then((m) => m.DerrBoardComponent),
    canActivate: [authGuard],
  },
];
