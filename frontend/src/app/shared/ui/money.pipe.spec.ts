import { MoneyPipe } from './money.pipe';

// Intl separates "R$" and the number with a non-breaking space: normalise it for the assertions.
const plain = (value: string) => value.replace(/ /g, ' ');

describe('MoneyPipe', () => {
  const pipe = new MoneyPipe();

  it('formats Brazilian reais', () => {
    expect(plain(pipe.transform(1234.5))).toBe('R$ 1.234,50');
    expect(plain(pipe.transform(0))).toBe('R$ 0,00');
  });

  it('keeps cents exact', () => {
    expect(plain(pipe.transform(80.9))).toBe('R$ 80,90');
  });

  it('returns an empty string for missing values', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });
});
