import { Pipe, PipeTransform } from '@angular/core';

/** 1234.5 -> "R$ 1.234,50". Brazilian formatting, since PIX is Brazilian. */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined, currency = 'BRL'): string {
    if (value === null || value === undefined) return '';
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(value);
  }
}
