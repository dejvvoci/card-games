import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Suit, SUIT_SYMBOL, rankLabel } from '../../models/card.model';

/**
 * Letër loje e vizatuar me SVG (indeks në qoshe + simbol qendror i madh).
 * Përdoret njësoj te Pesëkatësh dhe Peseqindsh — çdo përmirësim vizual i letrave
 * (p.sh. shtimi i "pips" për 2-10, ose figura për J/Q/K) bëhet vetëm këtu.
 *
 * Përdorim: <app-playing-card [suit]="'ZEMER'" [rank]="12" [width]="60" (cardClick)="..."></app-playing-card>
 * Për letër të kthyer: <app-playing-card [faceDown]="true" [width]="34"></app-playing-card>
 */
@Component({
  selector: 'app-playing-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <svg
      [attr.width]="width"
      [attr.height]="height"
      viewBox="0 0 70 98"
      class="playing-card"
      [class.selected]="selected"
      [class.dimmed]="dimmed"
      [class.clickable]="clickable"
      (click)="cardClick.emit()">

      <rect x="1" y="1" width="68" height="96" rx="10"
            [attr.fill]="faceDown ? backFill : '#FFFDF8'"
            [attr.stroke]="faceDown ? 'none' : 'rgba(43,42,37,0.14)'" />

      <ng-container *ngIf="faceDown">
        <rect x="6" y="6" width="58" height="86" rx="7"
              fill="none" stroke="#ffffff" stroke-width="1.5" stroke-opacity="0.55" />
        <path d="M6 6 L64 92 M64 6 L6 92" stroke="#ffffff" stroke-opacity="0.3" stroke-width="1.5" />
      </ng-container>

      <ng-container *ngIf="!faceDown && suit && rank">
        <text x="7" y="19" font-size="15" font-weight="500" [attr.fill]="color">{{ label }}</text>
        <text x="7" y="33" font-size="13" [attr.fill]="color">{{ symbol }}</text>

        <text x="35" y="58" font-size="28" text-anchor="middle" [attr.fill]="color">{{ symbol }}</text>

        <text x="63" y="90" font-size="15" font-weight="500" text-anchor="end"
              [attr.fill]="color" transform="rotate(180 63 90)">{{ label }}</text>
        <text x="63" y="76" font-size="13" text-anchor="end"
              [attr.fill]="color" transform="rotate(180 63 76)">{{ symbol }}</text>
      </ng-container>
    </svg>
  `,
  styles: [`
    .playing-card {
      display: block;
      font-family: 'Fraunces', 'Georgia', serif;
      filter: drop-shadow(0 4px 10px rgba(43,42,37,0.22));
      transition: transform 0.18s ease, filter 0.18s ease;
    }
    .playing-card.clickable { cursor: pointer; }
    .playing-card.clickable:hover { transform: translateY(-8px); }
    .playing-card.selected {
      transform: translateY(-16px);
      filter: drop-shadow(0 6px 16px rgba(193,110,81,0.35));
    }
    .playing-card.dimmed { opacity: 0.45; }
  `],
})
export class PlayingCardComponent {
  @Input() suit: Suit | null = null;
  @Input() rank: number | null = null;
  @Input() faceDown = false;
  @Input() width = 60;
  @Input() selected = false;
  @Input() dimmed = false;
  @Input() clickable = false;
  @Input() backFill = '#0F5132';

  @Output() cardClick = new EventEmitter<void>();

  get height(): number {
    return Math.round(this.width * (98 / 70));
  }

  get label(): string {
    return this.rank != null ? rankLabel(this.rank) : '';
  }

  get symbol(): string {
    return this.suit ? SUIT_SYMBOL[this.suit] : '';
  }

  get color(): string {
    if (!this.suit) return '#2B2A25';
    return this.suit === 'ZEMER' || this.suit === 'ROMB' ? '#8A4A34' : '#2B2A25';
  }
}
