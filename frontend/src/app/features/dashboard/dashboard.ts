import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

import { toApiError } from '../../core/api/api-error';
import { AccountsApi } from '../../core/api/accounts.api';
import { Account, Transfer, TransferStatus } from '../../core/api/models';
import { TransfersApi } from '../../core/api/transfers.api';
import { Icon, IconName } from '../../shared/ui/icon';
import { MoneyPipe } from '../../shared/ui/money.pipe';

interface ActivityItem {
  id: string;
  direction: 'sent' | 'received';
  amount: number;
  status: TransferStatus;
}

interface QuickAction {
  label: string;
  hint: string;
  icon: IconName;
}

const STATUS_STYLES: Record<TransferStatus, string> = {
  SUCCESS: 'bg-emerald-400/12 text-emerald-300',
  PENDING: 'bg-amber-400/12 text-amber-300',
  FAILED: 'bg-rose-400/12 text-rose-300',
  CANCELLED: 'bg-slate-400/12 text-slate-300',
  REVERSED: 'bg-violet-400/12 text-violet-300',
};

@Component({
  selector: 'app-dashboard',
  imports: [Icon, MoneyPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private readonly accountsApi = inject(AccountsApi);
  private readonly transfersApi = inject(TransfersApi);

  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly errorMessage = signal('');
  protected readonly account = signal<Account | null>(null);
  private readonly transfers = signal<Transfer[]>([]);

  /** The latest five transfers, labelled from the caller's point of view. */
  protected readonly activity = computed<ActivityItem[]>(() => {
    const mine = this.account()?.id;
    return this.transfers()
      .slice(0, 5)
      .map((t) => ({
        id: t.id,
        direction: t.sourceAccountId === mine ? 'sent' : 'received',
        amount: t.amount,
        status: t.status,
      }));
  });

  protected readonly shortAccountId = computed(() => this.account()?.id.slice(-4).toUpperCase() ?? '');

  // Coming in the next slices: they are shown (disabled) so the screen reads as a real product.
  protected readonly actions: QuickAction[] = [
    { label: 'Send', hint: 'To a PIX key', icon: 'send' },
    { label: 'My keys', hint: 'Receive money', icon: 'key' },
    { label: 'Statement', hint: 'Full ledger', icon: 'receipt' },
  ];

  protected readonly skeletonRows = [1, 2, 3];

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set('loading');
    forkJoin({ account: this.accountsApi.me(), transfers: this.transfersApi.list() }).subscribe({
      next: ({ account, transfers }) => {
        this.account.set(account);
        this.transfers.set(transfers);
        this.state.set('ready');
      },
      error: (e: unknown) => {
        this.errorMessage.set(toApiError(e, 'We could not load your account.').message);
        this.state.set('error');
      },
    });
  }

  protected statusStyle(status: TransferStatus): string {
    return STATUS_STYLES[status];
  }

  protected statusLabel(status: TransferStatus): string {
    return status.charAt(0) + status.slice(1).toLowerCase();
  }
}
