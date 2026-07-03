import { Routes } from '@angular/router';
import { GameSelectorComponent } from './game-selector/game-selector.component';
import { GameBoardComponent } from './components/game-board/game-board.component';
import { PeseqindshBoardComponent } from './games/peseqindsh/components/peseqindsh-board/peseqindsh-board.component';

// Shto këtu route-n e çdo loje të re që krijon në vazhdim.
export const routes: Routes = [
  { path: '', component: GameSelectorComponent },
  { path: 'pesekatesh', component: GameBoardComponent },
  { path: 'peseqindsh', component: PeseqindshBoardComponent },
];
