import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex items-center gap-2.5' },
  template: `
    <svg viewBox="0 0 32 32" class="size-8" aria-hidden="true">
      <defs>
        <linearGradient id="pf-logo" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#67e8f9" />
          <stop offset="1" stop-color="#06b6d4" />
        </linearGradient>
      </defs>
      <rect width="32" height="32" rx="10" fill="url(#pf-logo)" />
      <path d="M9 21 16 9l7 12" fill="none" stroke="#050a14" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M12.5 21h7" stroke="#050a14" stroke-width="3" stroke-linecap="round" />
    </svg>
    @if (!compact()) {
      <span class="text-xl font-extrabold tracking-tight text-white">pix<span class="text-brand-400">flow</span></span>
    }
  `,
})
export class Logo {
  readonly compact = input(false);
}
