import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';

import { PixKey } from '../../core/api/models';
import { PixKeysApi } from '../../core/api/pixkeys.api';
import { Icon, IconName } from '../../shared/ui/icon';

@Component({
  selector: 'app-pixkeys',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pixkeys.html',
  styleUrl: './pixkeys.css',
})
export class Pixkeys {
  private readonly pixKeysApi = inject(PixKeysApi);

  protected readonly pixKeys = signal<PixKey[]>([]);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.pixKeysApi.list().pipe(
      catchError((error: Error) => {
        console.error(error);
        return of([]);
      }),
    ).subscribe((pixKeys: PixKey[]) => {
      this.pixKeys.set(pixKeys);
    });
  }

  protected typeLabel(key: PixKey): string {
    if (key.keyType === 'EMAIL') return 'Email';
    if (key.keyType === 'CPF') return 'CPF';
    return 'Phone';
  }

  protected typeIcon(key: PixKey): IconName {
    if (key.keyType === 'EMAIL') return 'mail';
    if (key.keyType === 'CPF') return 'id-card';
    return 'phone';
  }

  /** Group digits so a stored phone or CPF reads the way people write it. */
  protected displayValue(key: PixKey): string {
    const value = key.keyValue;
    if (key.keyType === 'CPF' && value.length === 11) {
      return `${value.slice(0, 3)}.${value.slice(3, 6)}.${value.slice(6, 9)}-${value.slice(9)}`;
    }
    if (key.keyType !== 'EMAIL' && value.length === 11) {
      return `(${value.slice(0, 2)}) ${value.slice(2, 7)}-${value.slice(7)}`;
    }
    return value;
  }
}
