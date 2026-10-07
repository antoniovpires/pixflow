import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { toApiError } from '../../../core/api/api-error';
import { AuthService } from '../../../core/auth/auth.service';
import { Icon } from '../../../shared/ui/icon';

type Field = 'name' | 'email' | 'password';

// Same limits as the backend (RegisterRequest): BCrypt only reads 72 bytes.
const PASSWORD_MIN = 8;
const PASSWORD_MAX = 72;

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './register.html',
})
export class Register {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, notBlank]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(PASSWORD_MIN), Validators.maxLength(PASSWORD_MAX)]],
  });

  protected readonly submitting = signal(false);
  protected readonly showPassword = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Messages the server attached to a specific field (e.g. "email already registered"). */
  protected readonly serverErrors = signal<Partial<Record<Field, string>>>({});

  protected readonly strengthLabels = ['Too short', 'Weak', 'Okay', 'Good', 'Strong'];

  /** 0-4, a rough visual hint only. The real rule is the length check. */
  protected strength(): number {
    const value = this.form.controls.password.value;
    if (value.length < PASSWORD_MIN) return 0;
    let score = 1;
    if (value.length >= 12) score++;
    if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score++;
    if (/\d/.test(value) && /[^A-Za-z0-9]/.test(value)) score++;
    return Math.min(score, 4);
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting()) return;

    this.submitting.set(true);
    this.error.set(null);
    this.serverErrors.set({});

    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => void this.router.navigateByUrl('/'),
      error: (e: unknown) => {
        this.submitting.set(false);
        const apiError = toApiError(e, 'Could not create your account. Please try again.');
        if (apiError.status === 409) {
          this.serverErrors.set({ email: 'This email is already registered.' });
        } else if (Object.keys(apiError.fields).length > 0) {
          this.serverErrors.set(apiError.fields as Partial<Record<Field, string>>);
        } else {
          this.error.set(apiError.message);
        }
      },
    });
  }

  protected invalid(name: Field): boolean {
    const control = this.form.controls[name];
    return (control.invalid && control.touched) || !!this.serverErrors()[name];
  }

  protected clearServerError(name: Field): void {
    if (this.serverErrors()[name]) this.serverErrors.update((errors) => ({ ...errors, [name]: undefined }));
  }
}

function notBlank(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim().length === 0 ? { blank: true } : null;
}
