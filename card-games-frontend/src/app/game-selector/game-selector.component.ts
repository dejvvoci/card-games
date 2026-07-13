import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { AuthWidgetComponent } from '../auth/auth-widget/auth-widget.component';
import { StatsPageComponent } from '../stats/stats-page.component';

interface GameOption {
  id: string;
  name: string;
  description: string;
  route: string;
  cornerLabel: string;   // p.sh. "4P" (si vlerë e një letre, por për numrin e lojtarëve)
  centerIcon: string;    // simboli i madh qendror i "letrës"
  accent: 'primary' | 'accent';
  tilt: number;          // rrotullim i lehtë si letra e shpërndara në tavolinë
}

type Tab = 'games' | 'stats';

@Component({
  selector: 'app-game-selector',
  standalone: true,
  imports: [CommonModule, AuthWidgetComponent, StatsPageComponent],
  templateUrl: './game-selector.component.html',
  styleUrls: ['./game-selector.component.css'],
})
export class GameSelectorComponent {

  activeTab: Tab = 'games';

  // Shto këtu çdo lojë të re: cornerLabel si "vlera" e letrës, centerIcon si simboli i saj.
  games: GameOption[] = [
    {
      id: 'pesekatesh',
      name: 'Pesëkatësh',
      description: 'Lojë klasike me 5 kate, plus Shtatat.',
      route: '/pesekatesh',
      cornerLabel: '4P',
      centerIcon: '♠',
      accent: 'primary',
      tilt: -4,
    },
    {
      id: 'peseqindsh',
      name: 'Peseqindsh',
      description: 'Lojë klasike — deri në 500 pikë.',
      route: '/peseqindsh',
      cornerLabel: '2P',
      centerIcon: '♦',
      accent: 'accent',
      tilt: 4,
    },
  ];

  constructor(private router: Router, public auth: AuthService) {}

  play(game: GameOption): void {
    this.router.navigateByUrl(game.route);
  }
}
