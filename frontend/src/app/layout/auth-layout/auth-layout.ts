import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Icon } from '../../shared/ui/icon';
import { Logo } from '../../shared/ui/logo';
import { MoneyPipe } from '../../shared/ui/money.pipe';

/** Split screen for login/register: brand story on the left, the form on the right. */
@Component({
  selector: 'app-auth-layout',
  imports: [RouterOutlet, Icon, Logo, MoneyPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './auth-layout.html',
})
export class AuthLayout {}
