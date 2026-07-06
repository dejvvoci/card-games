import { Routes } from '@angular/router';
import { GameSelectorComponent } from './game-selector/game-selector.component';
import { GameBoardComponent } from './games/pesekatesh/components/game-board/game-board.component';
import { PeseqindshBoardComponent } from './games/peseqindsh/components/peseqindsh-board/peseqindsh-board.component';
import { authGuard } from './auth/auth.guard';

// Shto këtu route-n e çdo loje të re që krijon në vazhdim (me authGuard, si te lojërat ekzistuese).
export const routes: Routes = [
  { path: '', component: GameSelectorComponent },
  { path: 'pesekatesh', component: GameBoardComponent, canActivate: [authGuard] },
  { path: 'peseqindsh', component: PeseqindshBoardComponent, canActivate: [authGuard] },
];
